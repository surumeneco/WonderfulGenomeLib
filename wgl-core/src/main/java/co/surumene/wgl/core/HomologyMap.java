package co.surumene.wgl.core;
import java.util.*;
public record HomologyMap(List<HomologyCandidate> candidates,List<HomologyBlock> blocks,List<HomologyCandidate> ambiguousCandidates){
    public HomologyMap { candidates=List.copyOf(candidates);blocks=List.copyOf(blocks);ambiguousCandidates=List.copyOf(ambiguousCandidates); }
}
