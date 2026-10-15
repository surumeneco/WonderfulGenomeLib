package co.surumene.wgl.api;

import java.util.Objects;

public record AnchorSeed(int position, BitSequence canonicalBits) {
    public AnchorSeed {
        if (position < 0) throw new IllegalArgumentException("position must be >= 0");
        Objects.requireNonNull(canonicalBits, "canonicalBits");
        if (canonicalBits.bitLength() != 48) throw new IllegalArgumentException("anchor must be 48 bits");
    }
}
