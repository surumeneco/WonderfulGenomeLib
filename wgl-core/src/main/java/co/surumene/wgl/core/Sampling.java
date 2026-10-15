package co.surumene.wgl.core;

import co.surumene.wgl.api.GenomeRandom;

final class Sampling {
    private Sampling() {}

    static double standardGaussian(GenomeRandom random) {
        if (random == null) throw new IllegalArgumentException("random must not be null");
        double u1;
        do {
            u1 = random.nextDouble();
            if (!Double.isFinite(u1) || u1 < 0.0 || u1 >= 1.0) {
                throw new IllegalArgumentException("GenomeRandom.nextDouble() must return a finite value in [0,1)");
            }
        } while (u1 == 0.0);
        double u2 = random.nextDouble();
        if (!Double.isFinite(u2) || u2 < 0.0 || u2 >= 1.0) {
            throw new IllegalArgumentException("GenomeRandom.nextDouble() must return a finite value in [0,1)");
        }
        return StrictMath.sqrt(-2.0 * StrictMath.log(u1))
                * StrictMath.cos(2.0 * StrictMath.PI * u2);
    }

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
