package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HomologyEngineTest {
    @Test
    void findsLosslessAnchorsAndBuildsMonotonicBlocks() {
        BitSequence a = TestSequences.patterned(600, 17);
        BitSequence b = a.insert(300, BitSequence.fromBits("1010101"));
        HomologyEngine engine = new HomologyEngine(EngineConfig.defaults());
        HomologyMap map = engine.analyze(a, b);
        assertFalse(map.candidates().isEmpty());
        assertTrue(map.blocks().stream().anyMatch(block -> block.physicalLength() >= 256));
    }

    @Test
    void recognizesReverseCandidatesButDoesNotPutThemInNormalBlocks() {
        BitSequence a = TestSequences.patterned(400, 9);
        HomologyEngine engine = new HomologyEngine(EngineConfig.defaults());
        HomologyMap map = engine.analyze(a, a.reverse());
        assertTrue(map.candidates().stream().anyMatch(c -> c.orientation() == HomologyOrientation.REVERSE));
        assertTrue(map.blocks().isEmpty());
    }
}
