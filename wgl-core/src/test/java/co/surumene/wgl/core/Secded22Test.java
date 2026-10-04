package co.surumene.wgl.core;

import co.surumene.wgl.api.GenomeAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Secded22Test {
    @Test
    void correctsEverySingleBitAndRejectsDoubleBitError() {
        GenomeAddress address = new GenomeAddress(0x7A, 0x35);
        BitHeader22 encoded = Secded22.encode(address);
        assertEquals(address, Secded22.decode(encoded).address());
        for (int bit = 0; bit < 22; bit++) {
            assertEquals(address, Secded22.decode(encoded.flip(bit)).address(), "bit=" + bit);
            assertTrue(Secded22.decode(encoded.flip(bit)).corrected());
        }
        assertFalse(Secded22.decode(encoded.flip(2).flip(11)).valid());
    }
}
