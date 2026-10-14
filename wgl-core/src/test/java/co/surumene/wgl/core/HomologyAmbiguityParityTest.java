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
            int firstB = -1;
            int firstA = -1;
            boolean ambiguousA = false;
            boolean ambiguousB = false;
            for (HomologyCandidate other : candidates) {
                if (other.orientation() != current.orientation()) continue;
                if (other.positionA() == current.positionA()) {
                    if (firstB < 0) firstB = other.positionB();
                    else if (other.positionB() != firstB) ambiguousA = true;
                }
                if (other.positionB() == current.positionB()) {
                    if (firstA < 0) firstA = other.positionA();
                    else if (other.positionA() != firstA) ambiguousB = true;
                }
            }
            if (ambiguousA || ambiguousB) out.add(current);
        }
        return List.copyOf(out);
    }
}
