package co.surumene.wgl.api;

import java.util.List;

public final class StandardGenomeSafetyPolicyV1 implements GenomeSafetyPolicy {
    @Override
    public boolean isSafe(List<Integer> lengths, List<Integer> baselines) {
        if (lengths.size() != baselines.size()) return false;
        long total = 0;
        long baselineTotal = 0;
        for (int i = 0; i < lengths.size(); i++) {
            int length = lengths.get(i);
            int baseline = baselines.get(i);
            if (baseline <= 0) return false;
            if (length < 0.25 * baseline || length > 4.0 * baseline) return false;
            total += length;
            baselineTotal += baseline;
        }
        return total >= 0.50 * baselineTotal && total <= 2.0 * baselineTotal;
    }
}
