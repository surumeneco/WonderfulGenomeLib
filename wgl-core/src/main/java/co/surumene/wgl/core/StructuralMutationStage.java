package co.surumene.wgl.core;

import java.util.*;

/**
 * Plans normal structural-mutation events against one immutable stage-start snapshot.
 * Coordinates never shift while events are being selected. Conflicting cuts or destinations
 * are rejected, then the accepted plan is materialized in one pass.
 */
final class StructuralMutationStage {
    private final List<TrackedSequence> snapshot;
    private final List<List<Replacement>> replacements;
    private final List<List<Insertion>> insertions;
    private final List<List<Interval>> reservedIntervals;
    private final List<Set<Integer>> reservedBoundaries;

    StructuralMutationStage(List<TrackedSequence> snapshot) {
        this.snapshot = List.copyOf(Objects.requireNonNull(snapshot, "snapshot"));
        int n = snapshot.size();
        replacements = lists(n);
        insertions = lists(n);
        reservedIntervals = lists(n);
        reservedBoundaries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) reservedBoundaries.add(new HashSet<>());
    }

    private StructuralMutationStage(StructuralMutationStage source) {
        this.snapshot = source.snapshot;
        int n = snapshot.size();
        replacements = lists(n);
        insertions = lists(n);
        reservedIntervals = lists(n);
        reservedBoundaries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            replacements.get(i).addAll(source.replacements.get(i));
            insertions.get(i).addAll(source.insertions.get(i));
            reservedIntervals.get(i).addAll(source.reservedIntervals.get(i));
            reservedBoundaries.add(new HashSet<>(source.reservedBoundaries.get(i)));
        }
    }

    StructuralMutationStage copy() { return new StructuralMutationStage(this); }
    List<TrackedSequence> snapshot() { return snapshot; }

    boolean insert(int chromosome, int boundary, TrackedSequence sequence) {
        validateChromosome(chromosome);
        Objects.requireNonNull(sequence, "sequence");
        validateBoundary(chromosome, boundary);
        if (!reserveBoundary(chromosome, boundary)) return false;
        insertions.get(chromosome).add(new Insertion(boundary, sequence));
        return true;
    }

    boolean delete(int chromosome, int from, int to) {
        validateRange(chromosome, from, to);
        if (!reserveInterval(chromosome, from, to)) return false;
        replacements.get(chromosome).add(new Replacement(from, to, TrackedSequence.fresh(co.surumene.wgl.api.BitSequence.empty())));
        return true;
    }

    boolean invert(int chromosome, int from, int to) {
        validateRange(chromosome, from, to);
        if (!reserveInterval(chromosome, from, to)) return false;
        replacements.get(chromosome).add(new Replacement(from, to, snapshot.get(chromosome).slice(from, to).reverse()));
        return true;
    }

    boolean duplicate(int sourceChromosome, int from, int to, int targetChromosome, int targetBoundary) {
        validateRange(sourceChromosome, from, to);
        validateBoundary(targetChromosome, targetBoundary);
        return insert(targetChromosome, targetBoundary, snapshot.get(sourceChromosome).slice(from, to));
    }

    boolean move(int sourceChromosome, int from, int to, int targetChromosome, int targetBoundary) {
        validateRange(sourceChromosome, from, to);
        validateBoundary(targetChromosome, targetBoundary);
        if (sourceChromosome == targetChromosome && targetBoundary >= from && targetBoundary <= to) return false;
        if (!canReserveInterval(sourceChromosome, from, to) || !canReserveBoundary(targetChromosome, targetBoundary)) return false;
        TrackedSequence piece = snapshot.get(sourceChromosome).slice(from, to);
        reserveInterval(sourceChromosome, from, to);
        replacements.get(sourceChromosome).add(new Replacement(from, to,
                TrackedSequence.fresh(co.surumene.wgl.api.BitSequence.empty())));
        reserveBoundary(targetChromosome, targetBoundary);
        insertions.get(targetChromosome).add(new Insertion(targetBoundary, piece));
        return true;
    }

    boolean reciprocalTailSwap(int firstChromosome, int firstBoundary,
                               int secondChromosome, int secondBoundary) {
        if (firstChromosome == secondChromosome) return false;
        validateBoundary(firstChromosome, firstBoundary);
        validateBoundary(secondChromosome, secondBoundary);
        TrackedSequence first = snapshot.get(firstChromosome);
        TrackedSequence second = snapshot.get(secondChromosome);
        if (!canReserveInterval(firstChromosome, firstBoundary, first.bitLength())
                || !canReserveInterval(secondChromosome, secondBoundary, second.bitLength())) return false;
        reserveInterval(firstChromosome, firstBoundary, first.bitLength());
        reserveInterval(secondChromosome, secondBoundary, second.bitLength());
        replacements.get(firstChromosome).add(new Replacement(firstBoundary, first.bitLength(),
                second.slice(secondBoundary, second.bitLength())));
        replacements.get(secondChromosome).add(new Replacement(secondBoundary, second.bitLength(),
                first.slice(firstBoundary, first.bitLength())));
        return true;
    }

    List<TrackedSequence> materialize() {
        List<TrackedSequence> out = new ArrayList<>(snapshot.size());
        for (int c = 0; c < snapshot.size(); c++) out.add(materializeChromosome(c));
        return List.copyOf(out);
    }

    private TrackedSequence materializeChromosome(int chromosome) {
        TrackedSequence base = snapshot.get(chromosome);
        Map<Integer, List<TrackedSequence>> additions = new TreeMap<>();
        for (Insertion insertion : insertions.get(chromosome)) {
            additions.computeIfAbsent(insertion.boundary(), k -> new ArrayList<>()).add(insertion.sequence());
        }
        Map<Integer, Replacement> byStart = new HashMap<>();
        for (Replacement replacement : replacements.get(chromosome)) byStart.put(replacement.from(), replacement);

        TrackedSequence out = TrackedSequence.fresh(co.surumene.wgl.api.BitSequence.empty());
        int cursor = 0;
        while (cursor <= base.bitLength()) {
            for (TrackedSequence addition : additions.getOrDefault(cursor, List.of())) out = out.concat(addition);
            if (cursor == base.bitLength()) break;
            Replacement replacement = byStart.get(cursor);
            if (replacement != null) {
                out = out.concat(replacement.sequence());
                cursor = replacement.to();
            } else {
                out = out.concat(base.slice(cursor, cursor + 1));
                cursor++;
            }
        }
        return out;
    }

    private boolean reserveInterval(int chromosome, int from, int to) {
        if (!canReserveInterval(chromosome, from, to)) return false;
        reservedIntervals.get(chromosome).add(new Interval(from, to));
        return true;
    }

    private boolean canReserveInterval(int chromosome, int from, int to) {
        for (Interval existing : reservedIntervals.get(chromosome)) {
            if (from <= existing.to() && existing.from() <= to) return false;
        }
        for (int boundary : reservedBoundaries.get(chromosome)) {
            if (boundary >= from && boundary <= to) return false;
        }
        return true;
    }

    private boolean reserveBoundary(int chromosome, int boundary) {
        if (!canReserveBoundary(chromosome, boundary)) return false;
        reservedBoundaries.get(chromosome).add(boundary);
        return true;
    }

    private boolean canReserveBoundary(int chromosome, int boundary) {
        if (reservedBoundaries.get(chromosome).contains(boundary)) return false;
        for (Interval interval : reservedIntervals.get(chromosome)) {
            if (boundary >= interval.from() && boundary <= interval.to()) return false;
        }
        return true;
    }

    private void validateChromosome(int chromosome) {
        if (chromosome < 0 || chromosome >= snapshot.size()) throw new IndexOutOfBoundsException("invalid chromosome");
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

    private static <T> List<List<T>> lists(int count) {
        List<List<T>> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) out.add(new ArrayList<>());
        return out;
    }

    private record Interval(int from, int to) {}
    private record Replacement(int from, int to, TrackedSequence sequence) {}
    private record Insertion(int boundary, TrackedSequence sequence) {}
}
