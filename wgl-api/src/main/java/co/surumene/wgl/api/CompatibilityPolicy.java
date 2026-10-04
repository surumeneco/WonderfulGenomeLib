package co.surumene.wgl.api;

@FunctionalInterface
public interface CompatibilityPolicy {
    CompatibilityReport assess(DiploidGenome parentA, DiploidGenome parentB);
}
