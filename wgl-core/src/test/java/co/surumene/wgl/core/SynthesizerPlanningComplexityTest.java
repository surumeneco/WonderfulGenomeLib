package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SynthesizerPlanningComplexityTest {
    @Test
    void wideGeneCountRangeDoesNotReevaluateAllTailMagnitudesForEveryCandidate() {
        GenomeAddress address = new GenomeAddress(0x00, 0x00);
        CountingModel model = new CountingModel(StandardDirectContributionModel.defaultModel());

        GenomeProfile<Double> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("planning-complexity", 1, new byte[32]);
            }

            @Override public boolean isDefinedAddress(GenomeAddress candidate) {
                return address.equals(candidate);
            }

            @Override public DirectContributionModel contributionModel(GenomeAddress candidate) {
                return model;
            }

            @Override public SynthesisAddressPlan synthesisPlan(
                    GenomeAddress candidate,
                    double target,
                    SynthesisContext context,
                    GenomeRandom random) {
                return new SynthesisAddressPlan(target, 0.0, 16, 24, 0, 0);
            }

            @Override public Double mapPhenotype(DecodedGenome decoded) {
                return decoded.aggregate(address).score();
            }
        };

        SynthesisTarget target = new BasicSynthesisTarget(Map.of(address, 0.40));
        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());

        SynthesisResult result = engine.synthesize(
                profile,
                TestBackbones.singlePair(4096),
                target,
                new SynthesisContext(1, 1, 0.0, 0.0, 1),
                new SplitMix64GenomeRandom(42));

        assertInstanceOf(SynthesisResult.Success.class, result, result.toString());
        assertTrue(model.baseEffectCalls.get() < 10_000,
                "planning should reuse magnitude evaluations; calls=" + model.baseEffectCalls.get());
    }

    private static final class CountingModel implements DirectContributionModel {
        private final DirectContributionModel delegate;
        private final AtomicInteger baseEffectCalls = new AtomicInteger();

        private CountingModel(DirectContributionModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public double baseEffect(
                GenomeAddress address,
                boolean negative,
                int magnitudeCode,
                int expressionCode) {
            baseEffectCalls.incrementAndGet();
            return delegate.baseEffect(address, negative, magnitudeCode, expressionCode);
        }

        @Override
        public double saturation(GenomeAddress address, double absoluteFinalEffect) {
            return delegate.saturation(address, absoluteFinalEffect);
        }

        @Override
        public int closestMagnitudeCode(
                GenomeAddress address,
                double desiredAbsoluteBaseEffect,
                int expressionCode) {
            return delegate.closestMagnitudeCode(address, desiredAbsoluteBaseEffect, expressionCode);
        }
    }
}
