package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SynthesizerLocalAdjustmentRollbackTest {
    @Test
    void unsafeLocalAdjustmentCandidateIsRolledBackBeforeAddressRegeneration() {
        GenomeAddress address = new GenomeAddress(0x00, 0x03);
        java.util.concurrent.atomic.AtomicInteger plans = new java.util.concurrent.atomic.AtomicInteger();

        GenomeProfile<Double> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("rollback-profile", 1, new byte[32]);
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
                if (plans.getAndIncrement() == 0) {
                    // Deliberately unreachable with two positive genes, while the
                    // negative side keeps the requested phenotype near 0.5.
                    return new SynthesisAddressPlan(1.0, 0.5, 2, 2, 2, 2);
                }
                return new SynthesisAddressPlan(0.5, 0.0, 2, 2, 0, 0);
            }

            @Override public Double mapPhenotype(DecodedGenome decodedGenome) {
                return decodedGenome.aggregate(address).score();
            }
        };

        BackboneDefinition base = TestBackbones.singlePair(2048);
        GenomeSafetyPolicy boundedGrowth = (candidate, baseline) ->
                candidate.size() == baseline.size()
                        && candidate.getFirst() <= baseline.getFirst() + 320;
        BackboneDefinition backbone = new BackboneDefinition(
                "rollback-backbone",
                1,
                base.chromosomes(),
                boundedGrowth);

        EngineConfig defaults = EngineConfig.defaults();
        EngineConfig config = new EngineConfig(
                defaults.homology(), defaults.recombination(), defaults.mutation(),
                defaults.regulation(), defaults.localRates(),
                new EngineConfig.Synthesizer(
                        defaults.synthesizer().convergenceTolerance(),
                        1,
                        defaults.synthesizer().microCorrectionMaxRatio()),
                defaults.eventRetryMax());

        GenomeRandom deterministic = new GenomeRandom() {
            @Override public long nextLong() { return 0L; }
            @Override public double nextDouble() { return 0.9; }
            @Override public int nextInt(int bound) { return 0; }
            @Override public boolean nextBoolean() { return false; }
        };

        SynthesisResult result = WonderfulGenomeEngine.create(config).synthesize(
                profile,
                backbone,
                new BasicSynthesisTarget(Map.of(address, 0.5)),
                new SynthesisContext(2, 2, 0.0, 0.0, 1),
                deterministic);

        SynthesisResult.Success success = assertInstanceOf(
                SynthesisResult.Success.class, result, result.toString());
        assertEquals(0.5, success.decoded().decodedGenome().aggregate(address).score(),
                config.synthesizer().convergenceTolerance());
        assertEquals(2, plans.get());
    }
}
