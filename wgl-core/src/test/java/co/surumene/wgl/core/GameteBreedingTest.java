package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class GameteBreedingTest {
    @Test
    void fixedGametesAreCombinedWithoutRunningMeiosisOrMutationAgain() {
        WonderfulGenomeEngine engine =
                WonderfulGenomeEngine.create(EngineConfig.defaults());
        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        BitSequence a = backbone.chromosomes().getFirst().templateBits();
        BitSequence b = a.flip(1000);
        HaploidGenome gameteA = new HaploidGenome(1, List.of(a));
        HaploidGenome gameteB = new HaploidGenome(1, List.of(b));
        TestProfile profile =
                TestProfile.defining(new GenomeAddress(0x00, 0x00));

        BreedingResult.Success success = assertInstanceOf(
                BreedingResult.Success.class,
                engine.breed(
                        profile,
                        new BreedingParentSource.Gamete(gameteA),
                        new BreedingParentSource.Gamete(gameteB),
                        BreedingContext.standard(backbone),
                        new SplitMix64GenomeRandom(12345L)));

        assertEquals(
                new DiploidGenome(
                        1,
                        List.of(new ChromosomePair(a, b))),
                success.genome());
    }
}
