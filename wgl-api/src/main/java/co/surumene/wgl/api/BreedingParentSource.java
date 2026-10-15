package co.surumene.wgl.api;

import java.util.Objects;

/**
 * One parental input to breeding: either a complete diploid parent or an
 * already-resolved haploid gamete.
 */
public sealed interface BreedingParentSource
        permits BreedingParentSource.DiploidParent, BreedingParentSource.Gamete {

    int genomeFormatVersion();

    int chromosomeCount();

    record DiploidParent(DiploidGenome genome) implements BreedingParentSource {
        public DiploidParent {
            Objects.requireNonNull(genome, "genome");
        }

        @Override
        public int genomeFormatVersion() {
            return genome.genomeFormatVersion();
        }

        @Override
        public int chromosomeCount() {
            return genome.chromosomePairCount();
        }
    }

    record Gamete(HaploidGenome genome) implements BreedingParentSource {
        public Gamete {
            Objects.requireNonNull(genome, "genome");
        }

        @Override
        public int genomeFormatVersion() {
            return genome.genomeFormatVersion();
        }

        @Override
        public int chromosomeCount() {
            return genome.chromosomeCount();
        }
    }
}
