package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;

import java.util.Arrays;
import java.util.Objects;

/**
 * Bit sequence plus transient meiosis provenance/rate metadata.
 * Each bit's original lane/source position is stored in one packed long,
 * with the two multipliers in parallel primitive arrays. Avoid allocating
 * one Origin object per source bit (none of this is serialized).
 */
final class TrackedSequence {
    record Origin(int lane, int sourceBit, double pointMultiplier, double structuralMultiplier) {
        static Origin fresh() { return new Origin(-1, -1, 1.0, 1.0); }
        boolean isFresh() { return lane < 0; }
    }

    private final BitSequence bits;
    private final long[] source;
    private final double[] pointMultipliers;
    private final double[] structuralMultipliers;

    TrackedSequence(BitSequence bits, Origin[] origins) {
        this.bits = Objects.requireNonNull(bits, "bits");
        Objects.requireNonNull(origins, "origins");
        if (origins.length != bits.bitLength())
            throw new IllegalArgumentException("origin count must match bit length");
        this.source = new long[origins.length];
        this.pointMultipliers = new double[origins.length];
        this.structuralMultipliers = new double[origins.length];
        for (int i = 0; i < origins.length; i++) {
            Origin o = origins[i];
            source[i] = pack(o.lane(), o.sourceBit());
            pointMultipliers[i] = o.pointMultiplier();
            structuralMultipliers[i] = o.structuralMultiplier();
        }
    }

    /** Internal arrays are newly allocated or never mutated after construction. */
    private TrackedSequence(BitSequence bits, long[] source,
            double[] pointMultipliers, double[] structuralMultipliers) {
        this.bits = Objects.requireNonNull(bits, "bits");
        this.source = source;
        this.pointMultipliers = pointMultipliers;
        this.structuralMultipliers = structuralMultipliers;
        if (source.length != bits.bitLength()
                || pointMultipliers.length != bits.bitLength()
                || structuralMultipliers.length != bits.bitLength()) {
            throw new IllegalArgumentException("origin count must match bit length");
        }
    }

    private static long pack(int lane, int position) {
        return ((long) lane << 32) | (position & 0xFFFF_FFFFL);
    }

    static TrackedSequence snapshot(BitSequence bits, int lane, LocalRateSnapshot rates) {
        int length = bits.bitLength();
        long[] source = new long[length];
        double[] point = new double[length];
        double[] structural = new double[length];
        for (int i = 0; i < length; i++) {
            source[i] = pack(lane, i);
            point[i] = rates.multiplier(LocalRateSnapshot.Kind.POINT, i);
            structural[i] = rates.multiplier(LocalRateSnapshot.Kind.STRUCTURAL, i);
        }
        return new TrackedSequence(bits, source, point, structural);
    }

    static TrackedSequence fresh(BitSequence bits) {
        int length = bits.bitLength();
        long[] source = new long[length];
        double[] point = new double[length];
        double[] structural = new double[length];
        Arrays.fill(source, pack(-1, -1));
        Arrays.fill(point, 1.0);
        Arrays.fill(structural, 1.0);
        return new TrackedSequence(bits, source, point, structural);
    }

    BitSequence bits() { return bits; }
    int bitLength() { return bits.bitLength(); }

    Origin originAt(int index) {
        long packed = source[index];
        return new Origin((int) (packed >> 32), (int) packed,
                pointMultipliers[index], structuralMultipliers[index]);
    }

    Origin[] origins() {
        Origin[] result = new Origin[bitLength()];
        for (int i = 0; i < result.length; i++) result[i] = originAt(i);
        return result;
    }

    TrackedSequence flip(int index) {
        // The provenance arrays are private and immutable after construction.
        // Bit flips change values, not their biological source.
        return new TrackedSequence(bits.flip(index), source,
                pointMultipliers, structuralMultipliers);
    }

    TrackedSequence slice(int fromInclusive, int toExclusive) {
        if (fromInclusive < 0 || toExclusive < fromInclusive || toExclusive > bitLength()) {
            throw new IndexOutOfBoundsException("invalid tracked slice");
        }
        return new TrackedSequence(bits.slice(fromInclusive, toExclusive),
                Arrays.copyOfRange(source, fromInclusive, toExclusive),
                Arrays.copyOfRange(pointMultipliers, fromInclusive, toExclusive),
                Arrays.copyOfRange(structuralMultipliers, fromInclusive, toExclusive));
    }

    TrackedSequence concat(TrackedSequence other) {
        Objects.requireNonNull(other, "other");
        int originalSize = bitLength();
        int otherSize = other.bitLength();
        long[] nextSource = Arrays.copyOf(source, originalSize + otherSize);
        double[] nextPoint = Arrays.copyOf(pointMultipliers, originalSize + otherSize);
        double[] nextStructural = Arrays.copyOf(structuralMultipliers, originalSize + otherSize);
        System.arraycopy(other.source, 0, nextSource, originalSize, otherSize);
        System.arraycopy(other.pointMultipliers, 0, nextPoint, originalSize, otherSize);
        System.arraycopy(other.structuralMultipliers, 0, nextStructural, originalSize, otherSize);
        return new TrackedSequence(bits.concat(other.bits),
                nextSource, nextPoint, nextStructural);
    }

    TrackedSequence insert(int position, TrackedSequence inserted) {
        if (position < 0 || position > bitLength())
            throw new IndexOutOfBoundsException("invalid insert position");
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
        int n = bitLength();
        long[] newSource = new long[n];
        double[] newPoint = new double[n];
        double[] newStructural = new double[n];
        for (int i = 0; i < n; i++) {
            int last = n - 1 - i;
            newSource[i] = source[last];
            newPoint[i] = pointMultipliers[last];
            newStructural[i] = structuralMultipliers[last];
        }
        return new TrackedSequence(bits.reverse(), newSource, newPoint, newStructural);
    }

    double structuralBoundaryWeight(int boundary) {
        if (bitLength() == 0) return 1.0;
        if (boundary <= 0) return structuralMultipliers[0];
        if (boundary >= bitLength()) return structuralMultipliers[bitLength() - 1];
        return StrictMath.sqrt(structuralMultipliers[boundary - 1]
                * structuralMultipliers[boundary]);
    }
}
