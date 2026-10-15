package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HomologyCompatibilityPolicyV1Test {
    @Test
    void rejectsUnsupportedGenomeFormatWhenUsedDirectly() {
        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        BitSequence bits = backbone.chromosomes().getFirst().templateBits();
        DiploidGenome v2 = new DiploidGenome(2, List.of(new ChromosomePair(bits, bits)));

        HomologyCompatibilityPolicyV1 policy = new HomologyCompatibilityPolicyV1(EngineConfig.defaults());

        assertThrows(IllegalArgumentException.class, () -> policy.assess(v2, v2));
    }
}
