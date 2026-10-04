package co.surumene.wgl.api;

/** Immutable, thread-safe consumer profile. */
public interface GenomeProfile<P> {
    ProfileDescriptor descriptor();
    boolean isDefinedAddress(GenomeAddress address);

    /** Required extension prefix length for a profile-defined address. */
    default int minimumExtensionBits(GenomeAddress address) { return 0; }

    DirectContributionModel contributionModel(GenomeAddress address);

    /** Convert generic decoded physical contributions into consumer phenotype data. */
    P mapPhenotype(DecodedGenome decodedGenome);
}
