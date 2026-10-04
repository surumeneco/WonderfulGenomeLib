package co.surumene.wgl.api;

import java.util.List;
import java.util.Objects;

public record ChromosomeTemplate(BitSequence templateBits, List<AnchorSeed> anchors,
                                 MarkerLocus markerLocus,
                                 FounderScaffoldTolerance founderScaffoldTolerance) {
    public ChromosomeTemplate(BitSequence templateBits, List<AnchorSeed> anchors, MarkerLocus markerLocus) {
        this(templateBits, anchors, markerLocus, FounderScaffoldTolerance.exact());
    }

    public ChromosomeTemplate {
        Objects.requireNonNull(templateBits, "templateBits");
        Objects.requireNonNull(anchors, "anchors");
        Objects.requireNonNull(founderScaffoldTolerance, "founderScaffoldTolerance");
        anchors = List.copyOf(anchors);
        for (AnchorSeed seed : anchors) {
            validateSeed(templateBits, seed, "anchor");
        }
        if (markerLocus != null) {
            validateSeed(templateBits, markerLocus.first(), "marker locus");
            validateSeed(templateBits, markerLocus.second(), "marker locus");
        }
    }

    private static void validateSeed(BitSequence templateBits, AnchorSeed seed, String label) {
        if (seed.position() + 48 > templateBits.bitLength()) {
            throw new IllegalArgumentException(label + " outside template");
        }
        BitSequence actual = templateBits.slice(seed.position(), seed.position() + 48);
        if (!actual.equals(seed.canonicalBits())) {
            throw new IllegalArgumentException(label + " canonical bits do not match template window");
        }
    }
}
