package co.surumene.wgl.core;

import co.surumene.wgl.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class BackboneCompatibilityEngine {
    private final HomologyEngine homology;

    BackboneCompatibilityEngine(EngineConfig config) {
        homology = new HomologyEngine(Objects.requireNonNull(config, "config"));
    }

    BackboneCompatibilityReport assess(
            BackboneDefinition backbone,
            DiploidGenome genome) {
        Objects.requireNonNull(backbone, "backbone");
        Objects.requireNonNull(genome, "genome");
        if (genome.chromosomePairCount() != backbone.chromosomes().size()) {
            return new BackboneCompatibilityReport(
                    false, "CHROMOSOME_COUNT_MISMATCH", List.of());
        }

        List<Boolean> flags = new ArrayList<>(genome.chromosomePairCount());
        boolean all = true;
        for (int i = 0; i < genome.chromosomePairCount(); i++) {
            BitSequence template = backbone.chromosomes().get(i).templateBits();
            ChromosomePair pair = genome.chromosomePairs().get(i);
            boolean compatible = homologous(template, pair.haplotypeA())
                    || homologous(template, pair.haplotypeB());
            flags.add(compatible);
            all &= compatible;
        }
        return report(all, flags);
    }

    BackboneCompatibilityReport assess(
            BackboneDefinition backbone,
            HaploidGenome genome) {
        Objects.requireNonNull(backbone, "backbone");
        Objects.requireNonNull(genome, "genome");
        if (genome.chromosomeCount() != backbone.chromosomes().size()) {
            return new BackboneCompatibilityReport(
                    false, "CHROMOSOME_COUNT_MISMATCH", List.of());
        }

        List<Boolean> flags = new ArrayList<>(genome.chromosomeCount());
        boolean all = true;
        for (int i = 0; i < genome.chromosomeCount(); i++) {
            BitSequence template = backbone.chromosomes().get(i).templateBits();
            boolean compatible = homologous(template, genome.chromosomes().get(i));
            flags.add(compatible);
            all &= compatible;
        }
        return report(all, flags);
    }

    private boolean homologous(BitSequence template, BitSequence candidate) {
        return !homology.analyze(template, candidate).blocks().isEmpty();
    }

    private static BackboneCompatibilityReport report(
            boolean compatible,
            List<Boolean> flags) {
        return new BackboneCompatibilityReport(
                compatible,
                compatible ? "COMPATIBLE" : "INSUFFICIENT_BACKBONE_HOMOLOGY",
                flags);
    }
}
