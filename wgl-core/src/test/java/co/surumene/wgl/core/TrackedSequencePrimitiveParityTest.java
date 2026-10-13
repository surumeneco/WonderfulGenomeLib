package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden provenance semantics independent of the backing representation.
 * A bit can be fresh or from any original lane; orientation/copy operations
 * must retain all four origin properties bit-for-bit.
 */
final class TrackedSequencePrimitiveParityTest {
    private static TrackedSequence.Origin[] source(int size) {
        TrackedSequence.Origin[] a = new TrackedSequence.Origin[size];
        for (int i = 0; i < size; i++) {
            a[i] = i % 7 == 0
                    ? TrackedSequence.Origin.fresh()
                    : new TrackedSequence.Origin(i % 9, i * 17, 0.25 + i * 0.0125,
                            0.5 + i * 0.03125);
        }
        return a;
    }
    private static TrackedSequence.Origin[] join(TrackedSequence.Origin[] first,
            TrackedSequence.Origin[] second) {
        TrackedSequence.Origin[] a = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, a, first.length, second.length);
        return a;
    }
    private static TrackedSequence.Origin[] cut(
            TrackedSequence.Origin[] input, int from, int end) {
        return Arrays.copyOfRange(input, from, end);
    }
    private static TrackedSequence.Origin[] backwards(TrackedSequence.Origin[] input) {
        TrackedSequence.Origin[] copy = input.clone();
        for (int i = 0; i < copy.length / 2; i++) {
            TrackedSequence.Origin swap = copy[i];
            copy[i] = copy[copy.length - 1 - i];
            copy[copy.length - 1 - i] = swap;
        }
        return copy;
    }
    private static void check(TrackedSequence actual, BitSequence bits,
                              TrackedSequence.Origin[] origin) {
        assertEquals(bits, actual.bits());
        assertEquals(origin.length, actual.bitLength());
        assertArrayEquals(origin, actual.origins());
        for (int i = 0; i < origin.length; i++) {
            assertEquals(origin[i], actual.originAt(i), "origin bit " + i);
        }
        for (int boundary = 0; boundary <= origin.length; boundary++) {
            double expected;
            if (origin.length == 0) expected = 1.0;
            else if (boundary == 0) expected = origin[0].structuralMultiplier();
            else if (boundary == origin.length)
                expected = origin[origin.length - 1].structuralMultiplier();
            else expected = StrictMath.sqrt(origin[boundary - 1].structuralMultiplier()
                        * origin[boundary].structuralMultiplier());
            assertEquals(expected, actual.structuralBoundaryWeight(boundary), 0.0,
                    "boundary " + boundary);
        }
    }

    @Test void editedAndRearrangedBitsRetainAllOriginalProvenance() {
        for (int length : new int[]{0, 1, 7, 19, 65, 128}) {
            String bitString = "1011010010010110".repeat((length + 15) / 16).substring(0, length);
            BitSequence bits = BitSequence.fromBits(bitString);
            TrackedSequence.Origin[] src = source(length);
            TrackedSequence tracked = new TrackedSequence(bits, src);
            if (length > 2) src[2] = TrackedSequence.Origin.fresh();
            TrackedSequence.Origin[] original = source(length);
            check(tracked, bits, original);
            if (length > 0) check(tracked.flip(length / 2), bits.flip(length / 2), original);
            check(tracked.reverse(), bits.reverse(), backwards(original));
            int cut = length / 2;
            check(tracked.slice(0, cut), bits.slice(0, cut), cut(original, 0, cut));
            check(tracked.slice(cut, length), bits.slice(cut, length),
                    cut(original, cut, length));
            BitSequence insertBits = BitSequence.fromBits("01011");
            TrackedSequence insert = TrackedSequence.fresh(insertBits);
            TrackedSequence.Origin[] fresh = new TrackedSequence.Origin[5];
            Arrays.fill(fresh, TrackedSequence.Origin.fresh());
            check(tracked.insert(cut, insert), bits.insert(cut, insertBits),
                    join(join(cut(original, 0, cut), fresh), cut(original, cut, length)));
            check(tracked.replace(0, cut, insert),
                    bits.slice(0, 0).concat(insertBits).concat(bits.slice(cut, length)),
                    join(fresh, cut(original, cut, length)));
            check(tracked.delete(0, cut), bits.slice(cut, length),
                    cut(original, cut, length));
            check(tracked.concat(insert), bits.concat(insertBits),
                    join(original, fresh));
        }
    }

    @Test void returningArrayMustNotAllowMutationOfExistingSequence() {
        TrackedSequence.Origin[] input = source(4);
        TrackedSequence origin = new TrackedSequence(BitSequence.fromBits("1001"), input);
        TrackedSequence.Origin[] snapshot = origin.origins();
        snapshot[1] = TrackedSequence.Origin.fresh();
        input[0] = TrackedSequence.Origin.fresh();
        assertEquals(source(4)[0], origin.originAt(0));
        assertEquals(source(4)[1], origin.originAt(1));
    }
}
