package co.surumene.wgl.api;

public sealed interface SynthesisResult permits SynthesisResult.Success, SynthesisResult.Failure {
    record Success(DiploidGenome genome, DecodeResult<?> decoded) implements SynthesisResult { }
    record Failure(SynthesisFailureReason reason, String detail) implements SynthesisResult { }
}
