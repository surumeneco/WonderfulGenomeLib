package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NahrBreedingTest {
    @Test
    void forcedNahrUsesAlternativeRepeatMappingAndIsSeedDeterministic() {
        EngineConfig defaults = EngineConfig.defaults();
        EngineConfig.Structural s = defaults.mutation().structural();
        EngineConfig.Structural noStructural = new EngineConfig.Structural(
                0, 0, 0, 0, 0,
                s.insertionRandomSequenceRatio(), s.duplicationSameChromosomeRatio(),
                s.translocationReciprocalRatio(), s.translocationOtherChromosomeRatio(),
                s.insertionLengthP(), s.insertionLengthMaxBits(), s.deletionLengthP(), s.deletionLengthMaxBits(),
                s.duplicationLengthP(), s.duplicationLengthMaxBits(), s.inversionLengthP(), s.inversionLengthMaxBits(),
                s.translocationLengthP(), s.translocationLengthMaxBits());
        EngineConfig config = new EngineConfig(defaults.homology(), defaults.recombination(),
                new EngineConfig.Mutation(0.0, noStructural, new EngineConfig.Nahr(1.0, 8.0)),
                defaults.regulation(), defaults.localRates(), defaults.synthesizer(), defaults.eventRetryMax());

        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        BitSequence a = backbone.chromosomes().getFirst().templateBits();
        BitSequence b = a.insert(1200, a.slice(400, 448));
        DiploidGenome parent = new DiploidGenome(1, List.of(new ChromosomePair(a, b)));
        TestProfile profile = TestProfile.defining(new GenomeAddress(0x00, 0x00));
        BreedingContext context = new BreedingContext(backbone, 1.0, Set.of(), null, false);
        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(config);

        BreedingResult first = engine.breed(profile, parent, parent, context, new SplitMix64GenomeRandom(0));
        BreedingResult second = engine.breed(profile, parent, parent, context, new SplitMix64GenomeRandom(0));
        assertEquals(first, second);
        BreedingResult.Success success = assertInstanceOf(BreedingResult.Success.class, first);
        ChromosomePair child = success.genome().chromosomePairs().getFirst();
        assertTrue(backbone.safetyPolicy().isSafe(
                List.of(child.haplotypeA().bitLength()),
                backbone.baselineChromosomeLengths()));
        assertTrue(backbone.safetyPolicy().isSafe(
                List.of(child.haplotypeB().bitLength()),
                backbone.baselineChromosomeLengths()));
        assertTrue(
                child.haplotypeA().bitLength() != a.bitLength()
                        || child.haplotypeA().bitLength() != b.bitLength()
                        || child.haplotypeB().bitLength() != a.bitLength()
                        || child.haplotypeB().bitLength() != b.bitLength(),
                "forced NAHR should produce a structurally recombined product");
    }
}
