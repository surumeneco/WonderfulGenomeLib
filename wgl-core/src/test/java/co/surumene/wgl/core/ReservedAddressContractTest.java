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
}
