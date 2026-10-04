package co.surumene.wgl.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BitSequenceTest {
    @Test
    void canonicalizesUnusedTailBitsAndDefensivelyCopies() {
        byte[] source = {(byte) 0b1011_1111};
        BitSequence bits = BitSequence.ofPacked(source, 4);
        source[0] = 0;
        assertArrayEquals(new byte[]{(byte) 0b1011_0000}, bits.packedBits());
        byte[] copy = bits.packedBits();
        copy[0] = 0;
        assertArrayEquals(new byte[]{(byte) 0b1011_0000}, bits.packedBits());
    }

    @Test
    void supportsOddLengthSliceConcatAndReverse() {
        BitSequence bits = BitSequence.fromBits("10110");
        assertEquals("011", bits.slice(1, 4).toBitString());
        assertEquals("101101", bits.concat(BitSequence.fromBits("1")).toBitString());
        assertEquals("01101", bits.reverse().toBitString());
    }
}
