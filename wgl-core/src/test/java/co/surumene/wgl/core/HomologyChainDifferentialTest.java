package co.surumene.wgl.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Independent quadratic oracle copied from develop before the chain-index optimization.
 * Keep this deliberately simple: it must not reuse any optimized search/index helpers.
 */
final class HomologyChainDifferentialTest {
    private static final Comparator<HomologyCandidate> ORDER =
            Comparator.comparingInt(HomologyCandidate::positionA)
                    .thenComparingInt(HomologyCandidate::positionB)
                    .thenComparingInt(c -> c.orientation() == HomologyOrientation.FORWARD ? 0 : 1)
                    .thenComparingInt(HomologyCandidate::hammingDistance);

    @Test
    void identicalChainsForRandomizedDenseAndSparseCandidateSets() throws Exception {
        HomologyEngine engine = new HomologyEngine(EngineConfig.defaults());
        Random random = new Random(0x1251_AA39L);
        for (int scenario = 0; scenario < 200; scenario++) {
            int n = 30 + random.nextInt(145);
            ArrayList<HomologyCandidate> candidates = new ArrayList<>(n);
            int positionA = 0;
            for (int k = 0; k < n; k++) {
                if (scenario % 4 == 0) positionA += random.nextInt(2);
                else if (scenario % 4 == 1) positionA += random.nextInt(8);
                else if (scenario % 4 == 2) positionA += random.nextInt(120);
                else positionA += random.nextInt(1050);
                int positionB = scenario % 3 == 0 ? random.nextInt(80) : random.nextInt(2500);
                candidates.add(new HomologyCandidate(positionA, positionB,
                        HomologyOrientation.FORWARD, random.nextInt(3)));
            }
            candidates.sort(ORDER);
            assertEquals(referenceChain(candidates, 1024), invokeChain(engine, candidates),
                    "differential result for scenario " + scenario);
        }
    }

    @Test
    void exactResultForHeavyDuplicatesAndGapBoundaries() throws Exception {
        HomologyEngine engine = new HomologyEngine(EngineConfig.defaults());
        ArrayList<HomologyCandidate> candidates = new ArrayList<>();
        for (int pa : new int[]{0, 20, 252, 256, 512, 1024, 1025, 1290, 2048}) {
            for (int pb = 0; pb < 1300; pb += 31) {
                for (int distance = 0; distance <= 2; distance++) {
                    candidates.add(new HomologyCandidate(pa, pb,
                            HomologyOrientation.FORWARD, distance));
                }
            }
        }
        candidates.sort(ORDER);
        assertEquals(referenceChain(candidates, 1024), invokeChain(engine, candidates));
    }

    @Test
    void singleDiagonalReferenceParityAcrossLargeGapsAndVariableAnchorQuality() throws Exception {
        HomologyEngine engine = new HomologyEngine(EngineConfig.defaults());
        Random random = new Random(0x4000_77ABL);
        for (int scenario = 0; scenario < 16; scenario++) {
            ArrayList<HomologyCandidate> candidates = new ArrayList<>();
            int a = 0;
            for (int i = 0; i < 270; i++) {
                a += scenario % 2 == 0 && i % 47 == 0 ? 1025 + random.nextInt(128)
                        : 1 + random.nextInt(scenario % 3 == 0 ? 50 : 5);
                candidates.add(new HomologyCandidate(a, a + 17,
                        HomologyOrientation.FORWARD, random.nextInt(3)));
            }
            assertEquals(referenceChain(candidates, 1024), invokeChain(engine, candidates),
                    "single diagonal scenario " + scenario);
        }
    }


    @Test
    void reportsSameJvmReferenceComparisonOnControlledCandidateSets() throws Exception {
        List<HomologyCandidate> single = new ArrayList<>();
        for (int i = 0; i < 1800; i++) {
            int position = i * 2;
            single.add(new HomologyCandidate(position, position + 23,
                    HomologyOrientation.FORWARD, i % 3));
        }
        List<HomologyCandidate> clustered = new ArrayList<>();
        for (int group = 0; group < 65; group++) {
            for (int k = 0; k < 24; k++) {
                clustered.add(new HomologyCandidate(group * 25, group * 18 + k * 230,
                        HomologyOrientation.FORWARD, k % 3));
            }
        }
        clustered.sort(ORDER);
        benchmarkScenario("single-diagonal", single);
        benchmarkScenario("clustered-multi-diagonal", clustered);
    }

    private static void benchmarkScenario(String name, List<HomologyCandidate> cands)
            throws Exception {
        HomologyEngine engine = new HomologyEngine(EngineConfig.defaults());
        List<HomologyCandidate> expected = referenceChain(cands, 1024);
        assertEquals(expected, invokeChain(engine, cands), name);
        for (int warmup = 0; warmup < 3; warmup++) {
            referenceChain(cands, 1024);
            invokeChain(engine, cands);
        }
        long referenceNs = 0;
        long optimizedNs = 0;
        for (int iteration = 0; iteration < 5; iteration++) {
            if ((iteration & 1) == 0) {
                long start = System.nanoTime();
                referenceChain(cands, 1024);
                referenceNs += System.nanoTime() - start;
                start = System.nanoTime();
                invokeChain(engine, cands);
                optimizedNs += System.nanoTime() - start;
            } else {
                long start = System.nanoTime();
                invokeChain(engine, cands);
                optimizedNs += System.nanoTime() - start;
                start = System.nanoTime();
                referenceChain(cands, 1024);
                referenceNs += System.nanoTime() - start;
            }
        }
        System.out.printf(java.util.Locale.ROOT,
                "HOMOLOGY_CHAIN_BENCH scenario=%s n=%d original_ms=%.3f optimized_ms=%.3f ratio=%.2f%n",
                name, cands.size(), referenceNs / 1e6 / 5, optimizedNs / 1e6 / 5,
                (double) referenceNs / Math.max(optimizedNs, 1L));
    }

