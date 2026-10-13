package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Same Genome/phenotype, even when one chromosome is edited between decoding attempts. */
final class SynthesisHomologyCacheTest {
    private static final GenomeProfile<DecodedGenome> PROFILE = new GenomeProfile<>() {
        @Override public ProfileDescriptor descriptor() {
            return new ProfileDescriptor("homology-cache-test", 1, new byte[32]);
        }
        @Override public boolean isDefinedAddress(GenomeAddress address) { return false; }
        @Override public boolean requiresHomologyContext() { return true; }
        @Override public DirectContributionModel contributionModel(GenomeAddress address) {
            throw new IllegalArgumentException("no genes defined");
        }
        @Override public DecodedGenome mapPhenotype(DecodedGenome decoded) { return decoded; }
    };

    @Test
    void reusesUnmodifiedChromosomePairsButRecomputesChangedOnes() {
        GenomeDecoderEngine decoder = new GenomeDecoderEngine(EngineConfig.defaults());
        GenomeDecoderEngine.HomologyCache cache = new GenomeDecoderEngine.HomologyCache();
        BitSequence first = TestSequences.patterned(640, 0x1234L);
        BitSequence second = TestSequences.patterned(640, 0x5678L);
        DiploidGenome original = new DiploidGenome(1, List.of(
                new ChromosomePair(first, first),
                new ChromosomePair(second, second)));

        assertEquivalent(decoder.decode(PROFILE, original),
                decoder.decode(PROFILE, original, cache));
        assertEquals(2, cache.computedPairs());
        assertEquals(0, cache.reusedPairs());

        // New immutable objects with identical physical bits must be reused.
        DiploidGenome equivalent = new DiploidGenome(1, List.of(
                new ChromosomePair(BitSequence.ofPacked(first.packedBits(), first.bitLength()),
                        BitSequence.ofPacked(first.packedBits(), first.bitLength())),
                new ChromosomePair(BitSequence.ofPacked(second.packedBits(), second.bitLength()),
                        BitSequence.ofPacked(second.packedBits(), second.bitLength()))));
        assertEquivalent(decoder.decode(PROFILE, equivalent),
                decoder.decode(PROFILE, equivalent, cache));
        assertEquals(2, cache.computedPairs());
        assertEquals(2, cache.reusedPairs());

        DiploidGenome edited = new DiploidGenome(1, List.of(
                new ChromosomePair(first.flip(123), first),
                new ChromosomePair(second, second)));
        assertEquivalent(decoder.decode(PROFILE, edited),
                decoder.decode(PROFILE, edited, cache));
        assertEquals(3, cache.computedPairs());
        assertEquals(3, cache.reusedPairs());

        // Returning to an old value after a different edit is a miss in the
        // deliberately single-slot-per-chromosome bounded cache.
        assertEquivalent(decoder.decode(PROFILE, original),
                decoder.decode(PROFILE, original, cache));
        assertEquals(4, cache.computedPairs());
        assertEquals(4, cache.reusedPairs());
    }

    private static void assertEquivalent(DecodeResult<?> expected, DecodeResult<?> actual) {
        assertEquals(expected.identity(), actual.identity());
        assertEquals(expected.decodedGenome().homologyBlocks(), actual.decodedGenome().homologyBlocks());
        assertEquals(expected.decodedGenome().aggregates(), actual.decodedGenome().aggregates());
        assertEquals(expected.decodedGenome().physicalGenes(), actual.decodedGenome().physicalGenes());
    }
}
