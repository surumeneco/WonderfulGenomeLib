package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import co.surumene.wgl.api.GeneSequenceCodec;
import co.surumene.wgl.api.GenomeAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeneSequenceCodecTest {
    @Test
    void publicCodecUsesCanonicalFormatV1Encoding() {
        GeneSequenceCodec codec =
                WonderfulGenomeEngine.create(EngineConfig.defaults()).geneSequenceCodec();
        GenomeAddress address = new GenomeAddress(0x02, 0x04);
        BitSequence extension = BitSequence.fromBits("1010010111000011");

        assertEquals(
                GeneCodecV1.encode(address, true, 77, 11, extension),
                codec.encodeDirectGene(address, true, 77, 11, extension));
        assertEquals(
                GeneCodecV1.encodeRawEffect(address, 165, 9, extension),
                codec.encodeRawGene(address, 165, 9, extension));
        assertEquals(
                Secded22.encode(address).toBitString(),
                codec.encodeAddressHeader(address).toBitString());
    }
}
