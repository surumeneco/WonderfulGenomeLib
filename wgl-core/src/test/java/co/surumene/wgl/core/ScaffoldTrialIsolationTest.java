package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class ScaffoldTrialIsolationTest {
    @Test
    void localAdjustmentIsDeterministicAndCannotAlterTheBackbone() {
        GenomeAddress address = new GenomeAddress(0, 3);
        TestProfile profile = TestProfile.defining(address);
        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        BitSequence original = backbone.chromosomes().getFirst().templateBits();
        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
        SynthesisContext context = new SynthesisContext(1, 1, 0.0, 0.0, 1);
        SynthesisTarget target = new BasicSynthesisTarget(Map.of(address, 0.50));
        SynthesisResult first = engine.synthesize(profile, backbone, target, context,
                new SplitMix64GenomeRandom(444));
        SynthesisResult repeated = engine.synthesize(profile, backbone, target, context,
                new SplitMix64GenomeRandom(444));
        assertEquals(first, repeated);
        assertInstanceOf(SynthesisResult.Success.class, first);
        assertEquals(original, backbone.chromosomes().getFirst().templateBits());
    }
}
