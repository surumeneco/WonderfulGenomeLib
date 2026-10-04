package co.surumene.wgl.core;

import co.surumene.wgl.api.GenomeRandom;

final class Sampling {
    private Sampling() {}

    static int poisson(double lambda, GenomeRandom random) {
        if (lambda <= 0.0) return 0;
        // Current Engine revision uses Knuth sampling. The expected lambda in WGL is small.
        double limit = StrictMath.exp(-lambda);
        double product = 1.0;
        int k = 0;
        do {
            k++;
            product *= random.nextDouble();
        } while (product > limit && k < 10_000);
        return k - 1;
    }

    /** Geometric on {1,2,...}, rejecting samples above max instead of clamping them. */
    static int truncatedGeometric(double p, int max, GenomeRandom random) {
        if (!(p > 0.0 && p <= 1.0) || max < 1) throw new IllegalArgumentException("invalid truncated geometric parameters");
        if (p == 1.0) return 1;
        double logOneMinusP = StrictMath.log1p(-p);
        while (true) {
            double u = random.nextDouble();
            // nextDouble is in [0,1). log1p(-u) is finite except for an impossible u==1.
            int value = 1 + (int) StrictMath.floor(StrictMath.log1p(-u) / logOneMinusP);
            if (value >= 1 && value <= max) return value;
        }
    }

    static int weightedIndex(double[] weights, GenomeRandom random) {
        double total = 0.0;
        for (double weight : weights) {
            if (Double.isFinite(weight) && weight > 0.0) total += weight;
        }
        if (!(total > 0.0)) return -1;
        double roll = random.nextDouble() * total;
        int lastPositive = -1;
        for (int i = 0; i < weights.length; i++) {
            double weight = weights[i];
            if (!(Double.isFinite(weight) && weight > 0.0)) continue;
            lastPositive = i;
            roll -= weight;
            if (roll <= 0.0) return i;
        }
        return lastPositive;
    }
}
