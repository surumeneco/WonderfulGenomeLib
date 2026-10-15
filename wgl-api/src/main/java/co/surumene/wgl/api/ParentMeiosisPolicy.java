package co.surumene.wgl.api;

import java.util.Comparator;
import java.util.List;

/** Immutable per-parent physical meiosis policy. */
public record ParentMeiosisPolicy(List<InheritanceConstraint> inheritanceConstraints) {
    public ParentMeiosisPolicy {
        inheritanceConstraints = List.copyOf(inheritanceConstraints == null ? List.of() : inheritanceConstraints);
        inheritanceConstraints = inheritanceConstraints.stream()
                .sorted(Comparator.comparingInt(InheritanceConstraint::chromosomeIndex)
                        .thenComparingInt(InheritanceConstraint::startBit)
                        .thenComparingInt(InheritanceConstraint::haplotypeIndex))
                .toList();
    }

    public static ParentMeiosisPolicy none() { return new ParentMeiosisPolicy(List.of()); }
}
