package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The 48-bit optimized scan must reproduce every forward/reverse V1 hit. */
class MarkerWindowParityTest {
    @Test
    void exactParityAcrossOffsetsOrientationsAndThresholds() {
        Random random = new Random(0x4D41524B45524C31L);
        for (int length : new int[] {0, 1, 47, 48, 49, 55, 63, 64, 65, 127, 256, 513}) {
            for (int sample = 0; sample < 15; sample++) {
                BitSequence sequence = bits(random, length);
                BitSequence anchor = bits(random, 48);
                assertEquals(reference(sequence, anchor), MarkerEngine.findHits(sequence, anchor),
                        "length=" + length + ", sample=" + sample);
            }
        }
    }

    @Test
    void denseAndPalindromicAnchorsKeepBothOrientationsAndOrder() {
        BitSequence zero = BitSequence.fromBits("0".repeat(192));
        BitSequence anchor = BitSequence.fromBits("0".repeat(48));
        assertEquals(reference(zero, anchor), MarkerEngine.findHits(zero, anchor));
        BitSequence oneDifference = BitSequence.fromBits("0".repeat(23) + "1" + "0".repeat(24));
        BitSequence sample = BitSequence.fromBits("0".repeat(9) + oneDifference + "0".repeat(51));
        assertEquals(reference(sample, anchor), MarkerEngine.findHits(sample, anchor));
    }

    private static List<MarkerEngine.Hit> reference(BitSequence sequence, BitSequence anchor) {
        List<MarkerEngine.Hit> result = new ArrayList<>();
        for (int position = 0; position <= sequence.bitLength() - 48; position++) {
            BitSequence window = sequence.slice(position, position + 48);
            int forward = window.hammingDistance(anchor);
            if (forward <= 2) result.add(new MarkerEngine.Hit(position, false, forward));
            int reverse = window.reverse().hammingDistance(anchor);
            if (reverse <= 2) result.add(new MarkerEngine.Hit(position, true, reverse));
        }
        return result;
    }

    private static BitSequence bits(Random random, int length) {
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) value.append(random.nextBoolean() ? '1' : '0');
        return BitSequence.fromBits(value.toString());
    }
}
