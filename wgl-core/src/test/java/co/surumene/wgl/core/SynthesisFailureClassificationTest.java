package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SynthesisFailureClassificationTest {
    @Test
    void laterSafetyRejectionDoesNotEraseEarlierSafeCandidate() {
        AtomicInteger safetyChecks = new AtomicInteger();
        GenomeProfile<DecodedGenome> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("failure-classification", 1, new byte[32]);
            }

            @Override public boolean isDefinedAddress(GenomeAddress address) {
                return false;
            }

            @Override public DirectContributionModel contributionModel(GenomeAddress address) {
                return StandardDirectContributionModel.defaultModel();
            }

            @Override public SynthesisSafetyPolicy synthesisSafetyPolicy() {
                return (metrics, decoded) -> safetyChecks.getAndIncrement() == 0;
            }

            @Override public DecodedGenome mapPhenotype(DecodedGenome decodedGenome) {
                return decodedGenome;
            }
        };

        SynthesisTarget neverSatisfied = new SynthesisTarget() {
            @Override public Map<GenomeAddress, Double> continuousTargets() {
                return Map.of();
            }

            @Override public boolean isSatisfied(DecodedGenome decoded, double tolerance) {
                return false;
            }
        };

        SynthesisResult result = WonderfulGenomeEngine.create(EngineConfig.defaults()).synthesize(
                profile,
                TestBackbones.singlePair(512),
                neverSatisfied,
                new SynthesisContext(2, 2, 0.0, 0.0, 2),
                new SplitMix64GenomeRandom(2026100502L));

        SynthesisResult.Failure failure = assertInstanceOf(SynthesisResult.Failure.class, result);
        assertEquals(SynthesisFailureReason.CONVERGENCE_LIMIT, failure.reason());
    }
}
