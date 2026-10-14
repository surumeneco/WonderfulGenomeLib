package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class HomologyDpDensityProbeTest {
    private static final int GAP = 1024;

    @Test
    void candidateDensityAndPredecessorWorkAcrossInputShapes() throws Exception {
        testInput("patterned-600", TestSequences.patterned(600, 17));
        testInput("repeat-zero-72", BitSequence.fromBits("0".repeat(72)));
        testInput("repeat-zero-96", BitSequence.fromBits("0".repeat(96)));
        ArrayList<HomologyCandidate> many = new ArrayList<>();
        for (int a = 0; a < 200; a += 2)
            for (int k = 0; k < 8; k++)
                many.add(new HomologyCandidate(a, a + k * 13,
                    HomologyOrientation.FORWARD, (a + k) % 3));
        many.sort(Comparator.comparingInt(HomologyCandidate::positionA)
                .thenComparingInt(HomologyCandidate::positionB)
                .thenComparingInt(HomologyCandidate::hammingDistance));
        probe("multi-diagonal-synthetic", many);
    }

    private static void testInput(String name, BitSequence s) throws Exception {
        HomologyMap map = new HomologyEngine(EngineConfig.defaults()).analyze(s, s);
        System.out.printf(Locale.ROOT,
                "WGL_DP_INPUT scenario=%s bits=%d candidates=%d ambiguous=%d blocks=%d%n",
                name, s.bitLength(), map.candidates().size(),
                map.ambiguousCandidates().size(), map.blocks().size());
        probe(name, map.candidates().stream()
                .filter(c -> c.orientation() == HomologyOrientation.FORWARD).toList());
    }

    private static void probe(String name, List<HomologyCandidate> c) throws Exception {
        long scan = 0, valid = 0, sameOrNegativeA = 0, invalidB = 0, pruned = 0, scored = 0;
        double[] score = new double[c.size()];
        for (int i = 0; i < c.size(); i++) {
            HomologyCandidate cur = c.get(i);
            score[i] = cur.anchorQuality();
            for (int j = i - 1; j >= 0; j--) {
                HomologyCandidate prior = c.get(j);
                int da = cur.positionA() - prior.positionA();
                if (da > GAP) break;
                scan++;
                int db = cur.positionB() - prior.positionB();
                if (da <= 0) { sameOrNegativeA++; continue; }
                if (db <= 0 || db > GAP) { invalidB++; continue; }
                valid++;
                if (score[j] + cur.anchorQuality() < score[i]) { pruned++; continue; }
                scored++;
                double q = StrictMath.exp(-Math.abs(da - db) / 256.0);
                double candidateScore = score[j] + cur.anchorQuality() * q;
                if (candidateScore > score[i]) score[i] = candidateScore;
            }
        }
        System.out.printf(Locale.ROOT,
                "WGL_DP_WORK scenario=%s n=%d scanned=%d valid=%d invalid_a=%d invalid_b=%d pruned=%d evaluated=%d%n",
                name, c.size(), scan, valid, sameOrNegativeA, invalidB, pruned, scored);
        // The input and read-only traversal above must not alter production behavior.
        Method method = HomologyEngine.class.getDeclaredMethod("bestValidChain", List.class);
        method.setAccessible(true);
        assertNotNull(method.invoke(new HomologyEngine(EngineConfig.defaults()), c));
    }
}
