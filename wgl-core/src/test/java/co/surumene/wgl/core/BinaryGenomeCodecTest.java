package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import co.surumene.wgl.api.ChromosomePair;
import co.surumene.wgl.api.DiploidGenome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BinaryGenomeCodecTest {
    @Test
    void roundTripsOddLengthGenomeCanonically() {
        DiploidGenome genome = new DiploidGenome(1, List.of(
                new ChromosomePair(BitSequence.fromBits("10101"), BitSequence.fromBits("001")),
                new ChromosomePair(BitSequence.empty(), BitSequence.fromBits("1"))));
        BinaryGenomeCodecV1 codec = new BinaryGenomeCodecV1();
        byte[] encoded = codec.encode(genome);
        assertEquals(genome, codec.decode(encoded));
    }

    @Test
    void rejectsTrailingBytesAndNonCanonicalTailBits() {
        BinaryGenomeCodecV1 codec = new BinaryGenomeCodecV1();
        DiploidGenome genome = new DiploidGenome(1, List.of(
                new ChromosomePair(BitSequence.fromBits("1"), BitSequence.fromBits("0"))));
        byte[] encoded = codec.encode(genome);
        byte[] trailing = java.util.Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(GenomeCodecException.class, () -> codec.decode(trailing));

        byte[] malformed = encoded.clone();
        malformed[13] |= 0x01;
        assertThrows(GenomeCodecException.class, () -> codec.decode(malformed));
    }
    @Test
    void v1CodecRejectsEncodingUnsupportedGenomeFormat() {
        BinaryGenomeCodecV1 codec = new BinaryGenomeCodecV1();
        DiploidGenome v2 = new DiploidGenome(2, List.of(
                new ChromosomePair(BitSequence.fromBits("1"), BitSequence.fromBits("0"))));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(v2));
    }

}
