package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class HomologyPairMemoTest {
    @Test
    void reusesAnalyzedPairWithinOneMeiosisWithoutChangingMap() {
        BitSequence a = TestBackbones.singlePair(2048).chromosomes().getFirst().templateBits();
        BitSequence b = a.flip(101);
        HomologyEngine engine = new HomologyEngine(EngineConfig.defaults());
        HomologyPairMemo memo = new HomologyPairMemo(a, b);

        HomologyMap expected = engine.analyze(a, b);
        HomologyMap first = memo.analyze(engine);
        HomologyMap second = memo.analyze(engine);
        assertEquals(expected, first);
        assertSame(first, second, "second consumption must not recompute Homology");
    }

    @Test
    void separateMeiosesDoNotShareCachedResultsAndEmptyMapIsCachedToo() {
        BitSequence one = BitSequence.fromBits("0".repeat(20));
        BitSequence two = BitSequence.fromBits("1".repeat(20));
        HomologyEngine engine = new HomologyEngine(EngineConfig.defaults());
        HomologyPairMemo first = new HomologyPairMemo(one, two);
        HomologyPairMemo second = new HomologyPairMemo(one, two);

        HomologyMap empty = first.analyze(engine);
        assertTrue(empty.blocks().isEmpty());
        assertSame(empty, first.analyze(engine));
        assertNotSame(empty, second.analyze(engine),
                "the cache must not escape to other meiosis operations");
    }
}
