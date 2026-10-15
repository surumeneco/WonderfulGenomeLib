package co.surumene.wgl.api;

import java.util.Objects;

/** Physical Format V1 gene candidate after motif/header parsing. */
public record DecodedGene(GenomeAddress address, boolean addressValid, boolean addressCorrected,
                          boolean negative, int magnitudeCode, int rawEffectByte, int expressionCode,
                          BitSequence extension, int chromosomeIndex, int haplotypeIndex,
                          int startBit, int endBitExclusive, GeneOrientation orientation,
                          int motifHammingDistance) {
    public DecodedGene {
        Objects.requireNonNull(extension, "extension");
        Objects.requireNonNull(orientation, "orientation");
        if (magnitudeCode < 0 || magnitudeCode > 127) throw new IllegalArgumentException("magnitudeCode out of range");
        if (rawEffectByte < 0 || rawEffectByte > 255) throw new IllegalArgumentException("rawEffectByte out of range");
        if (expressionCode < 0 || expressionCode > 15) throw new IllegalArgumentException("expressionCode out of range");
        if (chromosomeIndex < -1 || haplotypeIndex < -1 || haplotypeIndex > 1) throw new IllegalArgumentException("invalid genome location");
        if (startBit < 0 || endBitExclusive < startBit) throw new IllegalArgumentException("invalid physical range");
    }

    public DecodedGene withGenomeLocation(int chromosomeIndex, int haplotypeIndex) {
        return new DecodedGene(address, addressValid, addressCorrected, negative, magnitudeCode, rawEffectByte,
                expressionCode, extension, chromosomeIndex, haplotypeIndex, startBit, endBitExclusive,
                orientation, motifHammingDistance);
    }

    public boolean regulation() {
        return addressValid && address != null && address.isRegulation();
    }
}
