package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import co.surumene.wgl.api.GenomeRandom;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static final class ZeroRandom implements GenomeRandom {
        @Override public long nextLong() { return 0L; }
        @Override public double nextDouble() { return 0.0; }
        @Override public int nextInt(int bound) { return 0; }
        @Override public boolean nextBoolean() { return false; }
    }
}
