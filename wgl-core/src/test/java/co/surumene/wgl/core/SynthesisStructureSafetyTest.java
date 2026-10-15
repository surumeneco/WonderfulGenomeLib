package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SynthesisStructureSafetyTest {
    @Test
    void profileCanRejectFounderByGenericSynthesisMetrics() {
        GenomeAddress address = new GenomeAddress(0x00, 0x06);
        AtomicReference<SynthesisMetrics> seen = new AtomicReference<>();

        GenomeProfile<Double> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("structure-safety", 1, new byte[32]);
            }

            @Override public boolean isDefinedAddress(GenomeAddress candidate) {
                return address.equals(candidate);
            }

            @Override public DirectContributionModel contributionModel(GenomeAddress candidate) {
                return StandardDirectContributionModel.defaultModel();
            }

            @Override public SynthesisAddressPlan synthesisPlan(
                    GenomeAddress candidate, double target,
                    SynthesisContext context, GenomeRandom random) {
                return new SynthesisAddressPlan(0.5, 0.0, 2, 2, 0, 0);
            }

            @Override public SynthesisSafetyPolicy synthesisSafetyPolicy() {
                return (metrics, decoded) -> {
                    seen.set(metrics);
                    return metrics.totalGeneCandidateCount() <= 1;
                };
            }

            @Override public Double mapPhenotype(DecodedGenome decodedGenome) {
                return decodedGenome.aggregate(address).score();
            }
        };

        SynthesisResult result = WonderfulGenomeEngine.create(EngineConfig.defaults()).synthesize(
                profile,
                TestBackbones.singlePair(2048),
                new BasicSynthesisTarget(Map.of(address, 0.5)),
                new SynthesisContext(2, 2, 0.0, 0.0, 1),
                new SplitMix64GenomeRandom(2026100501L));

        SynthesisResult.Failure failure = assertInstanceOf(
                SynthesisResult.Failure.class, result, result.toString());
        assertEquals(SynthesisFailureReason.SAFETY_REJECTED, failure.reason());

        SynthesisMetrics metrics = assertDoesNotThrow(() -> {
            assertNotNull(seen.get());
            return seen.get();
        });
        assertTrue(metrics.totalGeneCandidateCount() >= 2);
        assertEquals(2, metrics.haplotypes().size());
        assertTrue(metrics.haplotypes().stream().allMatch(h -> h.bitLength() > 0));
        assertTrue(metrics.haplotypes().stream()
                .allMatch(h -> h.recognizableBits() >= 0
                        && h.recognizableBits() <= h.bitLength()
                        && h.recognizableRatio() >= 0.0
                        && h.recognizableRatio() <= 1.0));
    }
}
