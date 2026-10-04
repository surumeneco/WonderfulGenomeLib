package co.surumene.wgl.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecombinationEventPlanTest {
    @Test
    void nahrCanCoexistWithNormalCrossoversAtDifferentMonotonicPositions() {
        RecombinationEventPlan.Boundary nahr = new RecombinationEventPlan.Boundary(500, 700, true);
        List<RecombinationEventPlan.Boundary> normal = List.of(
                new RecombinationEventPlan.Boundary(200, 200, false),
                new RecombinationEventPlan.Boundary(800, 1000, false),
                new RecombinationEventPlan.Boundary(600, 400, false),
                new RecombinationEventPlan.Boundary(500, 700, false));

        RecombinationEventPlan plan = RecombinationEventPlan.withNahr(nahr, normal);

        assertEquals(List.of(
                new RecombinationEventPlan.Boundary(200, 200, false),
                nahr,
                new RecombinationEventPlan.Boundary(800, 1000, false)), plan.boundaries());
    }

    @Test
    void onePhysicalRecombinationBoundaryCannotBeBothNormalAndNahr() {
        RecombinationEventPlan.Boundary nahr = new RecombinationEventPlan.Boundary(300, 420, true);
        RecombinationEventPlan plan = RecombinationEventPlan.withNahr(nahr, List.of(
                new RecombinationEventPlan.Boundary(300, 420, false)));

        assertEquals(List.of(nahr), plan.boundaries());
    }
    @Test
    void nahrDoesNotConsumeTheMandatoryNormalCrossoverEvent() {
        EngineConfig config = EngineConfig.defaults();

        int normalCount = RecombinationEventPlan.normalCrossoverTargetCount(
                4096.0,
                config.recombination(),
                new SplitMix64GenomeRandom(123L));

        assertEquals(1, normalCount);
    }

}
