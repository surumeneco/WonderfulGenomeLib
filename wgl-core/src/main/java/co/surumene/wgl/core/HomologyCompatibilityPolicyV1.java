package co.surumene.wgl.core;

import co.surumene.wgl.api.*;

import java.util.*;

public final class HomologyCompatibilityPolicyV1 implements CompatibilityPolicy {
    private final HomologyEngine homology;

    public HomologyCompatibilityPolicyV1(EngineConfig config) {
        homology = new HomologyEngine(Objects.requireNonNull(config, "config"));
    }

    @Override
    public CompatibilityReport assess(
            BreedingParentSource a,
            BreedingParentSource b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.genomeFormatVersion() != WonderfulGenomeEngine.GENOME_FORMAT_VERSION
                || b.genomeFormatVersion() != WonderfulGenomeEngine.GENOME_FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "HomologyCompatibilityPolicyV1 supports Genome Format V1 only");
        }
        if (a.chromosomeCount() != b.chromosomeCount()) {
            return new CompatibilityReport(
                    false, "CHROMOSOME_COUNT_MISMATCH", List.of());
        }

        List<Boolean> flags = new ArrayList<>(a.chromosomeCount());
        boolean all = true;
        for (int chromosome = 0; chromosome < a.chromosomeCount(); chromosome++) {
            boolean compatible = false;
            for (BitSequence left : haplotypes(a, chromosome)) {
                for (BitSequence right : haplotypes(b, chromosome)) {
                    if (!homology.analyze(left, right).blocks().isEmpty()) {
                        compatible = true;
                    }
                }
            }
            flags.add(compatible);
            all &= compatible;
        }
        return new CompatibilityReport(
                all,
                all ? "COMPATIBLE" : "INSUFFICIENT_CROSS_PARENT_HOMOLOGY",
                flags);
    }

    private static List<BitSequence> haplotypes(
            BreedingParentSource source,
            int chromosome) {
        if (source instanceof BreedingParentSource.DiploidParent diploid) {
            ChromosomePair pair =
                    diploid.genome().chromosomePairs().get(chromosome);
            return List.of(pair.haplotypeA(), pair.haplotypeB());
        }
        BreedingParentSource.Gamete gamete =
                (BreedingParentSource.Gamete) source;
        return List.of(gamete.genome().chromosomes().get(chromosome));
    }
}
