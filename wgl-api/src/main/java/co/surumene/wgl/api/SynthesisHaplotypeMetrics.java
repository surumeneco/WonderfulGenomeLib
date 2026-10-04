package co.surumene.wgl.api;

public record SynthesisHaplotypeMetrics(
        int chromosomeIndex,
        int haplotypeIndex,
        int bitLength,
        int geneCandidateCount,
        int recognizableBits) {
    public SynthesisHaplotypeMetrics {
        if (chromosomeIndex < 0) throw new IllegalArgumentException("chromosomeIndex must be >= 0");
        if (haplotypeIndex < 0 || haplotypeIndex > 1) {
            throw new IllegalArgumentException("haplotypeIndex must be 0 or 1");
        }
        if (bitLength < 0) throw new IllegalArgumentException("bitLength must be >= 0");
        if (geneCandidateCount < 0) throw new IllegalArgumentException("geneCandidateCount must be >= 0");
        if (recognizableBits < 0 || recognizableBits > bitLength) {
            throw new IllegalArgumentException("recognizableBits outside haplotype length");
        }
    }

    public double recognizableRatio() {
        return bitLength == 0 ? 0.0 : (double) recognizableBits / bitLength;
    }
}
