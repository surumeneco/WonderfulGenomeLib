package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InheritanceConstraintTest {
    @Test
    void hardProtectedBlockAlwaysComesFromRequestedParentHaplotypeBeforeMutation() {
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
                new EngineConfig.Mutation(0.0, noStructural, new EngineConfig.Nahr(0.0, 8.0)),
                defaults.regulation(), defaults.localRates(), defaults.synthesizer(), defaults.eventRetryMax());

        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        BitSequence hapA = backbone.chromosomes().getFirst().templateBits();
        BitSequence hapB = hapA;
        for (int bit = 760; bit < 780; bit++) hapB = hapB.flip(bit);
        DiploidGenome parent = new DiploidGenome(1, List.of(new ChromosomePair(hapA, hapB)));
        InheritanceConstraint hard = InheritanceConstraint.hard(0, 0, 720, 820);
        ParentMeiosisPolicy policy = new ParentMeiosisPolicy(List.of(hard));
        BreedingContext context = new BreedingContext(backbone, 1.0, Set.of(), null, false, policy, policy);
        TestProfile profile = TestProfile.defining(new GenomeAddress(0x00, 0x00));
        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(config);
        BitSequence expected = hapA.slice(720, 820);

        for (long seed = 0; seed < 16; seed++) {
            BreedingResult.Success child = assertInstanceOf(BreedingResult.Success.class,
                    engine.breed(profile, parent, parent, context, new SplitMix64GenomeRandom(seed)), "seed=" + seed);
            ChromosomePair pair = child.genome().chromosomePairs().getFirst();
            assertEquals(expected, pair.haplotypeA().slice(720, 820), "parent A seed=" + seed);
            assertEquals(expected, pair.haplotypeB().slice(720, 820), "parent B seed=" + seed);
        }
    }
}