    @SuppressWarnings("unchecked")
    private static List<HomologyCandidate> invokeChain(HomologyEngine engine,
            List<HomologyCandidate> input) throws Exception {
        Method method = HomologyEngine.class.getDeclaredMethod("bestValidChain", List.class);
        method.setAccessible(true);
        return (List<HomologyCandidate>) method.invoke(engine, input);
    }

    private static List<HomologyCandidate> referenceChain(
            List<HomologyCandidate> cands, int maxAnchorGapBits) {
        int n = cands.size();
        double[] score = new double[n];
        int[] count = new int[n];
        long[] gapError = new long[n];
        int[] prev = new int[n];
        int[] startA = new int[n];
        int[] startB = new int[n];
        Arrays.fill(prev, -1);
        Map<Integer, Double> gapWeights = new HashMap<>();
        for (int i = 0; i < n; i++) {
            HomologyCandidate cur = cands.get(i);
            score[i] = cur.anchorQuality();
            count[i] = 1;
            startA[i] = cur.positionA();
            startB[i] = cur.positionB();
            for (int j = i - 1; j >= 0; j--) {
                HomologyCandidate prior = cands.get(j);
                int da = cur.positionA() - prior.positionA();
                if (da > maxAnchorGapBits) break;
                int db = cur.positionB() - prior.positionB();
                if (da <= 0 || db <= 0 || db > maxAnchorGapBits) continue;
                double qGap = gapWeights.computeIfAbsent(Math.abs(da - db),
                        displacement -> StrictMath.exp(-displacement / 256.0));
                double candidateScore = score[j] + cur.anchorQuality() * qGap;
                int candidateCount = count[j] + 1;
                long candidateError = gapError[j] + StrictMath.abs(da - db);
                if (better(candidateScore, candidateCount, candidateError, startA[j],
                        score[i], count[i], gapError[i], startA[i])) {
                    score[i] = candidateScore;
                    count[i] = candidateCount;
                    gapError[i] = candidateError;
                    prev[i] = j;
                    startA[i] = startA[j];
                    startB[i] = startB[j];
                }
            }
        }
        int best = -1;
        for (int i = 0; i < n; i++) {
            if (count[i] < 2) continue;
            HomologyCandidate last = cands.get(i);
            int physical = Math.min(last.positionA() - startA[i] + HomologyEngine.ANCHOR_BITS,
                    last.positionB() - startB[i] + HomologyEngine.ANCHOR_BITS);
            if (physical < 256) continue;
            if (best < 0 || better(score[i], count[i], gapError[i], startA[i],
                    score[best], count[best], gapError[best], startA[best])) best = i;
        }
        if (best < 0) return List.of();
        ArrayList<HomologyCandidate> reverse = new ArrayList<>();
        for (int i = best; i >= 0; i = prev[i]) {
            reverse.add(cands.get(i));
            if (prev[i] < 0) break;
        }
        java.util.Collections.reverse(reverse);
        return List.copyOf(reverse);
    }

    private static boolean better(double s1, int c1, long e1, int start1,
                                  double s2, int c2, long e2, int start2) {
        int scoreCmp = Double.compare(s1, s2);
        if (scoreCmp != 0) return scoreCmp > 0;
        if (c1 != c2) return c1 > c2;
        if (e1 != e2) return e1 < e2;
        return start1 < start2;
    }

    @Test
    void denseRepeatsAndEqualScoreTiesPreserveExactQuadraticChain() throws Exception {
        HomologyEngine engine = new HomologyEngine(EngineConfig.defaults());
        ArrayList<HomologyCandidate> candidates = new ArrayList<>();
        for (int a = 0; a < 95; a++) {
            for (int b = 0; b < 14; b++) {
                candidates.add(new HomologyCandidate(
                        a * 11, ((a * 7 + b * 37) % 101) * 11,
                        HomologyOrientation.FORWARD, (a + b) % 3));
            }
        }
        candidates.sort(ORDER);
        assertEquals(referenceChain(candidates, 1024), invokeChain(engine, candidates),
                "dense multi-diagonal cases must preserve original tie ordering");
        ArrayList<HomologyCandidate> tied = new ArrayList<>();
        for (int a = 0; a < 420; a++) {
            tied.add(new HomologyCandidate(a * 3, a * 3 + 11, HomologyOrientation.FORWARD, 0));
            if (a % 17 == 0) tied.add(new HomologyCandidate(a * 3, a * 3 + 12,
                    HomologyOrientation.FORWARD, 0));
        }
        tied.sort(ORDER);
        assertEquals(referenceChain(tied, 1024), invokeChain(engine, tied));
    }
}
