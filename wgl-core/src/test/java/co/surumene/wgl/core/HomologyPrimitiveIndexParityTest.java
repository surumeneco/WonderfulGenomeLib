package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterize chunk-index output with an index-independent exhaustive oracle.
 * Run this unchanged before and after swapping the boxed posting lists.
 */
final class HomologyPrimitiveIndexParityTest {
    private static final Comparator<HomologyCandidate> ORDER =
            Comparator.comparingInt(HomologyCandidate::positionA)
                .thenComparingInt(HomologyCandidate::positionB)
                .thenComparingInt(c -> c.orientation() == HomologyOrientation.FORWARD ? 0 : 1)
                .thenComparingInt(HomologyCandidate::hammingDistance);

    @Test void retainsAllCandidatesOnRandomAndRepeatedInputs() {
        HomologyEngine engine = new HomologyEngine(EngineConfig.defaults());
        Random random = new Random(0x7ec_014L);
        for (int trial = 0; trial < 24; trial++) {
            int la = 48 + random.nextInt(122);
            int lb = 48 + random.nextInt(122);
            BitSequence a = randomBits(la, random, trial % 6 == 0);
            BitSequence b = randomBits(lb, random, trial % 5 == 0);
            assertEquals(exhaustive(a, b), engine.analyze(a, b).candidates(),
                    "trial " + trial + " / " + la + "x" + lb);
        }
        BitSequence repeated = BitSequence.fromBits("01".repeat(35));
        assertEquals(exhaustive(repeated, repeated), engine.analyze(repeated, repeated).candidates());
        assertEquals(exhaustive(repeated, repeated.reverse()),
                engine.analyze(repeated, repeated.reverse()).candidates());
    }

    private static BitSequence randomBits(int n, Random random, boolean repeated) {
        StringBuilder builder = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            builder.append(repeated ? ((i / 9) & 1) : random.nextBoolean() ? '1' : '0');
        }
        return BitSequence.fromBits(builder.toString());
    }

    private static List<HomologyCandidate> exhaustive(BitSequence a, BitSequence b) {
        List<HomologyCandidate> out = new ArrayList<>();
        for (int pa = 0; pa + 48 <= a.bitLength(); pa++) {
            long aa = a.toLong(pa, 48);
            for (int pb = 0; pb + 48 <= b.bitLength(); pb++) {
                long bb = b.toLong(pb, 48);
                int forward = Long.bitCount(aa ^ bb);
                if (forward <= 2) {
                    out.add(new HomologyCandidate(pa, pb, HomologyOrientation.FORWARD, forward));
                }
                int reverse = Long.bitCount(aa ^ (Long.reverse(bb) >>> 16));
                if (reverse <= 2) {
                    out.add(new HomologyCandidate(pa, pb, HomologyOrientation.REVERSE, reverse));
                }
            }
        }
        out.sort(ORDER);
        return List.copyOf(out);
    }
}
