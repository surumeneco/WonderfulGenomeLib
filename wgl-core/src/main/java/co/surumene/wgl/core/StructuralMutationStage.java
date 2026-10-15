package co.surumene.wgl.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Plans normal structural-mutation events against one immutable stage-start snapshot.
 * Accepted edits are conflict-checked in snapshot coordinates and materialized as one batch.
 */
final class StructuralMutationStage {
    private final List<TrackedSequence> snapshot;
    private final List<StructuralMutationBatch.Edit> edits;

    StructuralMutationStage(List<TrackedSequence> snapshot) {
        this.snapshot = List.copyOf(Objects.requireNonNull(snapshot, "snapshot"));
        this.edits = new ArrayList<>();
    }

    private StructuralMutationStage(StructuralMutationStage source) {
        this.snapshot = source.snapshot;
        this.edits = new ArrayList<>(source.edits);
    }

    StructuralMutationStage copy() {
        return new StructuralMutationStage(this);
    }

    List<TrackedSequence> snapshot() {
        return snapshot;
    }

    boolean insert(int chromosome, int boundary, TrackedSequence sequence) {
        validateBoundary(chromosome, boundary);
        Objects.requireNonNull(sequence, "sequence");
        return tryAdd(List.of(new StructuralMutationBatch.Insert(chromosome, boundary, sequence)));
    }

    boolean delete(int chromosome, int from, int to) {
        validateRange(chromosome, from, to);
        return tryAdd(List.of(new StructuralMutationBatch.Remove(chromosome, from, to)));
    }

    boolean invert(int chromosome, int from, int to) {
        validateRange(chromosome, from, to);
        TrackedSequence reversed = snapshot.get(chromosome).slice(from, to).reverse();
        return tryAdd(List.of(new StructuralMutationBatch.Replace(chromosome, from, to, reversed)));
    }

    boolean duplicate(int sourceChromosome, int from, int to,
                      int targetChromosome, int targetBoundary) {
        validateRange(sourceChromosome, from, to);
        validateBoundary(targetChromosome, targetBoundary);
        TrackedSequence payload = snapshot.get(sourceChromosome).slice(from, to);
        return tryAdd(List.of(new StructuralMutationBatch.Insert(targetChromosome, targetBoundary, payload)));
    }

    boolean move(int sourceChromosome, int from, int to,
                 int targetChromosome, int targetBoundary) {
        validateRange(sourceChromosome, from, to);
        validateBoundary(targetChromosome, targetBoundary);
        TrackedSequence payload = snapshot.get(sourceChromosome).slice(from, to);
        return tryAdd(List.of(
                new StructuralMutationBatch.Remove(sourceChromosome, from, to),
                new StructuralMutationBatch.Insert(targetChromosome, targetBoundary, payload)));
    }

    boolean reciprocalTailSwap(int firstChromosome, int firstBoundary,
                               int secondChromosome, int secondBoundary) {
        if (firstChromosome == secondChromosome) return false;
        validateBoundary(firstChromosome, firstBoundary);
        validateBoundary(secondChromosome, secondBoundary);

        TrackedSequence first = snapshot.get(firstChromosome);
        TrackedSequence second = snapshot.get(secondChromosome);
        return tryAdd(List.of(
                new StructuralMutationBatch.Replace(firstChromosome, firstBoundary, first.bitLength(),
                        second.slice(secondBoundary, second.bitLength())),
                new StructuralMutationBatch.Replace(secondChromosome, secondBoundary, second.bitLength(),
                        first.slice(firstBoundary, first.bitLength()))));
    }

    List<TrackedSequence> materialize() {
        return StructuralMutationBatch.apply(snapshot, edits);
    }

    private boolean tryAdd(List<? extends StructuralMutationBatch.Edit> candidate) {
        if (StructuralMutationBatch.conflicts(edits, candidate)) return false;
        edits.addAll(candidate);
        return true;
    }

    private void validateChromosome(int chromosome) {
        if (chromosome < 0 || chromosome >= snapshot.size()) {
            throw new IndexOutOfBoundsException("invalid chromosome");
        }
    }

    private void validateBoundary(int chromosome, int boundary) {
        validateChromosome(chromosome);
        if (boundary < 0 || boundary > snapshot.get(chromosome).bitLength()) {
            throw new IndexOutOfBoundsException("invalid boundary");
        }
    }

    private void validateRange(int chromosome, int from, int to) {
        validateChromosome(chromosome);
        if (from < 0 || to <= from || to > snapshot.get(chromosome).bitLength()) {
            throw new IndexOutOfBoundsException("invalid interval");
        }
    }
}
