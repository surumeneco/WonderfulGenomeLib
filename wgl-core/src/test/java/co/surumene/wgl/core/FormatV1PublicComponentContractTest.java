package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FormatV1PublicComponentContractTest {
    @Test
    void markerEngineRejectsUnsupportedGenomeAndBackboneVersions() {
        BackboneDefinition v1 = TestBackbones.singlePair(512);
        BackboneDefinition v2 = new BackboneDefinition(
                "marker-v2",
                2,
                v1.chromosomes(),
                v1.safetyPolicy());
        BitSequence bits = v1.chromosomes().getFirst().templateBits();
        DiploidGenome genomeV1 = new DiploidGenome(1,
                List.of(new ChromosomePair(bits, bits)));
        DiploidGenome genomeV2 = new DiploidGenome(2,
                List.of(new ChromosomePair(bits, bits)));

        MarkerEngine marker = new MarkerEngine();

        assertThrows(IllegalArgumentException.class, () -> marker.marker(v1, genomeV2));
        assertThrows(IllegalArgumentException.class, () -> marker.marker(v2, genomeV1));
    }

    @Test
    void geneCodecRejectsExtensionsLongerThanFormatV1Maximum() {
        GenomeAddress address = new GenomeAddress(0x00, 0x00);
        BitSequence tooLong = BitSequence.fromBits("0".repeat(65));

        assertThrows(IllegalArgumentException.class,
                () -> GeneCodecV1.encode(address, false, 1, 1, tooLong));
        assertThrows(IllegalArgumentException.class,
                () -> GeneCodecV1.encodeRawEffect(address, 1, 1, tooLong));
    }
}
