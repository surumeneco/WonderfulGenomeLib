package co.surumene.wgl.api;

import java.util.Objects;

public record ChromosomePair(BitSequence haplotypeA, BitSequence haplotypeB) {
    public ChromosomePair {
        Objects.requireNonNull(haplotypeA, "haplotypeA");
        Objects.requireNonNull(haplotypeB, "haplotypeB");
    }
}
