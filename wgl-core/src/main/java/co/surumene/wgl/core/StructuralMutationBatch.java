package co.surumene.wgl.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Applies a set of non-conflicting structural edits against one immutable stage-start snapshot.
 * Coordinates therefore never depend on the order in which structural event kinds are evaluated.
 */
final class StructuralMutationBatch {
    sealed interface Edit permits Insert, Remove, Replace {
        int chromosome();
        int coordinate();
    }

    record Insert(int chromosome, int position, TrackedSequence payload) implements Edit {
        Insert {
            if (chromosome < 0 || position < 0) throw new IllegalArgumentException("invalid insertion coordinate");
            Objects.requireNonNull(payload, "payload");
        }
        @Override public int coordinate() { return position; }
    }

    record Remove(int chromosome, int start, int end) implements Edit {
        Remove {
            if (chromosome < 0 || start < 0 || end <= start) throw new IllegalArgumentException("invalid removal interval");
        }
        @Override public int coordinate() { return start; }
    }

    record Replace(int chromosome, int start, int end, TrackedSequence payload) implements Edit {
        Replace {
            if (chromosome < 0 || start < 0 || end <= start) throw new IllegalArgumentException("invalid replacement interval");
            Objects.requireNonNull(payload, "payload");
        }
        @Override public int coordinate() { return start; }
    }

    private StructuralMutationBatch() {}

    static boolean conflicts(List<? extends Edit> existing, List<? extends Edit> candidate) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(candidate, "candidate");
        for (int i = 0; i < candidate.size(); i++) {
            for (int j = i + 1; j < candidate.size(); j++) {
                if (conflicts(candidate.get(i), candidate.get(j))) return true;
            }
        }
        for (Edit a : existing) {
            for (Edit b : candidate) {
                if (conflicts(a, b)) return true;
            }
        }
        return false;
    }

    static List<TrackedSequence> apply(List<TrackedSequence> snapshot, List<? extends Edit> edits) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(edits, "edits");
        if (conflicts(List.of(), edits)) throw new IllegalArgumentException("structural edits conflict");

        List<TrackedSequence> result = new ArrayList<>(snapshot);
        for (int chromosome = 0; chromosome < snapshot.size(); chromosome++) {
            TrackedSequence initial = snapshot.get(chromosome);
            List<Edit> local = edits.stream().filter(e -> e.chromosome() == chromosome)
                    .map(e -> (Edit) e)
                    .sorted(Comparator.comparingInt(Edit::coordinate).reversed()
                            .thenComparingInt(StructuralMutationBatch::editPriority))
                    .toList();
            validateBounds(initial, local);
            TrackedSequence current = initial;
            for (Edit edit : local) {
                if (edit instanceof Insert insert) {
                    current = current.insert(insert.position(), insert.payload());
                } else if (edit instanceof Remove remove) {
                    current = current.delete(remove.start(), remove.end());
                } else if (edit instanceof Replace replace) {
                    current = current.replace(replace.start(), replace.end(), replace.payload());
                }
            }
            result.set(chromosome, current);
        }
        for (Edit edit : edits) {
            if (edit.chromosome() >= snapshot.size()) throw new IllegalArgumentException("chromosome out of range");
        }
        return List.copyOf(result);
    }

    private static int editPriority(Edit edit) {
        return edit instanceof Insert ? 1 : 0;
    }

    private static void validateBounds(TrackedSequence sequence, List<Edit> edits) {
        for (Edit edit : edits) {
            if (edit instanceof Insert insert) {
                if (insert.position() > sequence.bitLength()) throw new IllegalArgumentException("insertion out of range");
            } else if (edit instanceof Remove remove) {
                if (remove.end() > sequence.bitLength()) throw new IllegalArgumentException("removal out of range");
            } else if (edit instanceof Replace replace) {
                if (replace.end() > sequence.bitLength()) throw new IllegalArgumentException("replacement out of range");
            }
        }
    }

    private static boolean conflicts(Edit a, Edit b) {
        if (a.chromosome() != b.chromosome()) return false;
        if (a instanceof Insert ia && b instanceof Insert ib) return ia.position() == ib.position();
        if (a instanceof Insert ia) return insertionConflicts(ia.position(), b);
        if (b instanceof Insert ib) return insertionConflicts(ib.position(), a);
        int aStart = start(a), aEnd = end(a);
        int bStart = start(b), bEnd = end(b);
        return Math.max(aStart, bStart) < Math.min(aEnd, bEnd);
    }

    private static boolean insertionConflicts(int position, Edit interval) {
        return position > start(interval) && position < end(interval);
    }

    private static int start(Edit edit) {
        if (edit instanceof Remove remove) return remove.start();
        if (edit instanceof Replace replace) return replace.start();
        throw new IllegalArgumentException("edit has no interval");
    }

    private static int end(Edit edit) {
        if (edit instanceof Remove remove) return remove.end();
        if (edit instanceof Replace replace) return replace.end();
        throw new IllegalArgumentException("edit has no interval");
    }
}
