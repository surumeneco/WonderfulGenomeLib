package co.surumene.wgl.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StandardGenomeSafetyPolicyV1Test {
    private final StandardGenomeSafetyPolicyV1 policy = new StandardGenomeSafetyPolicyV1();

    @Test
    void acceptsExactChromosomeAndHaploidBoundaryRatios() {
        List<Integer> baseline = List.of(400, 400);

        assertTrue(policy.isSafe(List.of(100, 300), baseline));
        assertTrue(policy.isSafe(List.of(800, 800), baseline));
    }

    @Test
    void rejectsChromosomeOutsideQuarterToFourTimesBaseline() {
        List<Integer> baseline = List.of(400, 400);

        assertFalse(policy.isSafe(List.of(99, 301), baseline));
        assertFalse(policy.isSafe(List.of(1601, 400), baseline));
    }

    @Test
    void rejectsHaploidTotalOutsideHalfToTwiceBaseline() {
        List<Integer> baseline = List.of(400, 400, 400, 400);

        assertFalse(policy.isSafe(List.of(100, 100, 100, 100), baseline));
        assertFalse(policy.isSafe(List.of(801, 801, 801, 801), baseline));
    }

    @Test
    void rejectsMismatchedOrInvalidBaselines() {
        assertFalse(policy.isSafe(List.of(100), List.of(100, 100)));
        assertFalse(policy.isSafe(List.of(100), List.of(0)));
    }
}
