package co.surumene.wgl.api;

import java.util.Objects;
import java.util.Set;

public record BreedingContext(BackboneDefinition backbone, double mutationRateMultiplier,
                              Set<GenomeAddress> deNovoForbiddenAddresses,
                              CompatibilityPolicy compatibilityPolicy,
                              boolean allowSafetyOverride,
                              ParentMeiosisPolicy parentAPolicy,
                              ParentMeiosisPolicy parentBPolicy) {
    public BreedingContext {
        Objects.requireNonNull(backbone, "backbone");
        if (!Double.isFinite(mutationRateMultiplier) || mutationRateMultiplier < 0) throw new IllegalArgumentException("invalid mutation multiplier");
        deNovoForbiddenAddresses = Set.copyOf(deNovoForbiddenAddresses == null ? Set.of() : deNovoForbiddenAddresses);
        parentAPolicy = parentAPolicy == null ? ParentMeiosisPolicy.none() : parentAPolicy;
        parentBPolicy = parentBPolicy == null ? ParentMeiosisPolicy.none() : parentBPolicy;
    }

    /** Backward-compatible constructor for contexts without physical inheritance preferences. */
    public BreedingContext(BackboneDefinition backbone, double mutationRateMultiplier,
                           Set<GenomeAddress> deNovoForbiddenAddresses,
                           CompatibilityPolicy compatibilityPolicy,
                           boolean allowSafetyOverride) {
        this(backbone, mutationRateMultiplier, deNovoForbiddenAddresses, compatibilityPolicy,
                allowSafetyOverride, ParentMeiosisPolicy.none(), ParentMeiosisPolicy.none());
    }

    public static BreedingContext standard(BackboneDefinition backbone) {
        return new BreedingContext(backbone, 1.0, Set.of(), null, false,
                ParentMeiosisPolicy.none(), ParentMeiosisPolicy.none());
    }
}
