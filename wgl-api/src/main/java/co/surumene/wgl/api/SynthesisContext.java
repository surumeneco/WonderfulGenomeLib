package co.surumene.wgl.api;

public record SynthesisContext(int minPositiveGenes, int maxPositiveGenes,
                               double cancellationMin, double cancellationMax,
                               int genomeRetries) {
    public SynthesisContext {
        if (minPositiveGenes < 1 || maxPositiveGenes < minPositiveGenes) throw new IllegalArgumentException("invalid gene count range");
        if (!Double.isFinite(cancellationMin) || !Double.isFinite(cancellationMax)
                || cancellationMin < 0 || cancellationMax < cancellationMin || cancellationMax >= 1) {
            throw new IllegalArgumentException("invalid cancellation range");
        }
        if (genomeRetries < 1) throw new IllegalArgumentException("genomeRetries must be >= 1");
    }

    public static SynthesisContext defaults() {
        return new SynthesisContext(4, 8, 0.0, 0.20, 8);
    }
}
