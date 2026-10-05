package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class ProfileBlockResidualSynthesisTest {
    @Test
    void positiveProfileBlockContributionIsSubtractedFromContinuousSynthesisTarget() {
        GenomeAddress address = new GenomeAddress(0x00, 0x02);
        DirectContributionModel model = StandardDirectContributionModel.defaultModel();

        GenomeProfile<Double> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("positive-profile-block", 1, new byte[32]);
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
                return new SynthesisAddressPlan(target, 0.0, 4, 4, 0, 0);
            }

            @Override public List<SynthesisBlock> synthesisBlocks(
                    SynthesisTarget target,
                    SynthesisContext context,
                    GenomeRandom random) {
                BitSequence positive = GeneCodecV1.encode(
                        address, false, 40, 15, BitSequence.empty());
                BitSequence guard = BitSequence.fromBits("0".repeat(32));
                return List.of(SynthesisBlock.fixed(
                        guard.concat(positive).concat(guard), 0, 0));
            }

            @Override public Double mapPhenotype(DecodedGenome decodedGenome) {
                return decodedGenome.aggregate(address).score();
            }
        };

        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
        double targetScore = 0.50;
        SynthesisResult result = engine.synthesize(
                profile,
                TestBackbones.singlePair(2048),
                new BasicSynthesisTarget(Map.of(address, targetScore)),
                new SynthesisContext(4, 4, 0.0, 0.0, 4),
                new SplitMix64GenomeRandom(2026100504L));

        SynthesisResult.Success success = assertInstanceOf(
                SynthesisResult.Success.class, result, result.toString());
        assertEquals(
                targetScore,
                success.decoded().decodedGenome().aggregate(address).score(),
                EngineConfig.defaults().synthesizer().convergenceTolerance());
        org.junit.jupiter.api.Assertions.assertTrue(
                success.decoded().decodedGenome().physicalGenes().stream()
                        .anyMatch(gene -> address.equals(gene.address())
                                && gene.magnitudeCode() == 40
                                && gene.expressionCode() == 15),
                "profile-supplied positive gene must survive canonical decoding");
    }
}
