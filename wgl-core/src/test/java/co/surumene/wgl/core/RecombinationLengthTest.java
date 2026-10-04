package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RecombinationLengthTest {
    @Test
    void normalCrossoversDoNotDuplicatePrefixesWhenSwitchingHomologues() {
        GenomeAddress address = new GenomeAddress(0x00, 0x00);
        TestProfile profile = TestProfile.defining(address);
        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        EngineConfig config = withoutMutation(EngineConfig.defaults());
        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(config);
        DiploidGenome parent = ((SynthesisResult.Success) engine.synthesize(profile, backbone,
                new BasicSynthesisTarget(Map.of(address, 0.62)), SynthesisContext.defaults(),
                new SplitMix64GenomeRandom(1234))).genome();
        int maxParent = Math.max(parent.chromosomePairs().getFirst().haplotypeA().bitLength(),
                parent.chromosomePairs().getFirst().haplotypeB().bitLength());

        for (long seed = 0; seed < 64; seed++) {
            BreedingResult result = engine.breed(profile, parent, parent,
                    new BreedingContext(backbone, 0.0, Set.of(), null, false), new SplitMix64GenomeRandom(seed));
            assertInstanceOf(BreedingResult.Success.class, result, "seed=" + seed);
            DiploidGenome child = ((BreedingResult.Success) result).genome();
            for (ChromosomePair pair : child.chromosomePairs()) {
                assertTrue(pair.haplotypeA().bitLength() <= maxParent + 256, "seed=" + seed);
                assertTrue(pair.haplotypeB().bitLength() <= maxParent + 256, "seed=" + seed);
            }
        }
    }

    private static EngineConfig withoutMutation(EngineConfig d) {
        EngineConfig.Structural s = d.mutation().structural();
        EngineConfig.Structural zero = new EngineConfig.Structural(0,0,0,0,0,
                s.insertionRandomSequenceRatio(), s.duplicationSameChromosomeRatio(),
                s.translocationReciprocalRatio(), s.translocationOtherChromosomeRatio(),
                s.insertionLengthP(), s.insertionLengthMaxBits(), s.deletionLengthP(), s.deletionLengthMaxBits(),
                s.duplicationLengthP(), s.duplicationLengthMaxBits(), s.inversionLengthP(), s.inversionLengthMaxBits(),
                s.translocationLengthP(), s.translocationLengthMaxBits());
        return new EngineConfig(d.homology(), d.recombination(), new EngineConfig.Mutation(0, zero,
                new EngineConfig.Nahr(0, d.mutation().nahr().structureMultiplierMax())),
                d.regulation(), d.localRates(), d.synthesizer(), d.eventRetryMax());
    }
}
