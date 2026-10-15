package co.surumene.wgl.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SplitMix64RandomTest {
    @Test
    void matchesGoldenSequence() {
        SplitMix64GenomeRandom random = new SplitMix64GenomeRandom(0L);
        assertEquals(0xE220A8397B1DCDAFL, random.nextLong());
        assertEquals(0x6E789E6AA1B965F4L, random.nextLong());
        assertEquals(0x06C45D188009454FL, random.nextLong());
        assertEquals(0xF88BB8A8724C81ECL, random.nextLong());
        assertEquals(0x1B39896A51A8749BL, random.nextLong());
    }

    @Test
    void boundedIntegersStayInRangeAndAreDeterministic() {
        SplitMix64GenomeRandom a = new SplitMix64GenomeRandom(42L);
        SplitMix64GenomeRandom b = new SplitMix64GenomeRandom(42L);
        for (int i = 0; i < 1000; i++) {
            int av = a.nextInt(7);
            int bv = b.nextInt(7);
            assertEquals(av, bv);
            assertTrue(av >= 0 && av < 7);
        }
    }
}
