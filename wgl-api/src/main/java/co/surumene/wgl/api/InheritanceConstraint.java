package co.surumene.wgl.api;

/**
 * Physical parent-haplotype block preference used during one meiosis.
 * The constraint has no consumer-specific semantic name; consumers resolve
 * traits/addresses to physical blocks before calling WGL.
 */
public record InheritanceConstraint(int chromosomeIndex, int haplotypeIndex,
                                    int startBit, int endBitExclusive,
                                    double retentionProbability,
                                    double crossoverWeightMultiplier,
                                    boolean hardProtection) {
    public InheritanceConstraint {
        if (chromosomeIndex < 0) throw new IllegalArgumentException("chromosomeIndex must be >= 0");
        if (haplotypeIndex != 0 && haplotypeIndex != 1) throw new IllegalArgumentException("haplotypeIndex must be 0 or 1");
        if (startBit < 0 || endBitExclusive <= startBit) throw new IllegalArgumentException("invalid inheritance interval");
        if (!Double.isFinite(retentionProbability) || retentionProbability < 0 || retentionProbability > 1) {
            throw new IllegalArgumentException("retentionProbability must be in [0,1]");
        }
        if (!Double.isFinite(crossoverWeightMultiplier) || crossoverWeightMultiplier < 0) {
            throw new IllegalArgumentException("crossoverWeightMultiplier must be finite and >= 0");
        }
    }

    public static InheritanceConstraint hard(int chromosomeIndex, int haplotypeIndex,
                                             int startBit, int endBitExclusive) {
        return new InheritanceConstraint(chromosomeIndex, haplotypeIndex, startBit, endBitExclusive,
                1.0, 0.0, true);
    }

    public static InheritanceConstraint soft(int chromosomeIndex, int haplotypeIndex,
                                             int startBit, int endBitExclusive,
                                             double retentionProbability,
                                             double crossoverWeightMultiplier) {
        return new InheritanceConstraint(chromosomeIndex, haplotypeIndex, startBit, endBitExclusive,
                retentionProbability, crossoverWeightMultiplier, false);
    }
}
