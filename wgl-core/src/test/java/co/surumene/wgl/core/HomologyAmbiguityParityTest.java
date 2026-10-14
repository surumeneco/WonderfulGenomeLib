package co.surumene.wgl.core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Independent exhaustive ambiguity oracle: only distinct counterpart
 * positions count, grouped by the exact source position and orientation.
 */
final class HomologyAmbiguityParityTest {
    @Test void preservesAmbiguityAndInputOrderWithDuplicatesAndMixedOrientations()
            throws Exception {
        Random random = new Random(20261014L);
        for (int iteration = 0; iteration < 80; iteration++) {
            List<HomologyCandidate> candidates = new ArrayList<>();
            int size = 10 + random.nextInt(150);
            for (int i = 0; i < size; i++) {
                candidates.add(new HomologyCandidate(
                        random.nextInt(iteration % 2 == 0 ? 5 : 34),
                        random.nextInt(iteration % 3 == 0 ? 6 : 48),
                        random.nextBoolean() ? HomologyOrientation.FORWARD
                                : HomologyOrientation.REVERSE,
                        random.nextInt(3)));
            }
            assertEquals(exhaustive(candidates), invoke(candidates),
                    "iteration " + iteration);
        }
        List<HomologyCandidate> repeated = List.of(
                new HomologyCandidate(0, 1, HomologyOrientation.FORWARD, 0),
                new HomologyCandidate(0, 1, HomologyOrientation.FORWARD, 1),
                new HomologyCandidate(0, 2, HomologyOrientation.REVERSE, 0),
                new HomologyCandidate(2, 1, HomologyOrientation.REVERSE, 0));
        assertEquals(exhaustive(repeated), invoke(repeated));
    }

    @SuppressWarnings("unchecked")
    private static List<HomologyCandidate> invoke(List<HomologyCandidate> candidates)
            throws Exception {
        Method m = HomologyEngine.class.getDeclaredMethod("findAmbiguous", List.class);
        m.setAccessible(true);
        return (List<HomologyCandidate>) m.invoke(null, candidates);
    }

    private static List<HomologyCandidate> exhaustive(List<HomologyCandidate> candidates) {
        List<HomologyCandidate> out = new ArrayList<>();
        for (HomologyCandidate current : candidates) {
            boolean ambiguousA = false;
            boolean ambiguousB = false;
            for (HomologyCandidate one : candidates) {
                if (one.orientation() != current.orientation()) continue;
                for (HomologyCandidate two : candidates) {
                    if (two.orientation() != current.orientation()) continue;
                    if (one.positionA() == current.positionA()
                            && two.positionA() == current.positionA()
                            && one.positionB() != two.positionB()) ambiguousA = true;
                    if (one.positionB() == current.positionB()
                            && two.positionB() == current.positionB()
                            && one.positionA() != two.positionA()) ambiguousB = true;
                }
            }
            if (ambiguousA || ambiguousB) out.add(current);
        }
        return List.copyOf(out);
    }
}
