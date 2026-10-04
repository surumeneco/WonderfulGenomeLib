package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GenomeFormatVersionContractTest {
    @Test
    void formatV1EngineRejectsUnsupportedGenomeVersionsInsteadOfDecodingAsV1() {
        GenomeAddress address = new GenomeAddress(0x00, 0x00);
        TestProfile profile = TestProfile.defining(address);
        BackboneDefinition v1 = TestBackbones.singlePair(2048);
        DiploidGenome v2Genome = new DiploidGenome(2, List.of(new ChromosomePair(
                v1.chromosomes().getFirst().templateBits(),
                v1.chromosomes().getFirst().templateBits())));

        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());

        assertThrows(IllegalArgumentException.class, () -> engine.decode(profile, v2Genome));
        assertThrows(IllegalArgumentException.class, () -> engine.marker(v1, v2Genome));
    }

    @Test
    void formatV1EngineRejectsUnsupportedBackboneVersionsForSynthesisAndBreeding() {
        GenomeAddress address = new GenomeAddress(0x00, 0x00);
        TestProfile profile = TestProfile.defining(address);
        BackboneDefinition v1 = TestBackbones.singlePair(2048);
        BackboneDefinition v2 = new BackboneDefinition(
                "format-v2-test",
                2,
                v1.chromosomes(),
                v1.safetyPolicy());

        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());

        assertThrows(IllegalArgumentException.class, () -> engine.synthesize(
                profile,
                v2,
                new BasicSynthesisTarget(Map.of(address, 0.3)),
                SynthesisContext.defaults(),
                new SplitMix64GenomeRandom(1L)));

        DiploidGenome parent = new DiploidGenome(2, List.of(new ChromosomePair(
                v2.chromosomes().getFirst().templateBits(),
                v2.chromosomes().getFirst().templateBits())));
        BreedingContext context = new BreedingContext(v2, 0.0, Set.of(), null, false);
        assertThrows(IllegalArgumentException.class, () -> engine.breed(
                profile, parent, parent, context, new SplitMix64GenomeRandom(2L)));
    }
}
