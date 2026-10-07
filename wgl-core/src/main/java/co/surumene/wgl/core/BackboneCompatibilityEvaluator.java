package co.surumene.wgl.core;

import co.surumene.wgl.api.*;

import java.util.*;

final class BackboneCompatibilityEvaluator {
    private final HomologyEngine homology;

    BackboneCompatibilityEvaluator(EngineConfig config) {
        homology = new HomologyEngine(Objects.requireNonNull(config, "config"));
    }

    BackboneCompatibilityReport assess(
            BackboneDefinition backbone,
            DiploidGenome genome) {
        Objects.requireNonNull(backbone, "backbone");
        Objects.requireNonNull(genome, "genome");
        requireMatchingFormat(backbone, genome.genomeFormatVersion());

        if (genome.chromosomePairCount() != backbone.chromosomes().size()) {
            return mismatch();
        }

        List<Boolean> compatible = new ArrayList<>(genome.chromosomePairCount());
        boolean all = true;
        for (int i = 0; i < genome.chromosomePairCount(); i++) {
            ChromosomePair pair = genome.chromosomePairs().get(i);
            BitSequence template = backbone.chromosomes().get(i).templateBits();
            boolean ok = hasHomology(template, pair.haplotypeA())
                    || hasHomology(template, pair.haplotypeB());
            compatible.add(ok);
            all &= ok;
        }
        return report(all, compatible);
    }

    BackboneCompatibilityReport assess(
            BackboneDefinition backbone,
            HaploidGenome genome) {
        Objects.requireNonNull(backbone, "backbone");
        Objects.requireNonNull(genome, "genome");
        requireMatchingFormat(backbone, genome.genomeFormatVersion());

        if (genome.chromosomeCount() != backbone.chromosomes().size()) {
            return mismatch();
        }

        List<Boolean> compatible = new ArrayList<>(genome.chromosomeCount());
        boolean all = true;
        for (int i = 0; i < genome.chromosomeCount(); i++) {
            BitSequence template = backbone.chromosomes().get(i).templateBits();
            boolean ok = hasHomology(template, genome.chromosomes().get(i));
            compatible.add(ok);
            all &= ok;
        }
        return report(all, compatible);
    }

    private boolean hasHomology(BitSequence template, BitSequence candidate) {
        return !homology.analyze(template, candidate).blocks().isEmpty();
    }

    private static void requireMatchingFormat(
            BackboneDefinition backbone,
            int genomeFormatVersion) {
        if (genomeFormatVersion != backbone.genomeFormatVersion()) {
            throw new IllegalArgumentException(
                    "genome format version does not match backbone");
        }
    }

    private static BackboneCompatibilityReport mismatch() {
        return new BackboneCompatibilityReport(
                false, "CHROMOSOME_COUNT_MISMATCH", List.of());
    }

    private static BackboneCompatibilityReport report(
            boolean all,
            List<Boolean> compatible) {
        return new BackboneCompatibilityReport(
                all,
                all ? "COMPATIBLE" : "INSUFFICIENT_BACKBONE_HOMOLOGY",
                compatible);
    }
}
