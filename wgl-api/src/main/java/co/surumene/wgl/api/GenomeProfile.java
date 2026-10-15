package co.surumene.wgl.api;

/** Immutable, thread-safe consumer profile. */
public interface GenomeProfile<P> {
    ProfileDescriptor descriptor();
    boolean isDefinedAddress(GenomeAddress address);

    /** Required extension prefix length for a profile-defined address. */
    default int minimumExtensionBits(GenomeAddress address) { return 0; }

    DirectContributionModel contributionModel(GenomeAddress address);

    /**
     * Optionally vary the same-length physical Founder template before WGL performs
     * scaffold length adjustment and generated-material placement. The default keeps
     * the canonical Backbone template unchanged. Consumers may use this for
     * profile-owned Founder anchor/marker variation, but must not change bit length.
     */
    default BitSequence founderTemplateBits(
            int chromosomeIndex,
            int haplotypeIndex,
            ChromosomeTemplate template,
            GenomeRandom random) {
        return template.templateBits();
    }

    /**
     * Whether decode should attach physical homologous-block coordinates to DecodedGenome.
     * Profiles that do not need homology context keep this disabled to avoid the analysis cost.
     */
    default boolean requiresHomologyContext() { return false; }

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
     * Optional Founder-only structural safety policy. Physical chromosome length safety remains
     * the BackboneDefinition GenomeSafetyPolicy responsibility.
     */
    default SynthesisSafetyPolicy synthesisSafetyPolicy() {
        return SynthesisSafetyPolicy.allowAll();
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
