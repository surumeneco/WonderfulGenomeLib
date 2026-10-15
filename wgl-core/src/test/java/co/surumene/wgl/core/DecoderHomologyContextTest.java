package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DecoderHomologyContextTest {
    @Test
    void suppliesPhysicalHomologyBlocksOnlyWhenProfileRequestsThem() {
        BitSequence haplotype = TestSequences.patterned(600, 0x4D4F4646L);
        DiploidGenome genome = new DiploidGenome(
                1,
                List.of(new ChromosomePair(haplotype, haplotype)));

        GenomeProfile<DecodedGenome> requesting = new ContextProfile(true);
        DecodedGenome withContext = WonderfulGenomeEngine.create(EngineConfig.defaults())
                .decode(requesting, genome)
                .decodedGenome();

        assertFalse(withContext.homologyBlocks().isEmpty());
        assertTrue(withContext.homologyBlocks().stream().allMatch(block ->
                block.chromosomeIndex() == 0
                        && block.startA() >= 0
                        && block.endAExclusive() <= haplotype.bitLength()
                        && block.startB() >= 0
                        && block.endBExclusive() <= haplotype.bitLength()));

        DecodedGenome withoutContext = WonderfulGenomeEngine.create(EngineConfig.defaults())
                .decode(new ContextProfile(false), genome)
                .decodedGenome();

        assertTrue(withoutContext.homologyBlocks().isEmpty());
    }

    private static final class ContextProfile implements GenomeProfile<DecodedGenome> {
        private final boolean requiresHomology;

        private ContextProfile(boolean requiresHomology) {
            this.requiresHomology = requiresHomology;
        }

        @Override
        public ProfileDescriptor descriptor() {
            return new ProfileDescriptor("homology-context-test", 1, new byte[32]);
        }

        @Override
        public boolean isDefinedAddress(GenomeAddress address) {
            return false;
        }

        @Override
        public boolean requiresHomologyContext() {
            return requiresHomology;
        }

        @Override
        public DirectContributionModel contributionModel(GenomeAddress address) {
            throw new IllegalArgumentException("no direct addresses");
        }

        @Override
        public DecodedGenome mapPhenotype(DecodedGenome decodedGenome) {
            return decodedGenome;
        }
    }
}
