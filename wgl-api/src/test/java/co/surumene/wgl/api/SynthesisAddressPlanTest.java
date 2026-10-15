package co.surumene.wgl.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SynthesisAddressPlanTest {
    @Test
    void genericBoundedProductDoesNotImposeConsumerSpecificTwoPercentHeadroom() {
        SynthesisContext context = new SynthesisContext(4, 8, 0.005, 0.005, 1);
        GenomeRandom random = new GenomeRandom() {
            @Override public long nextLong() { return 0L; }
            @Override public double nextDouble() { return 0.0; }
            @Override public int nextInt(int bound) { return 0; }
            @Override public boolean nextBoolean() { return false; }
        };

        SynthesisAddressPlan plan = SynthesisAddressPlan.boundedProduct(0.99, context, random);

        assertEquals(0.005, plan.negativeSaturation(), 1.0e-12);
        assertEquals(0.99 / 0.995, plan.positiveSaturation(), 1.0e-12);
    }
}
