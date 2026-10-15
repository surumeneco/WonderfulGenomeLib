package co.surumene.wgl.api;

import java.util.List;

/**
 * Diagnostic result for physical compatibility between a Genome and a
 * BackboneDefinition.
 */
public record BackboneCompatibilityReport(
        boolean compatible,
        String reason,
        List<Boolean> compatibleChromosomes) {

    public BackboneCompatibilityReport {
        compatibleChromosomes = List.copyOf(compatibleChromosomes);
    }
}
