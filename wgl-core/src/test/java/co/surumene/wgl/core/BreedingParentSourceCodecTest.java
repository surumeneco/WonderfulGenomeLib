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
    void roundTripsZeroBitGameteChromosome() {
        BreedingParentSource source =
                new BreedingParentSource.Gamete(
                        new HaploidGenome(1, List.of(BitSequence.empty())));

        assertEquals(
                source,
                engine.decodeParentSource(engine.encodeParentSource(source)));
    }

    @Test
    void rejectsBadMagicUnsupportedContainerVersionAndTruncatedPayload() {
        BreedingParentSource source =
                new BreedingParentSource.Gamete(
                        new HaploidGenome(
                                1, List.of(BitSequence.fromBits("101"))));
        byte[] encoded = engine.encodeParentSource(source);

        byte[] badMagic = encoded.clone();
        badMagic[0] ^= 0x01;
        assertThrows(
                GenomeCodecException.class,
                () -> engine.decodeParentSource(badMagic));

        byte[] unsupportedContainer = encoded.clone();
        unsupportedContainer[4] = 2;
        assertThrows(
                GenomeCodecException.class,
                () -> engine.decodeParentSource(unsupportedContainer));

        byte[] truncated =
                java.util.Arrays.copyOf(encoded, encoded.length - 1);
        assertThrows(
                GenomeCodecException.class,
                () -> engine.decodeParentSource(truncated));
    }

    @Test
    void rejectsUnsupportedGameteGenomeFormatAndNonCanonicalTailBits() {
        BreedingParentSource source =
                new BreedingParentSource.Gamete(
                        new HaploidGenome(
                                1, List.of(BitSequence.fromBits("101"))));
        byte[] encoded = engine.encodeParentSource(source);

        byte[] unsupportedFormat = encoded.clone();
        unsupportedFormat[10] = 0;
        unsupportedFormat[11] = 2;
        assertThrows(
                GenomeCodecException.class,
                () -> engine.decodeParentSource(unsupportedFormat));

        byte[] nonCanonicalTail = encoded.clone();
        nonCanonicalTail[18] |= 0x01;
        assertThrows(
                GenomeCodecException.class,
                () -> engine.decodeParentSource(nonCanonicalTail));
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
