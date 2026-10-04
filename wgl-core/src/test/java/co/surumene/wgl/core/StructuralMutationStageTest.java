package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructuralMutationStageTest {
    @Test
    void allCoordinatesAreResolvedAgainstStageStartSnapshot() {
        TrackedSequence.Origin[] origins = new TrackedSequence.Origin[8];
        for (int i = 0; i < origins.length; i++) {
            origins[i] = new TrackedSequence.Origin(0, i, 1.0, 1.0);
        }
        TrackedSequence source = new TrackedSequence(BitSequence.fromBits("01011010"), origins);
        StructuralMutationStage stage = new StructuralMutationStage(List.of(source));

        assertTrue(stage.insert(0, 2, TrackedSequence.fresh(BitSequence.fromBits("11"))));
        assertTrue(stage.delete(0, 6, 8));

        TrackedSequence result = stage.materialize().getFirst();
        assertEquals(8, result.bitLength());
        assertEquals(0, result.originAt(0).sourceBit());
        assertEquals(1, result.originAt(1).sourceBit());
        assertTrue(result.originAt(2).isFresh());
        assertTrue(result.originAt(3).isFresh());
        assertEquals(2, result.originAt(4).sourceBit());
        assertEquals(3, result.originAt(5).sourceBit());
        assertEquals(4, result.originAt(6).sourceBit());
        assertEquals(5, result.originAt(7).sourceBit());
    }

    @Test
    void conflictingSnapshotIntervalsAreRejectedInsteadOfBecomingOrderDependent() {
        TrackedSequence source = TrackedSequence.fresh(BitSequence.fromBits("0".repeat(64)));
        StructuralMutationStage stage = new StructuralMutationStage(List.of(source));

        assertTrue(stage.delete(0, 10, 24));
        assertFalse(stage.invert(0, 20, 32));
        assertFalse(stage.insert(0, 16, TrackedSequence.fresh(BitSequence.fromBits("1"))));
        assertTrue(stage.insert(0, 40, TrackedSequence.fresh(BitSequence.fromBits("1"))));
    }
}
