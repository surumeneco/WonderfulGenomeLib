package co.surumene.wgl.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record BasicSynthesisTarget(Map<GenomeAddress, Double> continuousTargets) implements SynthesisTarget {
    public BasicSynthesisTarget {
        Objects.requireNonNull(continuousTargets, "continuousTargets");
        Map<GenomeAddress, Double> copy = new LinkedHashMap<>();
        for (var e : continuousTargets.entrySet()) {
            if (e.getKey().isRegulation()) throw new IllegalArgumentException("regulation address cannot be a direct target");
            double v = e.getValue();
            if (!Double.isFinite(v) || v < 0.0 || v > 1.0) throw new IllegalArgumentException("continuous target must be in [0,1]");
            copy.put(e.getKey(), v);
        }
        continuousTargets = Map.copyOf(copy);
    }
}
