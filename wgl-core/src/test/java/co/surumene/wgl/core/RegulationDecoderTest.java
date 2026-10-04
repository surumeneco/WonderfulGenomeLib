package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegulationDecoderTest {
    private static final double ALPHA = -StrictMath.log(0.70);

    @Test
    void transEnhancerAppliesConfiguredMultiplierBeforeSaturation() {
        GenomeAddress target = new GenomeAddress(0x00, 0x00);
        TestProfile profile = TestProfile.defining(target);
        BitSequence direct = GeneCodecV1.encode(target, false, 127, 15, BitSequence.empty());
        BitSequence extension = BitSequence.fromBits(Secded22.encode(target).toBitString());
        BitSequence trans = GeneCodecV1.encodeRawEffect(new GenomeAddress(0x08, 0x02), 0xFF, 15, extension);

        AddressAggregate aggregate = decode(profile, direct.concat(trans)).aggregate(target);
        assertEquals(1.0 - StrictMath.exp(-ALPHA * 1.25), aggregate.score(), 1e-12);
    }

    @Test
    void finalRegulationClampAppliesOnceToCombinedCisTransAndEpistasisMultiplier() {
        GenomeAddress target = new GenomeAddress(0x00, 0x00);
        TestProfile profile = TestProfile.defining(target);

        BitSequence direct = GeneCodecV1.encode(target, false, 127, 15, BitSequence.empty());
        BitSequence cis = GeneCodecV1.encodeRawEffect(
                new GenomeAddress(0x08, 0x00), 0xFF, 15, BitSequence.empty());
        BitSequence targetHeader = BitSequence.fromBits(Secded22.encode(target).toBitString());
        BitSequence trans = GeneCodecV1.encodeRawEffect(
                new GenomeAddress(0x08, 0x02), 0xFF, 15, targetHeader);
        BitSequence epiExtension = targetHeader
                .concat(targetHeader)
                .concat(BitSequence.fromBits("0"))
                .concat(BitSequence.fromLong(0, 7));
        BitSequence epistasis = GeneCodecV1.encodeRawEffect(
                new GenomeAddress(0x08, 0x0C), 0xFF, 15, epiExtension);

        AddressAggregate aggregate = decode(profile,
                direct.concat(cis).concat(trans).concat(epistasis)).aggregate(target);

        assertEquals(1.0 - StrictMath.exp(-ALPHA * 3.0), aggregate.score(), 1e-12);
    }

    @Test
    void relayUsesPreviousDirectLocalContributionWithoutRecursion() {
        GenomeAddress source = new GenomeAddress(0x00, 0x00);
        GenomeAddress target = new GenomeAddress(0x00, 0x01);
        TestProfile profile = TestProfile.defining(source, target);
        BitSequence direct = GeneCodecV1.encode(source, false, 127, 15, BitSequence.empty());
        BitSequence extension = BitSequence.fromBits(Secded22.encode(target).toBitString());
        BitSequence relay = GeneCodecV1.encode(new GenomeAddress(0x08, 0x06), false, 127, 15, extension);

        DecodedGenome decoded = decode(profile, direct.concat(relay));
        assertEquals(0.30, decoded.aggregate(source).score(), 1e-12);
        assertEquals(0.30, decoded.aggregate(target).score(), 1e-12);
        assertTrue(decoded.aggregate(target).contributions().getFirst().secondary());
    }

    @Test
    void epistasisUsesFrozenLocalConditionSnapshot() {
        GenomeAddress condition = new GenomeAddress(0x00, 0x00);
        GenomeAddress target = new GenomeAddress(0x00, 0x01);
        TestProfile profile = TestProfile.defining(condition, target);
        BitSequence conditionGene = GeneCodecV1.encode(condition, false, 127, 15, BitSequence.empty());
        BitSequence targetGene = GeneCodecV1.encode(target, false, 127, 15, BitSequence.empty());
        BitSequence extension = BitSequence.fromBits(Secded22.encode(condition).toBitString())
                .concat(BitSequence.fromBits(Secded22.encode(target).toBitString()))
                .concat(BitSequence.fromBits("0")) // >= comparator
                .concat(BitSequence.fromLong(32, 7));
        BitSequence epi = GeneCodecV1.encodeRawEffect(new GenomeAddress(0x08, 0x0C), 0xFF, 15, extension);

        DecodedGenome decoded = decode(profile, conditionGene.concat(targetGene).concat(epi));
        assertEquals(0.30, decoded.aggregate(condition).score(), 1e-12);
        assertEquals(1.0 - StrictMath.exp(-ALPHA * 1.75), decoded.aggregate(target).score(), 1e-12);
    }

    private static DecodedGenome decode(TestProfile profile, BitSequence haplotype) {
        DiploidGenome genome = new DiploidGenome(1,
                List.of(new ChromosomePair(haplotype, BitSequence.empty())));
        return WonderfulGenomeEngine.create(EngineConfig.defaults()).decode(profile, genome).decodedGenome();
    }
}
