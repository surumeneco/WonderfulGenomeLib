package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeneParserTest {
    @Test
    void parsesForwardReverseAndOneBitMotifVariantsWithoutMutatingGenome() {
        GenomeAddress address = new GenomeAddress(0x00, 0x02);
        BitSequence gene = GeneCodecV1.encode(address, false, 96, 13, BitSequence.empty());
        TestProfile profile = TestProfile.defining(address);
        PhysicalGenomeDecoder decoder = new PhysicalGenomeDecoder(EngineConfig.defaults());

        var forward = decoder.parseChromosome(gene, profile);
        assertEquals(1, forward.size());
        assertEquals(address, forward.getFirst().address());
        assertEquals(GeneOrientation.FORWARD, forward.getFirst().orientation());

        var reverse = decoder.parseChromosome(gene.reverse(), profile);
        assertEquals(1, reverse.size());
        assertEquals(address, reverse.getFirst().address());
        assertEquals(GeneOrientation.REVERSE, reverse.getFirst().orientation());

        BitSequence motifMutated = gene.flip(0);
        assertEquals(1, decoder.parseChromosome(motifMutated, profile).size());
        assertEquals(motifMutated, motifMutated);
    }
    @Test
    void formatV1ReservesSixteenBitExtensionForType02Loci() {
        GenomeAddress address = new GenomeAddress(0x02, 0x00);
        TestProfile profile = TestProfile.defining(address);
        PhysicalGenomeDecoder decoder = new PhysicalGenomeDecoder(EngineConfig.defaults());

        BitSequence requiredPrefix = GeneCodecV1.END;
        BitSequence gene = GeneCodecV1.encode(address, false, 64, 15, requiredPrefix);

        var decoded = decoder.parseChromosome(gene, profile);

        assertEquals(1, decoded.size());
        assertEquals(requiredPrefix, decoded.getFirst().extension());
        assertEquals(gene.bitLength(), decoded.getFirst().endBitExclusive());
    }

}
