package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SynthesizerCancellationTest {
    @Test
    void cancellationContextProducesNegativeGenesWithoutBreakingTarget() {
        GenomeAddress address = new GenomeAddress(0x00, 0x01);
        TestProfile profile = TestProfile.defining(address);
        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
        SynthesisContext context = new SynthesisContext(5, 7, 0.20, 0.35, 8);

        SynthesisResult result = engine.synthesize(profile, backbone,
                new BasicSynthesisTarget(Map.of(address, 0.42)), context, new SplitMix64GenomeRandom(1776));
        assertInstanceOf(SynthesisResult.Success.class, result);
        DiploidGenome genome = ((SynthesisResult.Success) result).genome();
        DecodeResult<?> decoded = engine.decode(profile, genome);
        assertEquals(0.42, decoded.decodedGenome().aggregate(address).score(), 0.002);
        assertTrue(decoded.decodedGenome().physicalGenes().stream()
                .anyMatch(g -> address.equals(g.address()) && g.negative()));
    }
}
