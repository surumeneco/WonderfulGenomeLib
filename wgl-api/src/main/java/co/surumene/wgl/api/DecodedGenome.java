package co.surumene.wgl.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DecodedGenome(
        Map<GenomeAddress, AddressAggregate> aggregates,
        List<DecodedGene> physicalGenes,
        List<DecodedHomologyBlock> homologyBlocks) {

    public DecodedGenome(Map<GenomeAddress, AddressAggregate> aggregates, List<DecodedGene> physicalGenes) {
        this(aggregates, physicalGenes, List.of());
    }

    public DecodedGenome {
        Objects.requireNonNull(aggregates, "aggregates");
        Objects.requireNonNull(physicalGenes, "physicalGenes");
        Objects.requireNonNull(homologyBlocks, "homologyBlocks");
        aggregates = Map.copyOf(aggregates);
        physicalGenes = List.copyOf(physicalGenes);
        homologyBlocks = List.copyOf(homologyBlocks);
    }

    public AddressAggregate aggregate(GenomeAddress address) {
        return aggregates.getOrDefault(address, AddressAggregate.empty());
    }
}
