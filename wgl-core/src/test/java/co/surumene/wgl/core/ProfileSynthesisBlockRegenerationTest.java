package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProfileSynthesisBlockRegenerationTest {
    @Test
    void regeneratesProfileSuppliedBlocksBeforeWholeGenomeRetry() {
        GenomeAddress targetAddress = new GenomeAddress(0x00, 0x04);
        AtomicInteger blockCalls = new AtomicInteger();

        GenomeProfile<Double> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("profile-block-regen", 1, new byte[32]);
            }

            @Override public boolean isDefinedAddress(GenomeAddress address) {
                return targetAddress.equals(address);
            }

            @Override public DirectContributionModel contributionModel(GenomeAddress address) {
                return StandardDirectContributionModel.defaultModel();
            }

            @Override public SynthesisAddressPlan synthesisPlan(
                    GenomeAddress address, double target,
                    SynthesisContext context, GenomeRandom random) {
                return new SynthesisAddressPlan(0.50, 0.0, 2, 2, 0, 0);
            }

            @Override public List<SynthesisBlock> synthesisBlocks(
                    SynthesisTarget target, SynthesisContext context, GenomeRandom random) {
                if (blockCalls.getAndIncrement() == 0) {
                    BitSequence targetHeader = BitSequence.fromBits(
                            Secded22.encode(targetAddress).toBitString());
                    BitSequence transSilencer = GeneCodecV1.encodeRawEffect(
                            new GenomeAddress(0x08, 0x03),
                            0xFF,
                            15,
                            targetHeader);
                    return List.of(SynthesisBlock.fixed(transSilencer, 0, 0));
                }
                return List.of(SynthesisBlock.fixed(BitSequence.fromBits("0"), 0, 0));
            }

            @Override public Double mapPhenotype(DecodedGenome decodedGenome) {
                return decodedGenome.aggregate(targetAddress).score();
            }
        };

        EngineConfig defaults = EngineConfig.defaults();
        EngineConfig config = new EngineConfig(
                defaults.homology(), defaults.recombination(), defaults.mutation(),
                defaults.regulation(), defaults.localRates(),
                new EngineConfig.Synthesizer(
                        defaults.synthesizer().convergenceTolerance(),
                        1,
                        defaults.synthesizer().microCorrectionMaxRatio()),
                defaults.eventRetryMax());

        SynthesisResult result = WonderfulGenomeEngine.create(config).synthesize(
                profile,
                TestBackbones.singlePair(2048),
                new BasicSynthesisTarget(Map.of(targetAddress, 0.50)),
                new SynthesisContext(2, 2, 0.0, 0.0, 1),
                new SplitMix64GenomeRandom(99173L));

        SynthesisResult.Success success = assertInstanceOf(
                SynthesisResult.Success.class, result, result.toString());
        assertEquals(0.50, success.decoded().decodedGenome().aggregate(targetAddress).score(),
                config.synthesizer().convergenceTolerance());
        assertEquals(2, blockCalls.get());
    }
}
