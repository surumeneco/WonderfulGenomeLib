package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class BackboneCompatibilityTest {
    private final WonderfulGenomeEngine engine =
            WonderfulGenomeEngine.create(EngineConfig.defaults());

    @Test
    void evaluatesDiploidAndGameteAgainstBackboneByPhysicalHomology() {
        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        BitSequence template = backbone.chromosomes().getFirst().templateBits();
        BitSequence unrelated = TestSequences.patterned(2048, 0x6F17A2L);

        DiploidGenome diploid = new DiploidGenome(1, List.of(
                new ChromosomePair(unrelated, template)));
        HaploidGenome gamete = new HaploidGenome(1, List.of(template));
        HaploidGenome incompatible = new HaploidGenome(1, List.of(unrelated));

        BackboneCompatibilityReport diploidReport =
                engine.assessBackboneCompatibility(backbone, diploid);
        BackboneCompatibilityReport gameteReport =
                engine.assessBackboneCompatibility(backbone, gamete);
        BackboneCompatibilityReport incompatibleReport =
                engine.assessBackboneCompatibility(backbone, incompatible);

        assertTrue(diploidReport.compatible());
        assertEquals(List.of(true), diploidReport.compatibleChromosomes());
        assertTrue(gameteReport.compatible());
        assertFalse(incompatibleReport.compatible());
        assertEquals(
                "INSUFFICIENT_BACKBONE_HOMOLOGY",
                incompatibleReport.reason());
        assertEquals(List.of(false), incompatibleReport.compatibleChromosomes());
    }

    @Test
    void lengthSafetyRangeDoesNotDefineBackboneCompatibility() {
        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        BitSequence template = backbone.chromosomes().getFirst().templateBits();
        BitSequence oversized = template.concat(template).concat(template);

        assertFalse(backbone.safetyPolicy().isSafe(
                List.of(oversized.bitLength()),
                backbone.baselineChromosomeLengths()));

        BackboneCompatibilityReport report =
                engine.assessBackboneCompatibility(
                        backbone,
                        new HaploidGenome(1, List.of(oversized)));

        assertTrue(report.compatible());
        assertEquals(List.of(true), report.compatibleChromosomes());
    }

    @Test
    void chromosomeCountMismatchIsDiagnosedSeparately() {
        BackboneDefinition backbone = TestBackbones.singlePair(2048);
        BitSequence template = backbone.chromosomes().getFirst().templateBits();

        BackboneCompatibilityReport report =
                engine.assessBackboneCompatibility(
                        backbone,
                        new HaploidGenome(1, List.of(template, template)));

        assertFalse(report.compatible());
        assertEquals("CHROMOSOME_COUNT_MISMATCH", report.reason());
        assertTrue(report.compatibleChromosomes().isEmpty());
    }
}
