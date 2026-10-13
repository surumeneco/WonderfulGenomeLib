package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HomologyRollingWindowsParityTest {
    private static final int WINDOW = HomologyEngine.ANCHOR_BITS;
    private static final Comparator<HomologyCandidate> ORDER =
            Comparator.comparingInt(HomologyCandidate::positionA)
                    .thenComparingInt(HomologyCandidate::positionB)
                    .thenComparingInt(c -> c.orientation() == HomologyOrientation.FORWARD ? 0 : 1)
                    .thenComparingInt(HomologyCandidate::hammingDistance);

    @Test
    void slidingWindowMatchesIndependentFullBitReadingForEveryOffset() {
        List<BitSequence> inputs = List.of(
                BitSequence.fromBits("0".repeat(48)),
                BitSequence.fromBits("1".repeat(49)),
                TestSequences.patterned(257, 0x1569L),
                TestSequences.patterned(1025, 0x6a81L));
        for (BitSequence bits : inputs) {
            long rolling = bits.toLong(0, WINDOW);
            assertEquals(bits.toLong(0, WINDOW), rolling);
            for (int position = 1; position + WINDOW <= bits.bitLength(); position++) {
                rolling = HomologyEngine.roll48(rolling,
                        bits.bitAt(position + WINDOW - 1));
                assertEquals(bits.toLong(position, WINDOW), rolling,
                        "sliding bits window at " + position);
            }
        }
    }

    @Test
    void exhaustivePhysicalCandidateSetIsIdenticalIncludingReverseOrientation() {
        HomologyEngine engine = new HomologyEngine(EngineConfig.defaults());
        BitSequence a = TestSequences.patterned(512, 0x5a6aL);
        BitSequence b = a.flip(103);
        BitSequence shifted = a.insert(180, a.slice(41, 103));
        for (BitSequence right : List.of(a, b, a.reverse(), shifted)) {
            assertEquals(exhaustiveCandidates(a, right), engine.analyze(a, right).candidates(),
                    "candidate list must retain exact scores, order and orientation");
        }
    }

    private static List<HomologyCandidate> exhaustiveCandidates(BitSequence a, BitSequence b) {
        List<HomologyCandidate> found = new ArrayList<>();
        long mask = (1L << WINDOW) - 1L;
        for (int pa = 0; pa + WINDOW <= a.bitLength(); pa++) {
            long left = a.toLong(pa, WINDOW);
            for (int pb = 0; pb + WINDOW <= b.bitLength(); pb++) {
                long right = b.toLong(pb, WINDOW);
                int hamming = Long.bitCount((left ^ right) & mask);
                if (hamming <= 2) found.add(new HomologyCandidate(
                        pa, pb, HomologyOrientation.FORWARD, hamming));
                int reverseHamming = Long.bitCount((left ^ (Long.reverse(right) >>> 16)) & mask);
                if (reverseHamming <= 2) found.add(new HomologyCandidate(
                        pa, pb, HomologyOrientation.REVERSE, reverseHamming));
            }
        }
        found.sort(ORDER);
        return List.copyOf(found);
    }
}
