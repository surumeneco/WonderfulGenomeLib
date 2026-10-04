package co.surumene.wgl.core;

import java.util.List;
import java.util.Objects;

final class NahrSupport {
    private NahrSupport() {}

    static boolean hasSupportingAlternative(HomologyCandidate candidate,
                                            List<HomologyCandidate> all,
                                            int maxAnchorGapBits) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(all, "all");
        if (maxAnchorGapBits < 1) {
            throw new IllegalArgumentException("maxAnchorGapBits must be >= 1");
        }
        for (HomologyCandidate other : all) {
            if (other == candidate) continue;
            int da = other.positionA() - candidate.positionA();
            int db = other.positionB() - candidate.positionB();
            if (da == 0 || db == 0 || Integer.signum(da) != Integer.signum(db)) continue;
            if (Math.abs((long) da) <= maxAnchorGapBits
                    && Math.abs((long) db) <= maxAnchorGapBits) {
                return true;
            }
        }
        return false;
    }
}
