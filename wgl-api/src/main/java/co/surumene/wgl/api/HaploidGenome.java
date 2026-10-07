package co.surumene.wgl.api;

import java.util.List;
import java.util.Objects;

/** Immutable haploid genome used for an already-resolved gamete. */
public record HaploidGenome(int genomeFormatVersion, List<BitSequence> chromosomes) {
    public HaploidGenome {
        if (genomeFormatVersion < 1 || genomeFormatVersion > 0xFFFF) {
            throw new IllegalArgumentException("genomeFormatVersion must be 1..65535");
        }
        Objects.requireNonNull(chromosomes, "chromosomes");
        if (chromosomes.isEmpty()) {
            throw new IllegalArgumentException("at least one chromosome is required");
        }
        if (chromosomes.size() > 0xFFFF) {
            throw new IllegalArgumentException("too many chromosomes");
        }
        chromosomes = chromosomes.stream()
                .peek(chromosome -> Objects.requireNonNull(chromosome, "chromosome"))
                .toList();
    }

    public int chromosomeCount() {
        return chromosomes.size();
    }
}
