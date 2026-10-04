package co.surumene.wgl.api;

public record EffectiveContribution(GenomeAddress address, double effect, double saturation,
                                    int chromosomeIndex, int haplotypeIndex, int startBit, boolean secondary) {
}
