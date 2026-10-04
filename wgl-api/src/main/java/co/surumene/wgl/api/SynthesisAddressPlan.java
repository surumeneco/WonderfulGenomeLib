package co.surumene.wgl.api;

/**
 * Profile-owned synthesis target in generic bounded-contribution space.
 * positiveSaturation is P = 1 - product(1-u_positive).
 * negativeSaturation is Q = 1 - product(1-u_negative), so negative survival N = 1-Q.
 */
public record SynthesisAddressPlan(
        double positiveSaturation,
        double negativeSaturation,
        int minPositiveGenes,
        int maxPositiveGenes,
        int minNegativeGenes,
        int maxNegativeGenes) {

    public SynthesisAddressPlan {
        validateSaturation(positiveSaturation, "positiveSaturation");
        validateSaturation(negativeSaturation, "negativeSaturation");
        validateGeneRange(positiveSaturation, minPositiveGenes, maxPositiveGenes, "positive");
        validateGeneRange(negativeSaturation, minNegativeGenes, maxNegativeGenes, "negative");
    }

    public static SynthesisAddressPlan boundedProduct(double target,
                                                      SynthesisContext context,
                                                      GenomeRandom random) {
        if (!Double.isFinite(target) || target < 0.0 || target > 1.0) {
            throw new IllegalArgumentException("target must be in [0,1]");
        }
        java.util.Objects.requireNonNull(context, "context");
        java.util.Objects.requireNonNull(random, "random");

        if (target == 0.0) {
            return new SynthesisAddressPlan(0.0, 0.0, 0, 0, 0, 0);
        }

        double draw = context.cancellationMin()
                + (context.cancellationMax() - context.cancellationMin()) * random.nextDouble();
        double q = Math.min(draw, Math.max(0.0, 0.98 - target));
        double survival = 1.0 - q;
        double p = target / survival;

        int negativeMax = q == 0.0 ? 0 : Math.max(1, context.maxPositiveGenes() / 2);
        return new SynthesisAddressPlan(
                p, q,
                context.minPositiveGenes(), context.maxPositiveGenes(),
                q == 0.0 ? 0 : 1, negativeMax);
    }

    /**
     * Maps score = 0.5 + 0.5 * (P - Q) to one-sided contributions.
     * This is useful for centered quantitative factors such as personality dimensions.
     */
    public static SynthesisAddressPlan centeredDifference(double score,
                                                          int minGenes,
                                                          int maxGenes) {
        if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score must be in [0,1]");
        }
        if (minGenes < 1 || maxGenes < minGenes) {
            throw new IllegalArgumentException("invalid gene count range");
        }

        double delta = 2.0 * score - 1.0;
        if (delta > 0.0) {
            return new SynthesisAddressPlan(delta, 0.0, minGenes, maxGenes, 0, 0);
        }
        if (delta < 0.0) {
            return new SynthesisAddressPlan(0.0, -delta, 0, 0, minGenes, maxGenes);
        }
        return new SynthesisAddressPlan(0.0, 0.0, 0, 0, 0, 0);
    }

    private static void validateSaturation(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0,1]");
        }
    }

    private static void validateGeneRange(double target, int min, int max, String name) {
        if (target == 0.0) {
            if (min != 0 || max != 0) {
                throw new IllegalArgumentException(name + " gene range must be 0..0 for zero target");
            }
            return;
        }
        if (min < 1 || max < min) {
            throw new IllegalArgumentException("invalid " + name + " gene count range");
        }
    }
}
