package co.surumene.wgl.api;

@FunctionalInterface
public interface CompatibilityPolicy {
    CompatibilityReport assess(
            BreedingParentSource parentA,
            BreedingParentSource parentB);

    default CompatibilityReport assess(
            DiploidGenome parentA,
            DiploidGenome parentB) {
        return assess(
                new BreedingParentSource.DiploidParent(parentA),
                new BreedingParentSource.DiploidParent(parentB));
    }
}
