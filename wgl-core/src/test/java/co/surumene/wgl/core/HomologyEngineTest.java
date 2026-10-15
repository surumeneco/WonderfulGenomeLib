package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
    void optimizedCandidateSearchMatchesExhaustiveAllWindowComparison() {
        BitSequence a = TestSequences.patterned(112, 0x13579BDFL);
        BitSequence b = a.insert(37, BitSequence.fromBits("101"))
                .flip(71)
                .flip(89);

        HomologyMap actual = new HomologyEngine(EngineConfig.defaults()).analyze(a, b);

        List<HomologyCandidate> expected = new ArrayList<>();
        for (int pa = 0; pa + HomologyEngine.ANCHOR_BITS <= a.bitLength(); pa++) {
            BitSequence aw = a.slice(pa, pa + HomologyEngine.ANCHOR_BITS);
            for (int pb = 0; pb + HomologyEngine.ANCHOR_BITS <= b.bitLength(); pb++) {
                BitSequence bw = b.slice(pb, pb + HomologyEngine.ANCHOR_BITS);
                int forward = aw.hammingDistance(bw);
                if (forward <= 2) {
                    expected.add(new HomologyCandidate(pa, pb, HomologyOrientation.FORWARD, forward));
                }
                int reverse = aw.hammingDistance(bw.reverse());
                if (reverse <= 2) {
                    expected.add(new HomologyCandidate(pa, pb, HomologyOrientation.REVERSE, reverse));
                }
            }
        }
        expected.sort(Comparator.comparingInt(HomologyCandidate::positionA)
                .thenComparingInt(HomologyCandidate::positionB)
                .thenComparingInt(candidate -> candidate.orientation() == HomologyOrientation.FORWARD ? 0 : 1)
                .thenComparingInt(HomologyCandidate::hammingDistance));

        assertEquals(expected, actual.candidates());
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
