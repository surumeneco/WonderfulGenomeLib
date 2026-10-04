package co.surumene.wgl.api;

import java.util.Map;

/** Consumer target with generic continuous-address hints used by the common synthesizer. */
public interface SynthesisTarget {
    Map<GenomeAddress, Double> continuousTargets();

    default boolean isSatisfied(DecodedGenome decoded, double tolerance) {
        for (var entry : continuousTargets().entrySet()) {
            if (StrictMath.abs(decoded.aggregate(entry.getKey()).score() - entry.getValue()) > tolerance) return false;
        }
        return true;
    }
}
