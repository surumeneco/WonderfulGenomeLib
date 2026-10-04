package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProvenanceGuardTest {
    @Test
    void onlyHeadersDerivedFromLegitimateSourceRemainAllowed() {
        EngineConfig config = EngineConfig.defaults();
        GenomeAddress forbidden = new GenomeAddress(0x05, 0x00);
        TestProfile profile = TestProfile.defining(forbidden);
        PhysicalGenomeDecoder parser = new PhysicalGenomeDecoder(config);
        BitSequence gene = GeneCodecV1.encode(forbidden, false, 73, 15, BitSequence.empty());
        LocalRateSnapshot rates = LocalRateSnapshot.capture(gene, profile, parser, config);
        TrackedSequence source = TrackedSequence.snapshot(gene, 0, rates);
        ProvenanceGuard guard = ProvenanceGuard.capture(profile, parser, Set.of(forbidden), List.of(source));

        assertTrue(guard.valid(List.of(source)));
        assertTrue(guard.valid(List.of(source.concat(source))), "copy/duplication preserves legitimate provenance");

        TrackedSequence correctedHeaderMutation = source.flip(16 + 5);
        assertTrue(guard.valid(List.of(correctedHeaderMutation)), "SECDED-correctable mutation keeps source provenance");

        assertFalse(guard.valid(List.of(TrackedSequence.fresh(gene))),
                "a newly created identical header is still de novo and must be rejected");
    }
}
