package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GenomeSequenceCodecTest {
    private final GenomeSequenceCodecV1 codec = new GenomeSequenceCodecV1();

    @Test
    void dnaAndHexViewsPreserveResidualBits() {
        BitSequence odd = BitSequence.fromBits("00011011").concat(BitSequence.fromBits("1"));
        var dna = codec.encodeDna(odd);
        assertEquals("TCGA", dna.text());
        assertEquals("1", dna.residualBits());
        assertEquals(BitSequence.fromBits("00011011"), codec.decodeDna(dna.text()));

        BitSequence hexOdd = BitSequence.fromBits("1010110011");
        var hex = codec.encodeHex(hexOdd);
        assertEquals("AC", hex.text());
        assertEquals("11", hex.residualBits());
        assertEquals(BitSequence.fromBits("10101100"), codec.decodeHex(hex.text()));
    }

    @Test
    void dnaMappingIsExactlyTcga() {
        assertEquals("TCGA", codec.encodeDna(BitSequence.fromBits("00011011")).text());
        assertEquals("00011011", codec.decodeDna("TCGA").toBitString());
    }
}
