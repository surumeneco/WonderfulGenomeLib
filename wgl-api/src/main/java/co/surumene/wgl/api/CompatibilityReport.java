package co.surumene.wgl.api;

import java.util.List;

public record CompatibilityReport(boolean compatible, String reason, List<Boolean> compatibleChromosomes) {
    public CompatibilityReport {
        compatibleChromosomes = List.copyOf(compatibleChromosomes);
    }
}
