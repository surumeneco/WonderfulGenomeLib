package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

final class TrackedSequenceOriginOwnershipTest {
    @Test
    void callerOwnedOriginArraysStayDefensivelyCopied() {
        BitSequence bits = BitSequence.fromBits("0101");
        TrackedSequence.Origin original = new TrackedSequence.Origin(1, 10, 1.3, 0.8);
        TrackedSequence.Origin[] supplied = {original, original, original, original};
        TrackedSequence sequence = new TrackedSequence(bits, supplied);
        supplied[0] = TrackedSequence.Origin.fresh();
        assertEquals(original, sequence.originAt(0));

        TrackedSequence.Origin[] returned = sequence.origins();
        returned[1] = TrackedSequence.Origin.fresh();
        assertEquals(original, sequence.originAt(1));
    }

    @Test
    void internalTransformsPreserveExactBitAndProvenanceResults() {
        BitSequence bits = BitSequence.fromBits("010110");
        TrackedSequence.Origin[] origins = new TrackedSequence.Origin[bits.bitLength()];
        for (int i = 0; i < origins.length; i++) {
            origins[i] = new TrackedSequence.Origin(2, 50 + i, 1.0 + i * 0.1, 1.0);
        }
        TrackedSequence source = new TrackedSequence(bits, origins);
        TrackedSequence flipped = source.flip(2);
        assertEquals(bits.flip(2), flipped.bits());
        assertArrayEquals(origins, flipped.origins());
        TrackedSequence sliced = source.slice(1, 5);
        assertEquals(bits.slice(1, 5), sliced.bits());
        assertArrayEquals(Arrays.copyOfRange(origins, 1, 5), sliced.origins());
        TrackedSequence joined = sliced.concat(source.slice(5, 6));
        assertEquals(bits.slice(1, 6), joined.bits());
        assertArrayEquals(Arrays.copyOfRange(origins, 1, 6), joined.origins());
        TrackedSequence reversed = source.reverse();
        for (int i = 0; i < origins.length; i++) {
            assertEquals(origins[origins.length - 1 - i], reversed.originAt(i));
        }
        assertArrayEquals(origins, source.origins(), "transforms must not alter source provenance");
    }
}
