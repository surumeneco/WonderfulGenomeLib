package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import java.util.Objects;

/**
 * One homologous chromosome pair belonging to a single meiosis snapshot.
 * NAHR enumeration and normal recombination consume exactly the same analysis.
 * This intentionally has no shared/global lifetime or eviction policy.
 */
final class HomologyPairMemo {
    private final BitSequence a;
    private final BitSequence b;
    private HomologyMap cached;

    HomologyPairMemo(BitSequence a, BitSequence b) {
        this.a = Objects.requireNonNull(a, "a");
        this.b = Objects.requireNonNull(b, "b");
    }

    HomologyMap analyze(HomologyEngine engine) {
        if (cached == null) {
            cached = Objects.requireNonNull(engine, "engine").analyze(a, b);
        }
        return cached;
    }
}
