package co.surumene.wgl.api;

/**
 * Physical forward-orientation homology block between the two haplotypes of one chromosome pair.
 * Coordinates are half-open bit ranges in haplotype A and B.
 */
public record DecodedHomologyBlock(
        int chromosomeIndex,
        int startA,
        int endAExclusive,
        int startB,
        int endBExclusive) {

    public DecodedHomologyBlock {
        if (chromosomeIndex < 0) throw new IllegalArgumentException("chromosomeIndex must be >= 0");
        if (startA < 0 || endAExclusive <= startA) throw new IllegalArgumentException("invalid haplotype A range");
        if (startB < 0 || endBExclusive <= startB) throw new IllegalArgumentException("invalid haplotype B range");
    }
}
