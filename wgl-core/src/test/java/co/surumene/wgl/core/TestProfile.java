package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import java.util.*;

final class TestProfile implements GenomeProfile<DecodedGenome> {
    private final Set<GenomeAddress> addresses;
    private TestProfile(Set<GenomeAddress> addresses){this.addresses=Set.copyOf(addresses);}
    static TestProfile defining(GenomeAddress... addresses){return new TestProfile(Set.of(addresses));}
    @Override public ProfileDescriptor descriptor(){return new ProfileDescriptor("test",1,new byte[32]);}
    @Override public boolean isDefinedAddress(GenomeAddress address){return addresses.contains(address);}
    @Override public DirectContributionModel contributionModel(GenomeAddress address){if(!isDefinedAddress(address))throw new IllegalArgumentException("undefined address");return StandardDirectContributionModel.defaultModel();}
    @Override public DecodedGenome mapPhenotype(DecodedGenome decodedGenome){return decodedGenome;}
}
