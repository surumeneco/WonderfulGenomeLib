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
            if (seed.position() + 48 > templateBits.bitLength()) {
                throw new IllegalArgumentException("anchor outside template");
            }
        }
        if (markerLocus != null && (markerLocus.second().position() + 48 > templateBits.bitLength())) {
            throw new IllegalArgumentException("marker locus outside template");
        }
    }
}
