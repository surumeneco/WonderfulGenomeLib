package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReverseOrientationRegulationTest {
    private static final double ALPHA = -StrictMath.log(0.70);

    @Test
    void reverseOrientedCisUsesPhysicalStartMotifPositionsForDistance() {
        GenomeAddress target = new GenomeAddress(0x00, 0x20);
        GenomeProfile<DecodedGenome> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("reverse-cis-position", 1, new byte[32]);
            }

            @Override public boolean isDefinedAddress(GenomeAddress address) {
                return target.equals(address);
            }

            @Override public int minimumExtensionBits(GenomeAddress address) {
                return target.equals(address) ? 64 : 0;
            }

            @Override public DirectContributionModel contributionModel(GenomeAddress address) {
                return StandardDirectContributionModel.defaultModel();
            }

            @Override public DecodedGenome mapPhenotype(DecodedGenome decodedGenome) {
                return decodedGenome;
            }
        };

        BitSequence direct = GeneCodecV1.encode(
                target, false, 127, 15, BitSequence.fromBits("0".repeat(64)));
        BitSequence cis = GeneCodecV1.encodeRawEffect(
                new GenomeAddress(0x08, 0x00), 0x3F, 15, BitSequence.empty());

        BitSequence haplotype = direct.reverse().concat(cis.reverse());
        DiploidGenome genome = new DiploidGenome(
                1, List.of(new ChromosomePair(haplotype, BitSequence.empty())));

        AddressAggregate aggregate = WonderfulGenomeEngine.create(EngineConfig.defaults())
                .decode(profile, genome)
                .decodedGenome()
                .aggregate(target);

        int motifLength = GeneCodecV1.START.bitLength();
        int directStartMotif = direct.bitLength() - motifLength;
        int cisStartMotif = direct.bitLength() + cis.bitLength() - motifLength;
        double distance = cisStartMotif - directStartMotif;
        double radius = 128.0;
        double z = distance / radius;
        double attenuation = 1.0 - (3.0 * z * z - 2.0 * z * z * z);
        double multiplier = 1.0 + (EngineConfig.defaults().regulation().cisEnhancerMax() - 1.0)
                * attenuation;
        double expected = 1.0 - StrictMath.exp(-ALPHA * multiplier);

        assertEquals(expected, aggregate.score(), 1.0e-12);
    }
}
