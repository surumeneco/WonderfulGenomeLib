package co.surumene.wgl.api;

@FunctionalInterface
public interface SynthesisSafetyPolicy {
    boolean isSafe(SynthesisMetrics metrics, DecodedGenome decodedGenome);

    static SynthesisSafetyPolicy allowAll() {
        return (metrics, decodedGenome) -> true;
    }
}
