package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class FounderTemplateVariationTest {
    @Test
    void profileMayVaryEachFounderHaplotypeBeforeScaffoldResizing() {
        BackboneDefinition backbone = TestBackbones.singlePair(512);
        ChromosomeTemplate template = backbone.chromosomes().getFirst();
        int markerBit = template.markerLocus().first().position();
        RecordingProfile profile = new RecordingProfile(markerBit);

        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
        SynthesisResult.Success result = assertInstanceOf(
                SynthesisResult.Success.class,
                engine.synthesize(
                        profile,
                        backbone,
                        new BasicSynthesisTarget(Map.of()),
                        SynthesisContext.defaults(),
                        engine.standardRandom(20261005L)));

        assertEquals(List.of("0:0", "0:1"), profile.calls);
        assertNotEquals(
                template.templateBits().bitAt(markerBit),
                result.genome().chromosomePairs().getFirst().haplotypeA().bitAt(markerBit));
        assertNotEquals(
                template.templateBits().bitAt(markerBit + 1),
                result.genome().chromosomePairs().getFirst().haplotypeB().bitAt(markerBit + 1));
    }

    private static final class RecordingProfile implements GenomeProfile<DecodedGenome> {
        private final int markerBit;
        private final List<String> calls = new ArrayList<>();

        private RecordingProfile(int markerBit) {
            this.markerBit = markerBit;
        }

        @Override
        public ProfileDescriptor descriptor() {
            return new ProfileDescriptor("founder-template-variation-test", 1, new byte[32]);
        }

        @Override
        public boolean isDefinedAddress(GenomeAddress address) {
            return false;
        }

        @Override
        public DirectContributionModel contributionModel(GenomeAddress address) {
            throw new IllegalArgumentException("no direct addresses");
        }

        @Override
        public BitSequence founderTemplateBits(
                int chromosomeIndex,
                int haplotypeIndex,
                ChromosomeTemplate template,
                GenomeRandom random) {
            calls.add(chromosomeIndex + ":" + haplotypeIndex);
            int flip = haplotypeIndex == 0 ? markerBit : markerBit + 1;
            return template.templateBits().flip(flip);
        }

        @Override
        public DecodedGenome mapPhenotype(DecodedGenome decodedGenome) {
            return decodedGenome;
        }
    }
}
