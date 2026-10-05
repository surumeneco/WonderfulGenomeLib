package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReservedAddressContractTest {
    @Test
    void formatReservedTypeCannotBeActivatedByProfile() {
        GenomeAddress reserved = new GenomeAddress(0xF0, 0x00);
        GenomeProfile<DecodedGenome> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("reserved-address-test", 1, new byte[32]);
            }

            @Override public boolean isDefinedAddress(GenomeAddress address) {
                return reserved.equals(address);
            }

            @Override public DirectContributionModel contributionModel(GenomeAddress address) {
                return StandardDirectContributionModel.defaultModel();
            }

            @Override public DecodedGenome mapPhenotype(DecodedGenome decodedGenome) {
                return decodedGenome;
            }
        };

        BitSequence gene = GeneCodecV1.encode(reserved, false, 127, 15, BitSequence.empty());
        DiploidGenome genome = new DiploidGenome(
                1, List.of(new ChromosomePair(gene, BitSequence.empty())));

        DecodeResult<DecodedGenome> result =
                WonderfulGenomeEngine.create(EngineConfig.defaults()).decode(profile, genome);

        DecodedGene physical = result.decodedGenome().physicalGenes().getFirst();
        assertEquals(reserved, physical.address());
        assertFalse(physical.addressValid());
        assertTrue(result.decodedGenome().aggregates().isEmpty());
    }

    @Test
    void relayCannotTargetFormatReservedTypeEvenWhenProfileDefinesIt() {
        GenomeAddress source = new GenomeAddress(0x00, 0x00);
        GenomeAddress reserved = new GenomeAddress(0xF0, 0x00);
        GenomeProfile<DecodedGenome> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("reserved-relay-test", 1, new byte[32]);
            }

            @Override public boolean isDefinedAddress(GenomeAddress address) {
                return source.equals(address) || reserved.equals(address);
            }

            @Override public DirectContributionModel contributionModel(GenomeAddress address) {
                return StandardDirectContributionModel.defaultModel();
            }

            @Override public DecodedGenome mapPhenotype(DecodedGenome decodedGenome) {
                return decodedGenome;
            }
        };

        BitSequence direct = GeneCodecV1.encode(source, false, 127, 15, BitSequence.empty());
        BitSequence targetHeader = BitSequence.fromBits(Secded22.encode(reserved).toBitString());
        BitSequence relay = GeneCodecV1.encode(
                new GenomeAddress(0x08, 0x06), false, 127, 15, targetHeader);
        DiploidGenome genome = new DiploidGenome(
                1, List.of(new ChromosomePair(direct.concat(relay), BitSequence.empty())));

        DecodedGenome decoded = WonderfulGenomeEngine.create(EngineConfig.defaults())
                .decode(profile, genome)
                .decodedGenome();

        assertEquals(0.30, decoded.aggregate(source).score(), 1.0e-12);
        assertFalse(decoded.aggregates().containsKey(reserved));
    }
}
