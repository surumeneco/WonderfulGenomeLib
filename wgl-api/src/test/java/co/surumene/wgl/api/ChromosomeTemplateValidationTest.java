package co.surumene.wgl.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChromosomeTemplateValidationTest {
    @Test
    void anchorSeedMustMatchTemplateWindow() {
        BitSequence bits = BitSequence.fromBits("01".repeat(192));
        AnchorSeed mismatched = new AnchorSeed(0, bits.slice(0, 48).flip(3));

        assertThrows(IllegalArgumentException.class,
                () -> new ChromosomeTemplate(bits, List.of(mismatched), null));
    }

    @Test
    void markerLocusSeedsMustMatchTemplateWindows() {
        BitSequence bits = BitSequence.fromBits("0011".repeat(96));
        AnchorSeed first = new AnchorSeed(64, bits.slice(64, 112));
        AnchorSeed second = new AnchorSeed(192, bits.slice(192, 240).flip(7));

        assertThrows(IllegalArgumentException.class,
                () -> new ChromosomeTemplate(bits, List.of(), new MarkerLocus(first, second)));
    }

    @Test
    void matchingSeedsRemainValid() {
        BitSequence bits = BitSequence.fromBits("0110".repeat(96));
        AnchorSeed first = new AnchorSeed(64, bits.slice(64, 112));
        AnchorSeed second = new AnchorSeed(192, bits.slice(192, 240));

        assertDoesNotThrow(() -> new ChromosomeTemplate(
                bits, List.of(first, second), new MarkerLocus(first, second)));
    }
}
