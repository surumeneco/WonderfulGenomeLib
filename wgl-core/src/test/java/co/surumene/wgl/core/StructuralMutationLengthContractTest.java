package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import co.surumene.wgl.api.GenomeRandom;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StructuralMutationLengthContractTest {
    @Test
    void sourceIntervalKeepsSampledLengthInsteadOfClampingToShortChromosome() throws Exception {
        EngineConfig config = EngineConfig.defaults();
        BreedingEngine engine = new BreedingEngine(config, new GenomeDecoderEngine(config));
        List<TrackedSequence> snapshot = List.of(
                TrackedSequence.fresh(BitSequence.fromBits("0000")),
                TrackedSequence.fresh(BitSequence.fromBits("0".repeat(8))));

        Method choose = BreedingEngine.class.getDeclaredMethod(
                "chooseSourceInterval", List.class, int.class, GenomeRandom.class);
        choose.setAccessible(true);

        Object interval = choose.invoke(engine, snapshot, 6, new ZeroRandom());
        assertNotNull(interval);

        Method chromosome = interval.getClass().getDeclaredMethod("chromosome");
        Method start = interval.getClass().getDeclaredMethod("start");
        Method end = interval.getClass().getDeclaredMethod("end");
        chromosome.setAccessible(true);
        start.setAccessible(true);
        end.setAccessible(true);

        int selectedChromosome = (int) chromosome.invoke(interval);
        int selectedStart = (int) start.invoke(interval);
        int selectedEnd = (int) end.invoke(interval);

        assertEquals(1, selectedChromosome);
        assertEquals(6, selectedEnd - selectedStart);
    }

    @Test
    void copyInsertionDoesNotSilentlyBecomeRandomInsertionWhenSampledSourceLengthIsUnavailable() throws Exception {
        EngineConfig config = EngineConfig.defaults();
        BreedingEngine engine = new BreedingEngine(config, new GenomeDecoderEngine(config));
        StructuralMutationStage stage = new StructuralMutationStage(List.of(
                TrackedSequence.fresh(BitSequence.fromBits("0000"))));

        Method insertion = BreedingEngine.class.getDeclaredMethod(
                "insertion", StructuralMutationStage.class, EngineConfig.Structural.class, GenomeRandom.class);
        insertion.setAccessible(true);

        boolean planned = (boolean) insertion.invoke(
                engine,
                stage,
                config.mutation().structural(),
                new SequencedDoubleRandom(0.0, 0.15, 0.90));

        assertFalse(planned);
    }

    private static final class ZeroRandom implements GenomeRandom {
        @Override public long nextLong() { return 0L; }
        @Override public double nextDouble() { return 0.0; }
        @Override public int nextInt(int bound) { return 0; }
        @Override public boolean nextBoolean() { return false; }
    }

    private static final class SequencedDoubleRandom implements GenomeRandom {
        private final double[] values;
        private int index;

        private SequencedDoubleRandom(double... values) {
            this.values = values.clone();
        }

        @Override public long nextLong() { return 0L; }
        @Override public double nextDouble() {
            if (index >= values.length) throw new AssertionError("unexpected nextDouble call");
            return values[index++];
        }
        @Override public int nextInt(int bound) { return 0; }
        @Override public boolean nextBoolean() { return false; }
    }
}
