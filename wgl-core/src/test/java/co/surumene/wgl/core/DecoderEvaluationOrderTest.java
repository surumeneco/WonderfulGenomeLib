package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DecoderEvaluationOrderTest {
    @Test
    void directAndRelayContributionsAreAggregatedInPhysicalStartOrder() {
        GenomeAddress source = new GenomeAddress(0x00, 0x00);
        GenomeAddress target = new GenomeAddress(0x00, 0x01);
        TestProfile profile = TestProfile.defining(source, target);

        BitSequence sourceGene = GeneCodecV1.encode(source, false, 127, 15, BitSequence.empty());
        BitSequence targetHeader = BitSequence.fromBits(Secded22.encode(target).toBitString());
        BitSequence relay = GeneCodecV1.encode(
                new GenomeAddress(0x08, 0x06), false, 127, 15, targetHeader);
        BitSequence targetGene = GeneCodecV1.encode(target, false, 64, 15, BitSequence.empty());

        BitSequence haplotype = sourceGene.concat(relay).concat(targetGene);
        DiploidGenome genome = new DiploidGenome(1,
                List.of(new ChromosomePair(haplotype, BitSequence.empty())));

        AddressAggregate aggregate = WonderfulGenomeEngine.create(EngineConfig.defaults())
                .decode(profile, genome)
                .decodedGenome()
                .aggregate(target);

        assertEquals(List.of(sourceGene.bitLength(), sourceGene.bitLength() + relay.bitLength()),
                aggregate.contributions().stream().map(EffectiveContribution::startBit).toList());
        assertTrue(aggregate.contributions().getFirst().secondary());
        assertFalse(aggregate.contributions().getLast().secondary());
    }
}
