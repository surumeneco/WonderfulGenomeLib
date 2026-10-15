package co.surumene.wgl.api;

import java.util.List;

@FunctionalInterface
public interface GenomeSafetyPolicy {
    boolean isSafe(List<Integer> chromosomeLengths, List<Integer> baselineChromosomeLengths);
}
