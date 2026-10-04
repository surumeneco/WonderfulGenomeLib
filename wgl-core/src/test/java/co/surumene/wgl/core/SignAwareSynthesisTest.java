package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SignAwareSynthesisTest {
    @Test
    void negativeSynthesisUsesNegativeBaseEffectSemantics() {
        GenomeAddress address = new GenomeAddress(0x03, 0x01);

        DirectContributionModel asymmetric = new DirectContributionModel() {
            @Override
            public double baseEffect(GenomeAddress candidate, boolean negative,
                                     int magnitudeCode, int expressionCode) {
                double m = magnitudeCode / 127.0;
                double e = expressionCode / 15.0;
                double absolute = (negative ? 2.0 : 1.0) * m * e;
                return negative ? -absolute : absolute;
            }

            @Override
            public double saturation(GenomeAddress candidate, double absoluteFinalEffect) {
                return -StrictMath.expm1(-absoluteFinalEffect);
            }

            @Override
            public int closestMagnitudeCode(GenomeAddress candidate,
                                            double desiredAbsoluteBaseEffect,
                                            int expressionCode) {
                return (int) StrictMath.round(
                        Math.min(1.0, desiredAbsoluteBaseEffect) * 127.0);
            }
        };

        GenomeProfile<Double> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("sign-aware", 1, new byte[32]);
            }
            @Override public boolean isDefinedAddress(GenomeAddress candidate) {
                return address.equals(candidate);
            }
            @Override public DirectContributionModel contributionModel(GenomeAddress candidate) {
                return asymmetric;
            }
            @Override public SynthesisAddressPlan synthesisPlan(GenomeAddress candidate, double target,
                                                                SynthesisContext context, GenomeRandom random) {
                return SynthesisAddressPlan.centeredDifference(
                        target, context.minPositiveGenes(), context.maxPositiveGenes());
            }
            @Override public Double mapPhenotype(DecodedGenome decoded) {
                AddressAggregate aggregate = decoded.aggregate(address);
                double q = 1.0 - aggregate.negativeSurvival();
                return 0.5 + 0.5 * (aggregate.positiveSaturation() - q);
            }
        };

        SynthesisTarget target = new SynthesisTarget() {
            @Override public Map<GenomeAddress, Double> continuousTargets() {
                return Map.of(address, 0.25);
            }
            @Override public boolean isSatisfied(DecodedGenome decoded, double tolerance) {
                AddressAggregate aggregate = decoded.aggregate(address);
                double q = 1.0 - aggregate.negativeSurvival();
                double score = 0.5 + 0.5 * (aggregate.positiveSaturation() - q);
                return StrictMath.abs(score - 0.25) <= tolerance;
            }
        };

        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
        SynthesisResult.Success success = assertInstanceOf(SynthesisResult.Success.class,
                engine.synthesize(profile, TestBackbones.singlePair(2048), target,
                        SynthesisContext.defaults(), new SplitMix64GenomeRandom(123L)));

        DecodeResult<Double> decoded = engine.decode(profile, success.genome());
        assertEquals(0.25, decoded.phenotype(), 0.002);
        assertTrue(decoded.decodedGenome().physicalGenes().stream()
                .anyMatch(g -> address.equals(g.address()) && g.negative()));
    }
}
