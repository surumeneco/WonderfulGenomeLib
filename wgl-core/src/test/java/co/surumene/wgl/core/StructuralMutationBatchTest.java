package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructuralMutationBatchTest {
    @Test
    void appliesStageStartCoordinatesIndependentOfEditOrder() {
        List<TrackedSequence> snapshot = List.of(
                TrackedSequence.fresh(BitSequence.fromBits("00001111")));

        var remove = new StructuralMutationBatch.Remove(0, 1, 3);
        var add = new StructuralMutationBatch.Insert(
                0, 6, TrackedSequence.fresh(BitSequence.fromBits("10")));

        var first = StructuralMutationBatch.apply(snapshot, List.of(remove, add));
        var second = StructuralMutationBatch.apply(snapshot, List.of(add, remove));

        assertEquals("00111011", first.getFirst().bits().toBitString());
        assertEquals(first.getFirst().bits(), second.getFirst().bits());
    }

    @Test
    void rejectsCompetingEdits() {
        var remove = new StructuralMutationBatch.Remove(0, 10, 20);
        var overlap = new StructuralMutationBatch.Replace(
                0, 15, 25, TrackedSequence.fresh(BitSequence.fromBits("101")));
        var inside = new StructuralMutationBatch.Insert(
                0, 12, TrackedSequence.fresh(BitSequence.fromBits("1")));
        var sameA = new StructuralMutationBatch.Insert(
                0, 30, TrackedSequence.fresh(BitSequence.fromBits("1")));
        var sameB = new StructuralMutationBatch.Insert(
                0, 30, TrackedSequence.fresh(BitSequence.fromBits("0")));

        assertTrue(StructuralMutationBatch.conflicts(List.of(remove), List.of(overlap)));
        assertTrue(StructuralMutationBatch.conflicts(List.of(remove), List.of(inside)));
        assertTrue(StructuralMutationBatch.conflicts(List.of(sameA), List.of(sameB)));
        assertFalse(StructuralMutationBatch.conflicts(List.of(remove),
                List.of(new StructuralMutationBatch.Insert(
                        0, 20, TrackedSequence.fresh(BitSequence.fromBits("1"))))));
    }
}
