package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProfileSynthesisBlockTest {
    @Test
    void profileCanSupplyPhysicalFounderBlocksForNonContinuousTargets() {
        GenomeAddress trait = new GenomeAddress(0x04, 0x01);

        GenomeProfile<Double> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("block-profile", 1, new byte[32]);
            }

            @Override public boolean isDefinedAddress(GenomeAddress address) {
                return trait.equals(address);
            }

            @Override public DirectContributionModel contributionModel(GenomeAddress address) {
                return StandardDirectContributionModel.defaultModel();
            }

            @Override public List<SynthesisBlock> synthesisBlocks(
                    SynthesisTarget target, SynthesisContext context, GenomeRandom random) {
                BitSequence gene = GeneCodecV1.encode(
                        trait, false, 127, 15, BitSequence.empty());
                return List.of(SynthesisBlock.random(gene));
            }

            @Override public Double mapPhenotype(DecodedGenome decodedGenome) {
                return decodedGenome.aggregate(trait).score();
            }
        };

        SynthesisTarget target = new SynthesisTarget() {
            @Override public Map<GenomeAddress, Double> continuousTargets() {
                return Map.of();
            }

            @Override public boolean isSatisfied(DecodedGenome decoded, double tolerance) {
                return decoded.aggregate(trait).score() >= 0.29;
            }
        };

        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
        SynthesisResult.Success success = assertInstanceOf(SynthesisResult.Success.class,
                engine.synthesize(profile, TestBackbones.singlePair(1024), target,
                        SynthesisContext.defaults(), new SplitMix64GenomeRandom(909L)));

        assertTrue(engine.decode(profile, success.genome())
                .decodedGenome().aggregate(trait).score() >= 0.29);
    }
}
