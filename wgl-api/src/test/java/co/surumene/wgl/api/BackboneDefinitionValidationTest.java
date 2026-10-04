package co.surumene.wgl.api;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackboneDefinitionValidationTest {
    @Test
    void rejectsChromosomeCountAboveDiploidGenomeFormatLimit() {
        ChromosomeTemplate template = new ChromosomeTemplate(
                BitSequence.empty(), List.of(), null);

        assertThrows(IllegalArgumentException.class, () -> new BackboneDefinition(
                "too-many-chromosomes",
                1,
                Collections.nCopies(0x10000, template),
                new StandardGenomeSafetyPolicyV1()));
    }
}
