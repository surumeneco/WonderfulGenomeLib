package co.surumene.wgl.api;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record BackboneDefinition(String backboneId, int genomeFormatVersion,
                                 List<ChromosomeTemplate> chromosomes, GenomeSafetyPolicy safetyPolicy) {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    public BackboneDefinition {
        Objects.requireNonNull(backboneId, "backboneId");
        if (!ID.matcher(backboneId).matches()) throw new IllegalArgumentException("invalid backboneId");
        if (genomeFormatVersion < 1 || genomeFormatVersion > 0xFFFF) throw new IllegalArgumentException("invalid format version");
        Objects.requireNonNull(chromosomes, "chromosomes");
        if (chromosomes.isEmpty()) throw new IllegalArgumentException("at least one chromosome is required");
        chromosomes = List.copyOf(chromosomes);
        Objects.requireNonNull(safetyPolicy, "safetyPolicy");
    }

    public List<Integer> baselineChromosomeLengths() {
        return chromosomes.stream().map(c -> c.templateBits().bitLength()).toList();
    }
}
