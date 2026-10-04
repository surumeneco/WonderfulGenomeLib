package co.surumene.wgl.api;

import java.util.Objects;

public record MarkerLocus(AnchorSeed first, AnchorSeed second) {
    public MarkerLocus {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.position() >= second.position()) throw new IllegalArgumentException("marker anchors must be ordered");
    }
}
