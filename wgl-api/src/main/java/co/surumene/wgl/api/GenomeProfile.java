package co.surumene.wgl.api;

/** Immutable, thread-safe consumer profile. */
public interface GenomeProfile<P> {
    ProfileDescriptor descriptor();
    boolean isDefinedAddress(GenomeAddress address);

    /** Required extension prefix length for a profile-defined address. */
    default int minimumExtensionBits(GenomeAddress address) { return 0; }

    DirectContributionModel contributionModel(GenomeAddress address);

    /**
     * Map one consumer continuous target into generic positive/negative bounded contribution targets.
     * The default preserves WGL's standard P * N synthesis behavior.
     */
    default SynthesisAddressPlan synthesisPlan(GenomeAddress address, double target,
                                               SynthesisContext context, GenomeRandom random) {
        return SynthesisAddressPlan.boundedProduct(target, context, random);
    }

    /**
     * Supply additional physical Founder blocks for profile-specific latent/discrete targets,
     * regulation structures, silent/incomplete genes, or other synthesis material.
     * WGL owns physical placement and final canonical Decoder convergence.
     */
    default java.util.List<SynthesisBlock> synthesisBlocks(SynthesisTarget target,
                                                           SynthesisContext context,
                                                           GenomeRandom random) {
        return java.util.List.of();
    }

    /**
     * Build the profile-owned extension payload for a synthesized direct gene.
     * Implementations must return between minimumExtensionBits(address) and 64 bits.
     */
    default BitSequence synthesisExtension(GenomeAddress address, SynthesisTarget target, GenomeRandom random) {
        return BitSequence.empty();
    }

    /** Convert generic decoded physical contributions into consumer phenotype data. */
    P mapPhenotype(DecodedGenome decodedGenome);
}
