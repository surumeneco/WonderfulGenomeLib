package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Physical bits plus immutable biological provenance. Local edits must not
 * clone the provenance for unaffected bits. Every view references a source
 * array whose Origin records are immutable and never exposed for mutation.
 */
final class TrackedSequence {
    record Origin(int lane, int sourceBit, double pointMultiplier, double structuralMultiplier) {
        static Origin fresh() { return new Origin(-1, -1, 1.0, 1.0); }
        boolean isFresh() { return lane < 0; }
    }

    /**
     * Logical consecutive source bits, possibly viewed in reverse order.
     * Adjacent views can refer to the same underlying Origin[] without any
     * per-bit copying. Physical BitSequence remains independent and immutable.
     */
    private record Span(Origin[] values, int offset, int length, boolean reversed) {
        Origin at(int local) {
            return values[offset + (reversed ? length - 1 - local : local)];
        }

        Span subspan(int from, int to) {
            if (reversed) return new Span(values, offset + length - to, to - from, true);
            return new Span(values, offset + from, to - from, false);
        }

        Span backwards() {
            return new Span(values, offset, length, !reversed);
        }
    }

    private final BitSequence bits;
    private final List<Span> spans;
    private final int[] cumulativeEnd;

    TrackedSequence(BitSequence bits, Origin[] origins) {
        this(bits, ownedSpans(Objects.requireNonNull(origins, "origins").clone()));
    }

    private static List<Span> ownedSpans(Origin[] owned) {
        return owned.length == 0 ? List.of()
                : List.of(new Span(owned, 0, owned.length, false));
    }

    private TrackedSequence(BitSequence bits, List<Span> spans) {
        this.bits = Objects.requireNonNull(bits, "bits");
        this.spans = List.copyOf(spans);
        this.cumulativeEnd = new int[spans.size()];
        int length = 0;
        for (int i = 0; i < spans.size(); i++) {
            Span span = spans.get(i);
            length = Math.addExact(length, span.length());
            cumulativeEnd[i] = length;
        }
        if (length != bits.bitLength()) {
            throw new IllegalArgumentException("origin count must match bit length");
        }
    }

    static TrackedSequence snapshot(BitSequence bits, int lane, LocalRateSnapshot rates) {
        Origin[] origins = new Origin[bits.bitLength()];
        for (int i = 0; i < origins.length; i++) {
            origins[i] = new Origin(lane, i,
                    rates.multiplier(LocalRateSnapshot.Kind.POINT, i),
                    rates.multiplier(LocalRateSnapshot.Kind.STRUCTURAL, i));
        }
        return new TrackedSequence(bits, ownedSpans(origins));
    }

    static TrackedSequence fresh(BitSequence bits) {
        Origin[] origins = new Origin[bits.bitLength()];
        Arrays.fill(origins, Origin.fresh());
        return new TrackedSequence(bits, ownedSpans(origins));
    }

    BitSequence bits() { return bits; }
    int bitLength() { return bits.bitLength(); }

    Origin originAt(int index) {
        if (index < 0 || index >= bitLength()) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        int position = Arrays.binarySearch(cumulativeEnd, index + 1);
        if (position < 0) position = -position - 1;
        int start = position == 0 ? 0 : cumulativeEnd[position - 1];
        return spans.get(position).at(index - start);
    }

    Origin[] origins() {
        Origin[] result = new Origin[bitLength()];
        int out = 0;
        for (Span span : spans) {
            for (int j = 0; j < span.length(); j++) {
                result[out++] = span.at(j);
            }
        }
        return result;
    }

    TrackedSequence flip(int index) {
        return new TrackedSequence(bits.flip(index), spans);
    }

    TrackedSequence slice(int fromInclusive, int toExclusive) {
        if (fromInclusive < 0 || toExclusive < fromInclusive || toExclusive > bitLength()) {
            throw new IndexOutOfBoundsException("invalid tracked slice");
        }
        List<Span> sliced = new ArrayList<>();
        int start = 0;
        for (Span span : spans) {
            int end = start + span.length();
            int from = Math.max(fromInclusive, start);
            int to = Math.min(toExclusive, end);
            if (from < to) sliced.add(span.subspan(from - start, to - start));
            start = end;
            if (start >= toExclusive) break;
        }
        return new TrackedSequence(bits.slice(fromInclusive, toExclusive), sliced);
    }

    TrackedSequence concat(TrackedSequence other) {
        Objects.requireNonNull(other, "other");
        ArrayList<Span> combined = new ArrayList<>(spans.size() + other.spans.size());
        combined.addAll(spans);
        combined.addAll(other.spans);
        return new TrackedSequence(bits.concat(other.bits), combined);
    }

    TrackedSequence insert(int position, TrackedSequence inserted) {
        if (position < 0 || position > bitLength()) {
            throw new IndexOutOfBoundsException("invalid insert position");
        }
        return slice(0, position).concat(inserted).concat(slice(position, bitLength()));
    }

    TrackedSequence delete(int fromInclusive, int toExclusive) {
        return slice(0, fromInclusive).concat(slice(toExclusive, bitLength()));
    }

    TrackedSequence replace(int fromInclusive, int toExclusive, TrackedSequence replacement) {
        return slice(0, fromInclusive).concat(replacement).concat(slice(toExclusive, bitLength()));
    }

    TrackedSequence reverse() {
        if (bitLength() <= 1) return this;
        ArrayList<Span> reverse = new ArrayList<>(spans.size());
        for (int i = spans.size() - 1; i >= 0; i--) {
            reverse.add(spans.get(i).backwards());
        }
        return new TrackedSequence(bits.reverse(), reverse);
    }

    double structuralBoundaryWeight(int boundary) {
        if (bitLength() == 0) return 1.0;
        if (boundary <= 0) return originAt(0).structuralMultiplier();
        if (boundary >= bitLength()) return originAt(bitLength() - 1).structuralMultiplier();
        return StrictMath.sqrt(originAt(boundary - 1).structuralMultiplier()
                * originAt(boundary).structuralMultiplier());
    }
}
