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
        for(int pa=0;pa<=a.bitLength()-ANCHOR_BITS;pa++){
            long aw=a.toLong(pa,ANCHOR_BITS);
            addCandidates(candidates,aw,pa,b,forward,HomologyOrientation.FORWARD);
            addCandidates(candidates,aw,pa,b,reverse,HomologyOrientation.REVERSE);
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

    private static void addCandidates(List<HomologyCandidate> out,long aw,int pa,BitSequence b,ChunkIndex index,HomologyOrientation orientation){
        TreeSet<Integer> positions=new TreeSet<>();
        for(int chunk=0;chunk<3;chunk++){
            int shift=32-16*chunk; int key=(int)((aw>>>shift)&0xFFFFL);
            List<Integer> ps=index.positions[chunk].get(key); if(ps!=null)positions.addAll(ps);
        }
        for(int pb:positions){
            long bw=index.window(pb); int h=Long.bitCount((aw^bw)&0xFFFFFFFFFFFFL);
            if(h<=2)out.add(new HomologyCandidate(pa,pb,orientation,h));
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
        int n = cands.size();
        double[] score = new double[n];
        int[] count = new int[n];
        long[] gapError = new long[n];
        int[] prev = new int[n];
        int[] startA = new int[n];
        int[] startB = new int[n];
        Arrays.fill(prev, -1);
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
                double qGap = StrictMath.exp(-StrictMath.abs(da - db) / 256.0);
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
        Map<String, Set<Integer>> byA = new HashMap<>();
        Map<String, Set<Integer>> byB = new HashMap<>();
        for (HomologyCandidate c : candidates) {
            String aKey = c.positionA() + ":" + c.orientation();
            String bKey = c.positionB() + ":" + c.orientation();
            byA.computeIfAbsent(aKey, k -> new HashSet<>()).add(c.positionB());
            byB.computeIfAbsent(bKey, k -> new HashSet<>()).add(c.positionA());
        }
        return candidates.stream().filter(c ->
                byA.get(c.positionA() + ":" + c.orientation()).size() > 1
                        || byB.get(c.positionB() + ":" + c.orientation()).size() > 1).toList();
    }

    @SuppressWarnings("unchecked")
    private static final class ChunkIndex {
        final Map<Integer,List<Integer>>[] positions=new Map[]{new HashMap<>(),new HashMap<>(),new HashMap<>()};
        final long[] windows;
        ChunkIndex(BitSequence b,boolean reversed){
            int n=b.bitLength()-ANCHOR_BITS+1;windows=new long[n];
            for(int p=0;p<n;p++){
                long raw=b.toLong(p,ANCHOR_BITS); long w=reversed?reverse48(raw):raw;windows[p]=w;
                positions[0].computeIfAbsent((int)((w>>>32)&0xFFFF),k->new ArrayList<>()).add(p);
                positions[1].computeIfAbsent((int)((w>>>16)&0xFFFF),k->new ArrayList<>()).add(p);
                positions[2].computeIfAbsent((int)(w&0xFFFF),k->new ArrayList<>()).add(p);
            }
        }
        long window(int p){return windows[p];}
        private static long reverse48(long x){long r=0;for(int i=0;i<48;i++){r=(r<<1)|(x&1L);x>>>=1;}return r;}
    }
}
