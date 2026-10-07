package co.surumene.wgl.api;

public interface GenomeEngine {
    <P> DecodeResult<P> decode(GenomeProfile<P> profile, DiploidGenome genome);
    SynthesisResult synthesize(GenomeProfile<?> profile, BackboneDefinition backbone, SynthesisTarget target,
                               SynthesisContext context, GenomeRandom random);

    GenomeRandom standardRandom(long seed);

    default SynthesisResult synthesize(GenomeProfile<?> profile, BackboneDefinition backbone,
                                       SynthesisTarget target, SynthesisContext context, long seed) {
        return synthesize(profile, backbone, target, context, standardRandom(seed));
    }

    CompatibilityReport assessCompatibility(
            DiploidGenome parentA,
            DiploidGenome parentB,
            CompatibilityPolicy policy);

    BackboneCompatibilityReport assessBackboneCompatibility(
            BackboneDefinition backbone,
            DiploidGenome genome);

    BackboneCompatibilityReport assessBackboneCompatibility(
            BackboneDefinition backbone,
            HaploidGenome genome);

    BreedingResult breed(
            GenomeProfile<?> profile,
            DiploidGenome parentA,
            DiploidGenome parentB,
            BreedingContext context,
            GenomeRandom random);

    default BreedingResult breed(
            GenomeProfile<?> profile,
            DiploidGenome parentA,
            DiploidGenome parentB,
            BreedingContext context,
            long seed) {
        return breed(profile, parentA, parentB, context, standardRandom(seed));
    }

    byte[] encode(DiploidGenome genome);
    DiploidGenome decodeBinary(byte[] bytes);

    byte[] encodeParentSource(BreedingParentSource source);
    BreedingParentSource decodeParentSource(byte[] bytes);
    BreedingParentSourceCodec parentSourceCodec();

    MarkerResult marker(BackboneDefinition backbone, DiploidGenome genome);
    MarkerResult marker(BackboneDefinition backbone, DiploidGenome genome, MarkerScheme scheme);
    GenomeSequenceCodec sequenceCodec();
    GeneSequenceCodec geneSequenceCodec();
}
