package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SeedConvenienceApiTest {
    @Test
    void seedConvenienceUsesTheStandardDeterministicRandomSource() {
        GenomeAddress address = new GenomeAddress(0x00, 0x00);
        TestProfile profile = TestProfile.defining(address);
        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        GenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
        SynthesisTarget target = new BasicSynthesisTarget(Map.of(address, 0.42));

        SynthesisResult explicit = engine.synthesize(profile, backbone, target,
                SynthesisContext.defaults(), new SplitMix64GenomeRandom(314159L));
        SynthesisResult seeded = engine.synthesize(profile, backbone, target,
                SynthesisContext.defaults(), 314159L);

        assertEquals(explicit, seeded);

        DiploidGenome parent = ((SynthesisResult.Success) explicit).genome();
        BreedingContext context = new BreedingContext(backbone, 0.0, Set.of(), null, false);
        assertEquals(
                engine.breed(profile, parent, parent, context, new SplitMix64GenomeRandom(271828L)),
                engine.breed(profile, parent, parent, context, 271828L));
    }

    @Test
    void standardRandomFactoryMatchesSplitMix64GoldenImplementation() {
        GenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
        GenomeRandom fromEngine = engine.standardRandom(0L);
        SplitMix64GenomeRandom direct = new SplitMix64GenomeRandom(0L);

        for (int i = 0; i < 8; i++) assertEquals(direct.nextLong(), fromEngine.nextLong());
    }
}
