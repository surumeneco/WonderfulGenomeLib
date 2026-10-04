package co.surumene.wgl.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class RecombinationEventPlan {
    record Boundary(int a, int b, boolean nahr) {
        Boundary {
            if (a < 0 || b < 0) throw new IllegalArgumentException("boundary coordinates must be >= 0");
        }
    }

    private final List<Boundary> boundaries;

    private RecombinationEventPlan(List<Boundary> boundaries) {
        this.boundaries = List.copyOf(boundaries);
    }

    static RecombinationEventPlan withNahr(Boundary nahr, List<Boundary> normalBoundaries) {
        Objects.requireNonNull(nahr, "nahr");
        Objects.requireNonNull(normalBoundaries, "normalBoundaries");
        if (!nahr.nahr()) throw new IllegalArgumentException("nahr boundary is required");

        List<Boundary> before = new ArrayList<>();
        List<Boundary> after = new ArrayList<>();
        for (Boundary normal : normalBoundaries) {
            if (normal.nahr()) throw new IllegalArgumentException("normal list contains nahr boundary");
            if (normal.a() == nahr.a() && normal.b() == nahr.b()) continue;
            if (normal.a() < nahr.a() && normal.b() < nahr.b()) before.add(normal);
            else if (normal.a() > nahr.a() && normal.b() > nahr.b()) after.add(normal);
        }

        Comparator<Boundary> order = Comparator.comparingInt(Boundary::a).thenComparingInt(Boundary::b);
        before.sort(order);
        after.sort(order);

        List<Boundary> result = new ArrayList<>();
        appendMonotonic(result, before);
        result.add(nahr);
        appendMonotonic(result, after);
        return new RecombinationEventPlan(result);
    }

    private static void appendMonotonic(List<Boundary> target, List<Boundary> candidates) {
        int lastA = target.isEmpty() ? -1 : target.getLast().a();
        int lastB = target.isEmpty() ? -1 : target.getLast().b();
        for (Boundary candidate : candidates) {
            if (candidate.a() > lastA && candidate.b() > lastB) {
                target.add(candidate);
                lastA = candidate.a();
                lastB = candidate.b();
            }
        }
    }

    List<Boundary> boundaries() {
        return boundaries;
    }
}
