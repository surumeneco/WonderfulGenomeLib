package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FounderScaffoldSynthesisTest {
    @Test
    void legacyTemplateConstructorMeansExactScaffold() {
        BitSequence bits = TestSequences.patterned(1024, 44L);
        ChromosomeTemplate template = new ChromosomeTemplate(bits, List.of(), null);

        assertEquals(FounderScaffoldTolerance.exact(), template.founderScaffoldTolerance());

        BackboneDefinition backbone = new BackboneDefinition(
                "exact-scaffold", 1, List.of(template), new StandardGenomeSafetyPolicyV1());
        SynthesisResult.Success result = synthesizeEmpty(backbone, 1L);

        assertEquals(1024, result.genome().chromosomePairs().getFirst().haplotypeA().bitLength());
        assertEquals(1024, result.genome().chromosomePairs().getFirst().haplotypeB().bitLength());
    }

    @Test
    void variableToleranceSamplesIndependentFounderLengthsDeterministically() {
        BitSequence bits = TestSequences.patterned(1000, 45L);
        ChromosomeTemplate template = new ChromosomeTemplate(
                bits,
                List.of(),
                null,
                new FounderScaffoldTolerance(0.05, 0.90, 1.10));
        BackboneDefinition backbone = new BackboneDefinition(
                "variable-scaffold", 1, List.of(template), new StandardGenomeSafetyPolicyV1());

        SynthesisResult.Success first = synthesizeEmpty(backbone, 20261004L);
        SynthesisResult.Success second = synthesizeEmpty(backbone, 20261004L);

        ChromosomePair pair = first.genome().chromosomePairs().getFirst();
        assertEquals(first.genome(), second.genome());
        assertTrue(pair.haplotypeA().bitLength() >= 900 && pair.haplotypeA().bitLength() <= 1100);
        assertTrue(pair.haplotypeB().bitLength() >= 900 && pair.haplotypeB().bitLength() <= 1100);
        assertTrue(pair.haplotypeA().bitLength() != 1000 || pair.haplotypeB().bitLength() != 1000);
    }

    @Test
    void variableLengthAdjustmentPreservesEveryProtectedAnchorWindow() {
        BackboneDefinition base = TestBackbones.singlePair(1024);
        ChromosomeTemplate original = base.chromosomes().getFirst();
        ChromosomeTemplate variable = new ChromosomeTemplate(
                original.templateBits(),
                original.anchors(),
                original.markerLocus(),
                new FounderScaffoldTolerance(0.05, 0.90, 1.10));
        BackboneDefinition backbone = new BackboneDefinition(
                "anchor-preservation",
                1,
                List.of(variable),
                new StandardGenomeSafetyPolicyV1());

        SynthesisResult.Success result = synthesizeEmpty(backbone, 734L);
        ChromosomePair pair = result.genome().chromosomePairs().getFirst();

        for (AnchorSeed anchor : original.anchors()) {
            assertTrue(contains(pair.haplotypeA(), anchor.canonicalBits()));
            assertTrue(contains(pair.haplotypeB(), anchor.canonicalBits()));
        }
        assertTrue(contains(pair.haplotypeA(), original.markerLocus().first().canonicalBits()));
        assertTrue(contains(pair.haplotypeA(), original.markerLocus().second().canonicalBits()));
        assertTrue(contains(pair.haplotypeB(), original.markerLocus().first().canonicalBits()));
        assertTrue(contains(pair.haplotypeB(), original.markerLocus().second().canonicalBits()));
    }

    private static boolean contains(BitSequence haystack, BitSequence needle) {
        if (needle.bitLength() == 0) return true;
        for (int i = 0; i + needle.bitLength() <= haystack.bitLength(); i++) {
            if (haystack.slice(i, i + needle.bitLength()).equals(needle)) return true;
        }
        return false;
    }

    private static SynthesisResult.Success synthesizeEmpty(BackboneDefinition backbone, long seed) {
        GenomeProfile<DecodedGenome> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("founder-scaffold-test", 1, new byte[32]);
            }
            @Override public boolean isDefinedAddress(GenomeAddress address) { return false; }
            @Override public DirectContributionModel contributionModel(GenomeAddress address) {
                return StandardDirectContributionModel.defaultModel();
            }
            @Override public DecodedGenome mapPhenotype(DecodedGenome decodedGenome) {
                return decodedGenome;
            }
        };

        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
        return assertInstanceOf(SynthesisResult.Success.class, engine.synthesize(
                profile,
                backbone,
                new BasicSynthesisTarget(Map.of()),
                SynthesisContext.defaults(),
                seed));
    }
}
