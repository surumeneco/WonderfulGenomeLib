package co.surumene.wgl.api;

import java.util.List;
import java.util.Objects;

public record DiploidGenome(int genomeFormatVersion, List<ChromosomePair> chromosomePairs) {
    public DiploidGenome {
        if (genomeFormatVersion < 1 || genomeFormatVersion > 0xFFFF) {
            throw new IllegalArgumentException("genomeFormatVersion must be 1..65535");
        }
        Objects.requireNonNull(chromosomePairs, "chromosomePairs");
        if (chromosomePairs.isEmpty()) throw new IllegalArgumentException("at least one chromosome pair is required");
        if (chromosomePairs.size() > 0xFFFF) throw new IllegalArgumentException("too many chromosome pairs");
        chromosomePairs = List.copyOf(chromosomePairs);
    }

    public int chromosomePairCount() {
        return chromosomePairs.size();
    }
}
