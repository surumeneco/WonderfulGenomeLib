package co.surumene.wgl.core;

import co.surumene.wgl.api.GenomeRandom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SamplingTest {
    @Test
    void standardGaussianHasStableEngineRevisionGoldenValue() {
        double value = Sampling.standardGaussian(new SplitMix64GenomeRandom(20261004L));
        assertEquals(-0.4538944078092156, value, 1e-15);
    }

    @Test
    void truncatedGeometricRedrawsValuesAboveMaxInsteadOfClamping() {
        SequenceRandom random = new SequenceRandom(0.99, 0.0);
        assertEquals(1, Sampling.truncatedGeometric(0.5, 2, random));
        assertTrue(random.doubleCalls >= 2, "first oversized draw must have been rejected");
    }

    private static final class SequenceRandom implements GenomeRandom {
        private final double[] doubles;
        private int index;
        int doubleCalls;
        SequenceRandom(double... doubles) { this.doubles = doubles; }
        @Override public long nextLong() { return 0; }
        @Override public double nextDouble() { doubleCalls++; return doubles[Math.min(index++, doubles.length - 1)]; }
        @Override public int nextInt(int bound) { return 0; }
        @Override public boolean nextBoolean() { return false; }
    }
}
