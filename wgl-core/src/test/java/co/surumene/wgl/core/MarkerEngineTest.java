package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkerEngineTest {
    @Test
    void markerIsInvariantToHaplotypeOrderingAndFallsBackDeterministically() {
        BackboneDefinition backbone = TestBackbones.singlePair(512);
        BitSequence a = TestSequences.patterned(512, 3);
        BitSequence b = TestSequences.patterned(511, 5);
        DiploidGenome ab = new DiploidGenome(1, List.of(new ChromosomePair(a, b)));
        DiploidGenome ba = new DiploidGenome(1, List.of(new ChromosomePair(b, a)));
        MarkerEngine engine = new MarkerEngine();
        assertEquals(engine.marker(backbone, ab), engine.marker(backbone, ba));
        assertEquals(engine.marker(backbone, ab), engine.marker(backbone, ab));
    }
}
