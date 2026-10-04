package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SynthesizerProfileExtensionTest {
    @Test
    void profileSuppliesRequiredExtensionPayloadDuringSynthesis() {
        GenomeAddress address = new GenomeAddress(0x02, 0x00);
        BitSequence expectedExtension = BitSequence.fromBits("1010101010101010");
        GenomeProfile<DecodedGenome> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("extension-test", 1, new byte[32]);
            }

            @Override public boolean isDefinedAddress(GenomeAddress candidate) {
                return address.equals(candidate);
            }

            @Override public int minimumExtensionBits(GenomeAddress candidate) {
                return address.equals(candidate) ? 16 : 0;
            }

            @Override public BitSequence synthesisExtension(GenomeAddress candidate,
                                                            SynthesisTarget target,
                                                            GenomeRandom random) {
                return address.equals(candidate) ? expectedExtension : BitSequence.empty();
            }

            @Override public DirectContributionModel contributionModel(GenomeAddress candidate) {
                return StandardDirectContributionModel.defaultModel();
            }

            @Override public DecodedGenome mapPhenotype(DecodedGenome decodedGenome) {
                return decodedGenome;
            }
        };

        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        SynthesisTarget target = new BasicSynthesisTarget(Map.of(address, 0.30));

        SynthesisResult.Success success = assertInstanceOf(SynthesisResult.Success.class,
                engine.synthesize(profile, backbone, target, SynthesisContext.defaults(),
                        new SplitMix64GenomeRandom(2026)));

        assertTrue(engine.decode(profile, success.genome()).decodedGenome().physicalGenes().stream()
                .filter(g -> address.equals(g.address()))
                .allMatch(g -> expectedExtension.equals(g.extension())));
    }
}
