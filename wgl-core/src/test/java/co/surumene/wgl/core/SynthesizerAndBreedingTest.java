package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SynthesizerAndBreedingTest {
    @Test
    void synthesizesContinuousTargetThroughCanonicalDecoder() {
        GenomeAddress address = new GenomeAddress(0x00, 0x00);
        TestProfile profile = TestProfile.defining(address);
        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        BasicSynthesisTarget target = new BasicSynthesisTarget(java.util.Map.of(address, 0.62));
        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
        SynthesisResult result = engine.synthesize(profile, backbone, target, SynthesisContext.defaults(), new SplitMix64GenomeRandom(1234));
        assertInstanceOf(SynthesisResult.Success.class, result);
        DiploidGenome genome = ((SynthesisResult.Success) result).genome();
        DecodeResult<?> decoded = engine.decode(profile, genome);
        assertEquals(0.62, decoded.decodedGenome().aggregate(address).score(), 0.002);
    }

    @Test
    void breedingIsSeedReproducibleAndCountMismatchIsNormalFailure() {
        GenomeAddress address = new GenomeAddress(0x00, 0x00);
        TestProfile profile = TestProfile.defining(address);
        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
        DiploidGenome parent = ((SynthesisResult.Success) engine.synthesize(
                profile, backbone, new BasicSynthesisTarget(java.util.Map.of(address, 0.5)),
                SynthesisContext.defaults(), new SplitMix64GenomeRandom(7))).genome();
        BreedingContext context = BreedingContext.standard(backbone);
        assertEquals(
                engine.breed(profile, parent, parent, context, new SplitMix64GenomeRandom(99)),
                engine.breed(profile, parent, parent, context, new SplitMix64GenomeRandom(99)));

        DiploidGenome mismatch = new DiploidGenome(1, List.of(
                parent.chromosomePairs().getFirst(), parent.chromosomePairs().getFirst()));
        BreedingResult result = engine.breed(profile, parent, mismatch, context, new SplitMix64GenomeRandom(1));
        assertInstanceOf(BreedingResult.NoViableOffspring.class, result);
        assertEquals(BreedingFailureReason.CHROMOSOME_COUNT_MISMATCH,
                ((BreedingResult.NoViableOffspring) result).reason());
    }
}
