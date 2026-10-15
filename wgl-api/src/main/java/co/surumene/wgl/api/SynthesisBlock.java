package co.surumene.wgl.api;

import java.util.Objects;

/**
 * Consumer-supplied physical Founder block placed by the common synthesizer.
 * Placement metadata is synthesis-time only and is never persisted into the Genome.
 * A chromosome or haplotype index of -1 means that WGL chooses it using the supplied GenomeRandom.
 */
public record SynthesisBlock(BitSequence bits, int chromosomeIndex, int haplotypeIndex) {
    public static final int RANDOM = -1;

    public SynthesisBlock {
        Objects.requireNonNull(bits, "bits");
        if (bits.bitLength() == 0) {
            throw new IllegalArgumentException("synthesis block must contain at least one bit");
        }
        if (chromosomeIndex < RANDOM) {
            throw new IllegalArgumentException("chromosomeIndex must be -1 or >= 0");
        }
        if (haplotypeIndex != RANDOM && haplotypeIndex != 0 && haplotypeIndex != 1) {
            throw new IllegalArgumentException("haplotypeIndex must be -1, 0, or 1");
        }
    }

    public static SynthesisBlock random(BitSequence bits) {
        return new SynthesisBlock(bits, RANDOM, RANDOM);
    }

    public static SynthesisBlock onChromosome(BitSequence bits, int chromosomeIndex) {
        return new SynthesisBlock(bits, chromosomeIndex, RANDOM);
    }

    public static SynthesisBlock fixed(BitSequence bits, int chromosomeIndex, int haplotypeIndex) {
        return new SynthesisBlock(bits, chromosomeIndex, haplotypeIndex);
    }
}
