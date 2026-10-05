package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ReservedSynthesisTargetTest {
    @Test
    void synthesisRejectsFormatReservedTargetBeforeGeneratingGenome() {
        GenomeAddress reserved = new GenomeAddress(0xF0, 0x00);
        GenomeProfile<DecodedGenome> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("reserved-synthesis-test", 1, new byte[32]);
            }
            @Override public boolean isDefinedAddress(GenomeAddress address) {
                return reserved.equals(address);
            }
            @Override public DirectContributionModel contributionModel(GenomeAddress address) {
                return StandardDirectContributionModel.defaultModel();
            }
            @Override public DecodedGenome mapPhenotype(DecodedGenome decodedGenome) {
                return decodedGenome;
            }
        };

        SynthesisResult result = WonderfulGenomeEngine.create(EngineConfig.defaults()).synthesize(
                profile,
                TestBackbones.singlePair(512),
                new BasicSynthesisTarget(Map.of(reserved, 0.30)),
                new SynthesisContext(1, 1, 0.0, 0.0, 1),
                new SplitMix64GenomeRandom(2026100503L));

        SynthesisResult.Failure failure = assertInstanceOf(SynthesisResult.Failure.class, result);
        assertEquals(SynthesisFailureReason.INVALID_TARGET, failure.reason());
    }
}
