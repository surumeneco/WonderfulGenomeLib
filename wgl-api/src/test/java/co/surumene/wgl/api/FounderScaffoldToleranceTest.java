package co.surumene.wgl.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FounderScaffoldToleranceTest {
    @Test
    void exactToleranceIsExplicitAndImmutable() {
        FounderScaffoldTolerance exact = FounderScaffoldTolerance.exact();

        assertEquals(0.0, exact.standardDeviationRatio());
        assertEquals(1.0, exact.minLengthRatio());
        assertEquals(1.0, exact.maxLengthRatio());
    }

    @Test
    void validatesLengthDistributionContract() {
        assertThrows(IllegalArgumentException.class,
                () -> new FounderScaffoldTolerance(-0.01, 0.9, 1.1));
        assertThrows(IllegalArgumentException.class,
                () -> new FounderScaffoldTolerance(0.05, 0.0, 1.1));
        assertThrows(IllegalArgumentException.class,
                () -> new FounderScaffoldTolerance(0.05, 1.01, 1.1));
        assertThrows(IllegalArgumentException.class,
                () -> new FounderScaffoldTolerance(0.05, 0.9, 0.99));
        assertThrows(IllegalArgumentException.class,
                () -> new FounderScaffoldTolerance(0.0, 0.9, 1.1));
    }
}
