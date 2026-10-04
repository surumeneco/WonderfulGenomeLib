package co.surumene.wgl.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NahrSupportTest {
    @Test
    void supportingAlternativeUsesConfiguredAnchorGap() {
        HomologyCandidate origin = new HomologyCandidate(100, 100, HomologyOrientation.FORWARD, 0);
        HomologyCandidate nearby = new HomologyCandidate(300, 300, HomologyOrientation.FORWARD, 0);

        assertFalse(NahrSupport.hasSupportingAlternative(origin, List.of(origin, nearby), 128));
        assertTrue(NahrSupport.hasSupportingAlternative(origin, List.of(origin, nearby), 256));
    }
}
