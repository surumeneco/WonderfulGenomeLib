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

    @Test
    void diploidAndGameteMixUsesMeiosisOnlyForTheDiploidSide() {
        WonderfulGenomeEngine engine =
                WonderfulGenomeEngine.create(EngineConfig.defaults());
        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        BitSequence template = backbone.chromosomes().getFirst().templateBits();
        BitSequence fixed = template.flip(1200);
        DiploidGenome diploid = new DiploidGenome(
                1, List.of(new ChromosomePair(template, template)));
        HaploidGenome gamete = new HaploidGenome(1, List.of(fixed));
        TestProfile profile =
                TestProfile.defining(new GenomeAddress(0x00, 0x00));

        assertTrue(engine.assessCompatibility(
                new BreedingParentSource.DiploidParent(diploid),
                new BreedingParentSource.Gamete(gamete),
                null).compatible());

        BreedingResult.Success success = assertInstanceOf(
                BreedingResult.Success.class,
                engine.breed(
                        profile,
                        new BreedingParentSource.DiploidParent(diploid),
                        new BreedingParentSource.Gamete(gamete),
                        BreedingContext.standard(backbone),
                        new SplitMix64GenomeRandom(54321L)));

        assertEquals(
                fixed,
                success.genome().chromosomePairs().getFirst().haplotypeB());
    }

    @Test
    void incompatibleFixedGametesReturnNormalNoViableOffspring() {
        WonderfulGenomeEngine engine =
                WonderfulGenomeEngine.create(EngineConfig.defaults());
        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        BitSequence a = backbone.chromosomes().getFirst().templateBits();
        BitSequence unrelated = TestSequences.patterned(2048, 0x5C0FFEE1L);
        TestProfile profile =
                TestProfile.defining(new GenomeAddress(0x00, 0x00));

        BreedingResult.NoViableOffspring failure = assertInstanceOf(
                BreedingResult.NoViableOffspring.class,
                engine.breed(
                        profile,
                        new BreedingParentSource.Gamete(
                                new HaploidGenome(1, List.of(a))),
                        new BreedingParentSource.Gamete(
                                new HaploidGenome(1, List.of(unrelated))),
                        BreedingContext.standard(backbone),
                        new SplitMix64GenomeRandom(1L)));

        assertEquals(
                BreedingFailureReason.INSUFFICIENT_CROSS_PARENT_HOMOLOGY,
                failure.reason());
    }

    @Test
    void customCompatibilityPolicyRejectionUsesGenericPolicyFailureReason() {
        WonderfulGenomeEngine engine =
                WonderfulGenomeEngine.create(EngineConfig.defaults());
        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        BitSequence template = backbone.chromosomes().getFirst().templateBits();
        TestProfile profile =
                TestProfile.defining(new GenomeAddress(0x00, 0x00));

        CompatibilityPolicy deny =
                (a, b) -> new CompatibilityReport(
                        false, "consumer-denied", List.of(false));
        BreedingContext context = new BreedingContext(
                backbone,
                1.0,
                java.util.Set.of(),
                deny,
                false);

        BreedingResult.NoViableOffspring failure = assertInstanceOf(
                BreedingResult.NoViableOffspring.class,
                engine.breed(
                        profile,
                        new BreedingParentSource.Gamete(
                                new HaploidGenome(1, List.of(template))),
                        new BreedingParentSource.Gamete(
                                new HaploidGenome(1, List.of(template))),
                        context,
                        new SplitMix64GenomeRandom(3L)));

        assertEquals(
                BreedingFailureReason.COMPATIBILITY_POLICY_REJECTED,
                failure.reason());
        assertEquals("consumer-denied", failure.detail());
    }

    @Test
    void parentSourceChromosomeCountMismatchIsNormalFailure() {
        WonderfulGenomeEngine engine =
                WonderfulGenomeEngine.create(EngineConfig.defaults());
        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        BitSequence template = backbone.chromosomes().getFirst().templateBits();
        TestProfile profile =
                TestProfile.defining(new GenomeAddress(0x00, 0x00));

        BreedingResult.NoViableOffspring failure = assertInstanceOf(
                BreedingResult.NoViableOffspring.class,
                engine.breed(
                        profile,
                        new BreedingParentSource.Gamete(
                                new HaploidGenome(1, List.of(template))),
                        new BreedingParentSource.Gamete(
                                new HaploidGenome(
                                        1, List.of(template, template))),
                        BreedingContext.standard(backbone),
                        new SplitMix64GenomeRandom(2L)));

        assertEquals(
                BreedingFailureReason.CHROMOSOME_COUNT_MISMATCH,
                failure.reason());
    }
}
