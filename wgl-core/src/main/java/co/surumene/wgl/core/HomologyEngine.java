package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import java.util.*;

public final class HomologyEngine {
    public static final int ANCHOR_BITS=48;
    private final EngineConfig config;
    public HomologyEngine(EngineConfig config){this.config=Objects.requireNonNull(config);}

    public HomologyMap analyze(BitSequence a,BitSequence b){
        Objects.requireNonNull(a);Objects.requireNonNull(b);
        if(a.bitLength()<ANCHOR_BITS||b.bitLength()<ANCHOR_BITS)return new HomologyMap(List.of(),List.of(),List.of());
        ChunkIndex forward=new ChunkIndex(b,false); ChunkIndex reverse=new ChunkIndex(b,true);
        List<HomologyCandidate> candidates=new ArrayList<>();
        long aw = a.toLong(0, ANCHOR_BITS);
        for(int pa=0;pa<=a.bitLength()-ANCHOR_BITS;pa++){
            if (pa > 0) aw = roll48(aw, a.bitAt(pa + ANCHOR_BITS - 1));
            addCandidates(candidates,aw,pa,forward,HomologyOrientation.FORWARD);
            addCandidates(candidates,aw,pa,reverse,HomologyOrientation.REVERSE);
        }
        candidates.sort(Comparator.comparingInt(HomologyCandidate::positionA)
                .thenComparingInt(HomologyCandidate::positionB)
                .thenComparingInt(c->c.orientation()==HomologyOrientation.FORWARD?0:1)
                .thenComparingInt(HomologyCandidate::hammingDistance));
        List<HomologyCandidate> forwardCandidates=candidates.stream().filter(c->c.orientation()==HomologyOrientation.FORWARD).toList();
        List<HomologyBlock> blocks=buildBlocks(forwardCandidates);
        List<HomologyCandidate> ambiguous=findAmbiguous(candidates);
        return new HomologyMap(candidates,blocks,ambiguous);
    }

    static long roll48(long previous, boolean newBit) {
        return ((previous << 1) & 0xFFFFFFFFFFFFL) | (newBit ? 1L : 0L);
    }

    private static void addCandidates(List<HomologyCandidate> out,long aw,int pa,ChunkIndex index,HomologyOrientation orientation){
        // A <=2-bit mismatch in 48 bits must contain at least one identical
        // 16-bit slice. The three index buckets are therefore exhaustive.
        // Use generation stamps rather than allocating/sorting a TreeSet per A window.
        int generation = pa + 1;
        for (int chunk=0;chunk<3;chunk++) {
            int shift=32-16*chunk;
            int key=(int)((aw>>>shift)&0xFFFFL);
            IntPositions ps=index.positions[chunk].get(key);
            if (ps == null) continue;
            for (int i = 0; i < ps.size; i++) {
                int pb = ps.values[i];
                if (index.visited[pb] == generation) continue;
                index.visited[pb] = generation;
                int h=Long.bitCount((aw^index.window(pb))&0xFFFFFFFFFFFFL);
                if (h<=2) out.add(new HomologyCandidate(pa,pb,orientation,h));
            }
        }
    }

    private List<HomologyBlock> buildBlocks(List<HomologyCandidate> candidates) {
        if (candidates.size() < 2) return List.of();
        List<HomologyCandidate> remaining = new ArrayList<>(candidates);
        List<HomologyBlock> blocks = new ArrayList<>();
        while (remaining.size() >= 2) {
            List<HomologyCandidate> chain = bestValidChain(remaining);
            if (chain.isEmpty()) break;
            HomologyBlock block = new HomologyBlock(chain);
            blocks.add(block);
            remaining.removeIf(c -> (c.positionA() >= block.startA() && c.positionA() < block.endA())
                    || (c.positionB() >= block.startB() && c.positionB() < block.endB()));
        }
        blocks.sort(Comparator.comparingInt(HomologyBlock::startA).thenComparingInt(HomologyBlock::startB));
        return List.copyOf(blocks);
    }

