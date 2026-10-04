package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SynthesisFailureContractTest {
    @Test
    void reportsSafetyRejectedWhenEveryCandidateViolatesBackboneSafety() {
        GenomeAddress address = new GenomeAddress(0x00, 0x00);
        TestProfile profile = TestProfile.defining(address);

        BackboneDefinition base = TestBackbones.singlePair(1024);
        GenomeSafetyPolicy exactLengthOnly = (candidate, baseline) -> candidate.equals(baseline);
        BackboneDefinition strict = new BackboneDefinition(
                "strict-test-backbone",
                base.genomeFormatVersion(),
                base.chromosomes(),
                exactLengthOnly);

        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
        SynthesisResult result = engine.synthesize(
                profile,
                strict,
                new BasicSynthesisTarget(Map.of(address, 0.4)),
                new SynthesisContext(4, 8, 0.0, 0.0, 2),
                new SplitMix64GenomeRandom(7));

        SynthesisResult.Failure failure = assertInstanceOf(SynthesisResult.Failure.class, result);
        assertEquals(SynthesisFailureReason.SAFETY_REJECTED, failure.reason());
    }
}
