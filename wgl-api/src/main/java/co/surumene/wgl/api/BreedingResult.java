package co.surumene.wgl.api;

public sealed interface BreedingResult permits BreedingResult.Success, BreedingResult.NoViableOffspring {
    record Success(DiploidGenome genome, DecodeResult<?> decoded) implements BreedingResult { }
    record NoViableOffspring(BreedingFailureReason reason, String detail) implements BreedingResult { }
}
