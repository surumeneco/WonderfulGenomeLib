package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HomologyMultipleBlocksTest {
    @Test
    void preservesSeparateNormalBlocksAcrossLargeNonHomologousGap() {
        BitSequence left = TestSequences.patterned(420, 101);
        BitSequence right = TestSequences.patterned(420, 202);
        BitSequence gapA = TestSequences.patterned(1400, 303);
        BitSequence gapB = TestSequences.patterned(1400, 404);
        BitSequence a = left.concat(gapA).concat(right);
        BitSequence b = left.concat(gapB).concat(right);

        HomologyMap map = new HomologyEngine(EngineConfig.defaults()).analyze(a, b);
        assertEquals(2, map.blocks().size());
        assertTrue(map.blocks().get(0).physicalLength() >= 256);
        assertTrue(map.blocks().get(1).physicalLength() >= 256);
    }
}
