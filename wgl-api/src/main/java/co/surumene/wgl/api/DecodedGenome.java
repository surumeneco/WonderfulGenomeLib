package co.surumene.wgl.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DecodedGenome(Map<GenomeAddress, AddressAggregate> aggregates, List<DecodedGene> physicalGenes) {
    public DecodedGenome {
        Objects.requireNonNull(aggregates, "aggregates");
        Objects.requireNonNull(physicalGenes, "physicalGenes");
        aggregates = Map.copyOf(aggregates);
        physicalGenes = List.copyOf(physicalGenes);
    }

    public AddressAggregate aggregate(GenomeAddress address) {
        return aggregates.getOrDefault(address, AddressAggregate.empty());
    }
}
