package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SynthesizerBlockRegenerationTest {
    @Test
    void regeneratesProblematicAddressBlockBeforeWholeGenomeRetry() {
        GenomeAddress address = new GenomeAddress(0x00, 0x02);

        GenomeProfile<Double> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("regen-profile", 1, new byte[32]);
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
                if (random.nextDouble() < 0.5) {
                    // Same target: P * (1-Q) = 1.0 * 0.5 = 0.5,
                    // but P=1 cannot be reached by the configured two-gene initial block.
                    return new SynthesisAddressPlan(1.0, 0.5, 2, 2, 2, 2);
                }
                return new SynthesisAddressPlan(0.5, 0.0, 2, 2, 0, 0);
            }

            @Override public Double mapPhenotype(DecodedGenome decodedGenome) {
                return decodedGenome.aggregate(address).score();
            }
        };

        EngineConfig defaults = EngineConfig.defaults();
        EngineConfig config = new EngineConfig(
                defaults.homology(), defaults.recombination(), defaults.mutation(),
                defaults.regulation(), defaults.localRates(),
                new EngineConfig.Synthesizer(
                        defaults.synthesizer().convergenceTolerance(),
                        1,
                        defaults.synthesizer().localAdjustmentMaxContributionRatio()),
                defaults.eventRetryMax());

        GenomeRandom random = new GenomeRandom() {
            private final SplitMix64GenomeRandom delegate = new SplitMix64GenomeRandom(4404L);
            private int doubles;

            @Override public long nextLong() { return delegate.nextLong(); }
            @Override public double nextDouble() { return doubles++ == 0 ? 0.0 : 0.9; }
            @Override public int nextInt(int bound) { return delegate.nextInt(bound); }
            @Override public boolean nextBoolean() { return delegate.nextBoolean(); }
        };

        SynthesisResult result = WonderfulGenomeEngine.create(config).synthesize(
                profile,
                TestBackbones.singlePair(2048),
                new BasicSynthesisTarget(Map.of(address, 0.5)),
                new SynthesisContext(2, 2, 0.0, 0.0, 1),
                random);

        SynthesisResult.Success success = assertInstanceOf(SynthesisResult.Success.class, result, result.toString());
        assertEquals(0.5, success.decoded().decodedGenome().aggregate(address).score(),
                config.synthesizer().convergenceTolerance());
    }

    @Test
    void regeneratingOneAddressPreservesOtherAddressAndProfileBlocks() {
        GenomeAddress stable = new GenomeAddress(0x00, 0x01);
        GenomeAddress problematic = new GenomeAddress(0x00, 0x02);
        GenomeAddress latent = new GenomeAddress(0x04, 0x01);
        java.util.concurrent.atomic.AtomicInteger problematicPlans = new java.util.concurrent.atomic.AtomicInteger();

        GenomeProfile<Double> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("regen-isolation", 1, new byte[32]);
            }
            @Override public boolean isDefinedAddress(GenomeAddress candidate) {
                return stable.equals(candidate) || problematic.equals(candidate) || latent.equals(candidate);
            }
            @Override public DirectContributionModel contributionModel(GenomeAddress candidate) {
                return StandardDirectContributionModel.defaultModel();
            }
            @Override public SynthesisAddressPlan synthesisPlan(
                    GenomeAddress candidate, double target,
                    SynthesisContext context, GenomeRandom random) {
                if (problematic.equals(candidate) && problematicPlans.getAndIncrement() == 0) {
                    return new SynthesisAddressPlan(1.0, 0.5, 2, 2, 2, 2);
                }
                return new SynthesisAddressPlan(target, 0.0, 2, 2, 0, 0);
            }
            @Override public java.util.List<SynthesisBlock> synthesisBlocks(
                    SynthesisTarget target, SynthesisContext context, GenomeRandom random) {
                return java.util.List.of(SynthesisBlock.fixed(
                        GeneCodecV1.encode(latent, false, 127, 15, BitSequence.empty()), 0, 0));
            }
            @Override public Double mapPhenotype(DecodedGenome decodedGenome) {
                return decodedGenome.aggregate(stable).score()
                        + decodedGenome.aggregate(problematic).score()
                        + decodedGenome.aggregate(latent).score();
            }
        };

        SynthesisTarget target = new SynthesisTarget() {
            @Override public Map<GenomeAddress, Double> continuousTargets() {
                return Map.of(stable, 0.30, problematic, 0.50);
            }
            @Override public boolean isSatisfied(DecodedGenome decoded, double tolerance) {
                return StrictMath.abs(decoded.aggregate(stable).score() - 0.30) <= tolerance
                        && StrictMath.abs(decoded.aggregate(problematic).score() - 0.50) <= tolerance
                        && decoded.aggregate(latent).score() >= 0.29;
            }
        };

        EngineConfig defaults = EngineConfig.defaults();
        EngineConfig config = new EngineConfig(
                defaults.homology(), defaults.recombination(), defaults.mutation(),
                defaults.regulation(), defaults.localRates(),
                new EngineConfig.Synthesizer(
                        defaults.synthesizer().convergenceTolerance(),
                        1,
                        defaults.synthesizer().localAdjustmentMaxContributionRatio()),
                defaults.eventRetryMax());

        SynthesisResult.Success success = assertInstanceOf(SynthesisResult.Success.class,
                WonderfulGenomeEngine.create(config).synthesize(
                        profile, TestBackbones.singlePair(2048), target,
                        new SynthesisContext(2, 2, 0.0, 0.0, 1),
                        new SplitMix64GenomeRandom(20261004L)));

        DecodedGenome decoded = success.decoded().decodedGenome();
        assertEquals(0.30, decoded.aggregate(stable).score(), config.synthesizer().convergenceTolerance());
        assertEquals(0.50, decoded.aggregate(problematic).score(), config.synthesizer().convergenceTolerance());
        assertTrue(decoded.aggregate(latent).score() >= 0.29);
        assertEquals(2, problematicPlans.get());
    }
}
