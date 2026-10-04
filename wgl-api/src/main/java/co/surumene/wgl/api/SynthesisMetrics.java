package co.surumene.wgl.api;

import java.util.List;
import java.util.Objects;

public record SynthesisMetrics(List<SynthesisHaplotypeMetrics> haplotypes) {
    public SynthesisMetrics {
        Objects.requireNonNull(haplotypes, "haplotypes");
        haplotypes = List.copyOf(haplotypes);
        for (SynthesisHaplotypeMetrics metrics : haplotypes) {
            Objects.requireNonNull(metrics, "haplotypes contains null");
        }
    }

    public int totalGeneCandidateCount() {
        return haplotypes.stream()
                .mapToInt(SynthesisHaplotypeMetrics::geneCandidateCount)
                .sum();
    }

    public long totalRecognizableBits() {
        return haplotypes.stream()
                .mapToLong(SynthesisHaplotypeMetrics::recognizableBits)
                .sum();
    }

    public long totalBits() {
        return haplotypes.stream()
                .mapToLong(SynthesisHaplotypeMetrics::bitLength)
                .sum();
    }
}
