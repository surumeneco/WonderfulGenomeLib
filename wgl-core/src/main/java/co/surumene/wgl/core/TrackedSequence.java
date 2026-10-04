package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;

import java.util.Arrays;
import java.util.Objects;

/** Bit sequence plus transient meiosis provenance/rate metadata. Metadata is never serialized. */
final class TrackedSequence {
    record Origin(int lane, int sourceBit, double pointMultiplier, double structuralMultiplier) {
        static Origin fresh() { return new Origin(-1, -1, 1.0, 1.0); }
        boolean isFresh() { return lane < 0; }
    }

    private final BitSequence bits;
    private final Origin[] origins;

    TrackedSequence(BitSequence bits, Origin[] origins) {
        this.bits = Objects.requireNonNull(bits, "bits");
        Objects.requireNonNull(origins, "origins");
        if (origins.length != bits.bitLength()) throw new IllegalArgumentException("origin count must match bit length");
        this.origins = origins.clone();
    }

    static TrackedSequence snapshot(BitSequence bits, int lane, LocalRateSnapshot rates) {
        Origin[] origins = new Origin[bits.bitLength()];
        for (int i = 0; i < origins.length; i++) {
            origins[i] = new Origin(lane, i,
                    rates.multiplier(LocalRateSnapshot.Kind.POINT, i),
                    rates.multiplier(LocalRateSnapshot.Kind.STRUCTURAL, i));
        }
        return new TrackedSequence(bits, origins);
    }

    static TrackedSequence fresh(BitSequence bits) {
        Origin[] origins = new Origin[bits.bitLength()];
        Arrays.fill(origins, Origin.fresh());
        return new TrackedSequence(bits, origins);
    }

    BitSequence bits() { return bits; }
    int bitLength() { return bits.bitLength(); }
    Origin originAt(int index) { return origins[index]; }
    Origin[] origins() { return origins.clone(); }

    TrackedSequence flip(int index) {
        return new TrackedSequence(bits.flip(index), origins);
    }

    TrackedSequence slice(int fromInclusive, int toExclusive) {
        if (fromInclusive < 0 || toExclusive < fromInclusive || toExclusive > bitLength()) {
            throw new IndexOutOfBoundsException("invalid tracked slice");
        }
        return new TrackedSequence(bits.slice(fromInclusive, toExclusive),
                Arrays.copyOfRange(origins, fromInclusive, toExclusive));
    }

    TrackedSequence concat(TrackedSequence other) {
        Objects.requireNonNull(other, "other");
        Origin[] next = Arrays.copyOf(origins, origins.length + other.origins.length);
        System.arraycopy(other.origins, 0, next, origins.length, other.origins.length);
        return new TrackedSequence(bits.concat(other.bits), next);
    }

    TrackedSequence insert(int position, TrackedSequence inserted) {
        if (position < 0 || position > bitLength()) throw new IndexOutOfBoundsException("invalid insert position");
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
        Origin[] reversed = new Origin[origins.length];
        for (int i = 0; i < origins.length; i++) reversed[i] = origins[origins.length - 1 - i];
        return new TrackedSequence(bits.reverse(), reversed);
    }

    double structuralBoundaryWeight(int boundary) {
        if (bitLength() == 0) return 1.0;
        if (boundary <= 0) return origins[0].structuralMultiplier();
        if (boundary >= bitLength()) return origins[bitLength() - 1].structuralMultiplier();
        return StrictMath.sqrt(origins[boundary - 1].structuralMultiplier()
                * origins[boundary].structuralMultiplier());
    }
}
