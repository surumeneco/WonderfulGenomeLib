package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CompatibilityShortCircuitParityTest {
    @Test
    void sameBooleanFlagsAndReasonAsExhaustivePairEvaluation() {
        EngineConfig config = EngineConfig.defaults();
        HomologyCompatibilityPolicyV1 optimized = new HomologyCompatibilityPolicyV1(config);
        BitSequence template = TestBackbones.singlePair(2048)
                .chromosomes().getFirst().templateBits();
        BitSequence unrelated = TestSequences.patterned(2048, 8801234L);
        BitSequence unrelatedOther = TestSequences.patterned(2048, 90908312L);
        DiploidGenome a = new DiploidGenome(1, List.of(
                new ChromosomePair(template, template.flip(42)),
                new ChromosomePair(unrelated, unrelatedOther)));
        DiploidGenome b = new DiploidGenome(1, List.of(
                new ChromosomePair(template.flip(101), unrelated),
                new ChromosomePair(unrelatedOther, unrelated.flip(12))));

        CompatibilityReport expected = referenceAssess(config, a, b);
        assertEquals(expected, optimized.assess(a, b));
        assertEquals(referenceAssess(config, b, a), optimized.assess(b, a));
        assertEquals(referenceAssess(config, a, a), optimized.assess(a, a));
    }

    private static CompatibilityReport referenceAssess(
            EngineConfig config, DiploidGenome a, DiploidGenome b) {
        HomologyEngine engine = new HomologyEngine(config);
        List<Boolean> flags = new ArrayList<>();
        boolean all = true;
        for (int chromosome = 0; chromosome < a.chromosomePairCount(); chromosome++) {
            boolean compatible = false;
            ChromosomePair left = a.chromosomePairs().get(chromosome);
            ChromosomePair right = b.chromosomePairs().get(chromosome);
            for (BitSequence l : List.of(left.haplotypeA(), left.haplotypeB())) {
                for (BitSequence r : List.of(right.haplotypeA(), right.haplotypeB())) {
                    if (!engine.analyze(l, r).blocks().isEmpty()) compatible = true;
                }
            }
            flags.add(compatible);
            all &= compatible;
        }
        return new CompatibilityReport(all,
                all ? "COMPATIBLE" : "INSUFFICIENT_CROSS_PARENT_HOMOLOGY", flags);
    }
}
