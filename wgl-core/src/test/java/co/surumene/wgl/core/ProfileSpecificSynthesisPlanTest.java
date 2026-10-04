package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProfileSpecificSynthesisPlanTest {
    @Test
    void profileCanSynthesizeCenteredDifferenceScoreWithNegativeGenes() {
        GenomeAddress address = new GenomeAddress(0x03, 0x00);
        double targetScore = 0.25;

        GenomeProfile<Double> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("difference-profile", 1, new byte[32]);
            }

            @Override public boolean isDefinedAddress(GenomeAddress candidate) {
                return address.equals(candidate);
            }

            @Override public DirectContributionModel contributionModel(GenomeAddress candidate) {
                return StandardDirectContributionModel.defaultModel();
            }

            @Override public SynthesisAddressPlan synthesisPlan(GenomeAddress candidate, double target,
                                                                SynthesisContext context, GenomeRandom random) {
                assertEquals(address, candidate);
                return SynthesisAddressPlan.centeredDifference(target,
                        context.minPositiveGenes(), context.maxPositiveGenes());
            }

            @Override public Double mapPhenotype(DecodedGenome decoded) {
                AddressAggregate aggregate = decoded.aggregate(address);
                double q = 1.0 - aggregate.negativeSurvival();
                return 0.5 + 0.5 * (aggregate.positiveSaturation() - q);
            }
        };

        SynthesisTarget target = new SynthesisTarget() {
            @Override public Map<GenomeAddress, Double> continuousTargets() {
                return Map.of(address, targetScore);
            }

            @Override public boolean isSatisfied(DecodedGenome decoded, double tolerance) {
                AddressAggregate aggregate = decoded.aggregate(address);
                double q = 1.0 - aggregate.negativeSurvival();
                double score = 0.5 + 0.5 * (aggregate.positiveSaturation() - q);
                return StrictMath.abs(score - targetScore) <= tolerance;
            }
        };

        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
        SynthesisResult.Success success = assertInstanceOf(SynthesisResult.Success.class,
                engine.synthesize(profile, TestBackbones.singlePair(2048), target,
                        SynthesisContext.defaults(), new SplitMix64GenomeRandom(99)));

        AddressAggregate aggregate = engine.decode(profile, success.genome()).decodedGenome().aggregate(address);
        assertEquals(0.0, aggregate.positiveSaturation(), 1.0e-12);
        assertEquals(0.50, 1.0 - aggregate.negativeSurvival(), 0.002);
        assertEquals(targetScore, success.decoded().phenotype(), 0.002);
        assertTrue(success.decoded().decodedGenome().physicalGenes().stream()
                .anyMatch(g -> address.equals(g.address()) && g.negative()));
    }
}
