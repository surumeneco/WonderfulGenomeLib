package co.surumene.wgl.core;

import co.surumene.wgl.api.DecodedGene;
import co.surumene.wgl.api.GeneOrientation;
import co.surumene.wgl.api.GenomeAddress;
import co.surumene.wgl.api.GenomeProfile;

import java.util.*;

/** Implements transient de-novo-forbidden Address provenance checks during meiosis. */
final class ProvenanceGuard {
    private final GenomeProfile<?> profile;
    private final PhysicalGenomeDecoder parser;
    private final Set<GenomeAddress> forbidden;
    private final Set<HeaderSignature> legitimateHeaders;

    private ProvenanceGuard(GenomeProfile<?> profile, PhysicalGenomeDecoder parser, Set<GenomeAddress> forbidden,
                            Set<HeaderSignature> legitimateHeaders) {
        this.profile = profile;
        this.parser = parser;
        this.forbidden = Set.copyOf(forbidden);
        this.legitimateHeaders = Set.copyOf(legitimateHeaders);
    }

    static ProvenanceGuard capture(GenomeProfile<?> profile, PhysicalGenomeDecoder parser,
                                   Set<GenomeAddress> forbidden, List<TrackedSequence> sourceChromatids) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(parser, "parser");
        Set<GenomeAddress> safeForbidden = Set.copyOf(forbidden == null ? Set.of() : forbidden);
        if (safeForbidden.isEmpty()) return new ProvenanceGuard(profile, parser, safeForbidden, Set.of());

        Set<HeaderSignature> legitimate = new HashSet<>();
        for (TrackedSequence source : sourceChromatids) {
            for (DecodedGene gene : parser.parseChromosome(source.bits(), profile)) {
                if (!gene.addressValid() || !safeForbidden.contains(gene.address())) continue;
                HeaderSignature signature = signature(source, gene);
                if (signature != null) legitimate.add(signature);
            }
        }
        return new ProvenanceGuard(profile, parser, safeForbidden, legitimate);
    }

    boolean valid(List<TrackedSequence> chromosomes) {
        if (forbidden.isEmpty()) return true;
        for (TrackedSequence chromosome : chromosomes) {
            for (DecodedGene gene : parser.parseChromosome(chromosome.bits(), profile)) {
                if (!gene.addressValid() || !forbidden.contains(gene.address())) continue;
                HeaderSignature signature = signature(chromosome, gene);
                if (signature == null || !legitimateHeaders.contains(signature)) return false;
            }
        }
        return true;
    }

    private static HeaderSignature signature(TrackedSequence sequence, DecodedGene gene) {
        int[] physical = headerPhysicalIndices(gene);
        if (physical == null) return null;
        int lane = Integer.MIN_VALUE;
        int[] sourcePositions = new int[22];
        for (int i = 0; i < physical.length; i++) {
            int p = physical[i];
            if (p < 0 || p >= sequence.bitLength()) return null;
            TrackedSequence.Origin origin = sequence.originAt(p);
            if (origin.isFresh()) return null;
            if (i == 0) lane = origin.lane();
            else if (lane != origin.lane()) return null;
            sourcePositions[i] = origin.sourceBit();
        }
        return new HeaderSignature(lane, sourcePositions);
    }

    private static int[] headerPhysicalIndices(DecodedGene gene) {
        int[] positions = new int[22];
        if (gene.orientation() == GeneOrientation.FORWARD) {
            for (int i = 0; i < 22; i++) positions[i] = gene.startBit() + 16 + i;
        } else {
            for (int i = 0; i < 22; i++) positions[i] = gene.endBitExclusive() - 17 - i;
        }
        return positions;
    }

    private static final class HeaderSignature {
        private final int lane;
        private final int[] sourcePositions;

        HeaderSignature(int lane, int[] sourcePositions) {
            this.lane = lane;
            this.sourcePositions = sourcePositions.clone();
        }

        @Override public boolean equals(Object o) {
            return o instanceof HeaderSignature that && lane == that.lane
                    && Arrays.equals(sourcePositions, that.sourcePositions);
        }

        @Override public int hashCode() {
            return 31 * lane + Arrays.hashCode(sourcePositions);
        }
    }
}