    private List<HomologyCandidate> bestValidChain(List<HomologyCandidate> cands) {
        // A single ordered diagonal is the common case for homologous
        // sequences without insertions/deletions. Every valid predecessor
        // contributes the same qGap=1, so the immediately previous anchor
        // has the strictly greatest score within each gap-connected run.
        // Keep the full DP for cross-diagonal and repeated-anchor cases.
        if (isSingleOrderedDiagonal(cands)) {
            return bestSingleDiagonalChain(cands);
        }
        int n = cands.size();
        double[] score = new double[n];
        int[] count = new int[n];
        long[] gapError = new long[n];
        int[] prev = new int[n];
        int[] startA = new int[n];
        int[] startB = new int[n];
        Arrays.fill(prev, -1);
        // The exponential depends only on the integer displacement difference;
        // reuse identical values without changing strict floating-point semantics.
        GapWeightCache gapWeights = new GapWeightCache();
        for (int i = 0; i < n; i++) {
            HomologyCandidate cur = cands.get(i);
            score[i] = cur.anchorQuality();
            count[i] = 1;
            startA[i] = cur.positionA();
            startB[i] = cur.positionB();
            for (int j = i - 1; j >= 0; j--) {
                HomologyCandidate prior = cands.get(j);
                int da = cur.positionA() - prior.positionA();
                if (da > config.homology().maxAnchorGapBits()) break;
                int db = cur.positionB() - prior.positionB();
                if (da <= 0 || db <= 0 || db > config.homology().maxAnchorGapBits()) continue;
                // exp(-abs(da-db)/256) is in (0, 1], so even the best
                // possible transition from this predecessor cannot win if
                // its upper-bound score is already below the incumbent.
                // Equality must still be evaluated for original tie breaks.
                if (score[j] + cur.anchorQuality() < score[i]) continue;
                double qGap = gapWeights.get(Math.abs(da - db));
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
            int physical = Math.min(last.positionA() - startA[i] + ANCHOR_BITS,
                    last.positionB() - startB[i] + ANCHOR_BITS);
            if (physical < 256) continue;
            if (best < 0 || better(score[i], count[i], gapError[i], startA[i],
                    score[best], count[best], gapError[best], startA[best])) best = i;
        }
        return best < 0 ? List.of() : reconstruct(cands, prev, best);
    }

    private static boolean isSingleOrderedDiagonal(List<HomologyCandidate> cands) {
        if (cands.size() < 2) return false;
        HomologyCandidate first = cands.get(0);
        long diagonal = (long) first.positionA() - first.positionB();
        int previousA = first.positionA();
        for (int i = 1; i < cands.size(); i++) {
            HomologyCandidate next = cands.get(i);
            if (next.positionA() <= previousA
                    || (long) next.positionA() - next.positionB() != diagonal) {
                return false;
            }
            previousA = next.positionA();
        }
        return true;
    }

    private List<HomologyCandidate> bestSingleDiagonalChain(List<HomologyCandidate> cands) {
        int n = cands.size();
        double[] score = new double[n];
        int[] count = new int[n];
        int[] startA = new int[n];
        int[] startB = new int[n];
        int maxGap = config.homology().maxAnchorGapBits();
        for (int i = 0; i < n; i++) {
            HomologyCandidate current = cands.get(i);
            score[i] = current.anchorQuality();
            count[i] = 1;
            startA[i] = current.positionA();
            startB[i] = current.positionB();
            if (i > 0 && current.positionA() - cands.get(i - 1).positionA() <= maxGap) {
                // Exact original DP arithmetic: qGap=StrictMath.exp(0)=1.0.
                score[i] = score[i - 1] + current.anchorQuality() * 1.0;
                count[i] = count[i - 1] + 1;
                startA[i] = startA[i - 1];
                startB[i] = startB[i - 1];
            }
        }
        int best = -1;
        for (int i = 0; i < n; i++) {
            if (count[i] < 2) continue;
            HomologyCandidate last = cands.get(i);
            int physical = Math.min(last.positionA() - startA[i] + ANCHOR_BITS,
                    last.positionB() - startB[i] + ANCHOR_BITS);
            if (physical < 256) continue;
            if (best < 0 || better(score[i], count[i], 0, startA[i],
                    score[best], count[best], 0, startA[best])) best = i;
        }
        return best < 0 ? List.of()
                : List.copyOf(cands.subList(best - count[best] + 1, best + 1));
    }

    private static List<HomologyCandidate> reconstruct(List<HomologyCandidate> cands, int[] prev, int endpoint) {
        LinkedList<HomologyCandidate> chain = new LinkedList<>();
        for (int i = endpoint; i >= 0; i = prev[i]) {
            chain.addFirst(cands.get(i));
            if (prev[i] < 0) break;
        }
        return List.copyOf(chain);
    }

    private static boolean better(double s1, int c1, long e1, int start1,
                                  double s2, int c2, long e2, int start2) {
        int scoreCmp = Double.compare(s1, s2);
        if (scoreCmp != 0) return scoreCmp > 0;
        if (c1 != c2) return c1 > c2;
        if (e1 != e2) return e1 < e2;
        return start1 < start2;
    }

    private static List<HomologyCandidate> findAmbiguous(List<HomologyCandidate> candidates) {
        // Only distinct counterpart positions matter, not their frequency.
        // Numeric keys avoid allocating orientation Strings and HashSets for
        // every anchor coordinate. -1 indicates two distinct counterparts.
        Map<Long, Integer> byA = new HashMap<>();
        Map<Long, Integer> byB = new HashMap<>();
        for (HomologyCandidate c : candidates) {
            long aKey = ambiguityKey(c.positionA(), c.orientation());
            long bKey = ambiguityKey(c.positionB(), c.orientation());
            markDistinct(byA, aKey, c.positionB());
            markDistinct(byB, bKey, c.positionA());
        }
        List<HomologyCandidate> ambiguous = new ArrayList<>();
        for (HomologyCandidate c : candidates) {
            long aKey = ambiguityKey(c.positionA(), c.orientation());
            long bKey = ambiguityKey(c.positionB(), c.orientation());
            if (byA.get(aKey) == -1 || byB.get(bKey) == -1) {
                ambiguous.add(c);
            }
        }
        return List.copyOf(ambiguous);
    }

    private static long ambiguityKey(int position, HomologyOrientation orientation) {
        return ((long) position << 1) | (orientation == HomologyOrientation.FORWARD ? 0 : 1);
    }

    private static void markDistinct(Map<Long, Integer> values, long key, int other) {
        Integer first = values.putIfAbsent(key, other);
        if (first != null && first != -1 && first != other) values.put(key, -1);
    }

    /** Sparse postings backed by primitive positions: no Integer per 48-bit window. */
    private static final class IntPositions {
        int[] values = new int[4];
        int size;

        void add(int position) {
            if (size == values.length) values = Arrays.copyOf(values, size * 2);
            values[size++] = position;
        }
    }

    @SuppressWarnings("unchecked")
    private static final class ChunkIndex {
        final Map<Integer,IntPositions>[] positions=new Map[]{new HashMap<>(),new HashMap<>(),new HashMap<>()};
        final long[] windows;
        final int[] visited;
        ChunkIndex(BitSequence b,boolean reversed){
            int n=b.bitLength()-ANCHOR_BITS+1;windows=new long[n];visited=new int[n];
            long raw = b.toLong(0, ANCHOR_BITS);
            for(int p=0;p<n;p++){
                if (p > 0) raw = roll48(raw, b.bitAt(p + ANCHOR_BITS - 1));
                long w=reversed?reverse48(raw):raw;windows[p]=w;
                positions[0].computeIfAbsent((int)((w>>>32)&0xFFFF),k->new IntPositions()).add(p);
                positions[1].computeIfAbsent((int)((w>>>16)&0xFFFF),k->new IntPositions()).add(p);
                positions[2].computeIfAbsent((int)(w&0xFFFF),k->new IntPositions()).add(p);
            }
        }
        long window(int p){return windows[p];}
        private static long reverse48(long x){return Long.reverse(x) >>> 16;}
    }
}
