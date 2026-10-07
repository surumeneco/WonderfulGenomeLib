package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class BreedingParentSourceCodecTest {
    private final WonderfulGenomeEngine engine =
            WonderfulGenomeEngine.create(EngineConfig.defaults());

    @Test
    void roundTripsDiploidAndGameteWithoutLosingSourceTypeOrBits() {
        DiploidGenome diploid = new DiploidGenome(1, List.of(
                new ChromosomePair(
                        BitSequence.fromBits("10101"),
                        BitSequence.fromBits("001"))));
        HaploidGenome haploid = new HaploidGenome(1, List.of(
                BitSequence.fromBits("11100"),
                BitSequence.fromBits("1")));

        BreedingParentSource diploidSource =
                new BreedingParentSource.DiploidParent(diploid);
        BreedingParentSource gameteSource =
                new BreedingParentSource.Gamete(haploid);

        assertEquals(
                diploidSource,
                engine.decodeParentSource(engine.encodeParentSource(diploidSource)));
        assertEquals(
                gameteSource,
                engine.decodeParentSource(engine.encodeParentSource(gameteSource)));
    }

    @Test
    void rejectsTrailingBytesAndUnknownSourceType() {
        HaploidGenome haploid = new HaploidGenome(
                1, List.of(BitSequence.fromBits("101")));
        byte[] encoded = engine.encodeParentSource(
                new BreedingParentSource.Gamete(haploid));

        byte[] trailing = java.util.Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(GenomeCodecException.class, () -> engine.decodeParentSource(trailing));

        byte[] unknownType = encoded.clone();
        unknownType[5] = (byte) 99;
        assertThrows(GenomeCodecException.class, () -> engine.decodeParentSource(unknownType));
    }
}
