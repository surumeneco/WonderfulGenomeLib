package co.surumene.wgl.api;

import java.util.Objects;

public record DecodeResult<P>(DecodedGenome decodedGenome, P phenotype, DecoderIdentity identity) {
    public DecodeResult {
        Objects.requireNonNull(decodedGenome, "decodedGenome");
        Objects.requireNonNull(identity, "identity");
    }
}
