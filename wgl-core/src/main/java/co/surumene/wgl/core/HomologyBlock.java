package co.surumene.wgl.core;
import java.util.*;
public record HomologyBlock(java.util.List<HomologyCandidate> anchors) {
    public HomologyBlock { anchors=List.copyOf(anchors); if(anchors.size()<2)throw new IllegalArgumentException("block needs >=2 anchors"); }
    public int physicalLength(){ HomologyCandidate f=anchors.getFirst(),l=anchors.getLast(); return Math.min(l.positionA()-f.positionA()+48,l.positionB()-f.positionB()+48); }
    public int startA(){return anchors.getFirst().positionA();} public int endA(){return anchors.getLast().positionA()+48;}
    public int startB(){return anchors.getFirst().positionB();} public int endB(){return anchors.getLast().positionB()+48;}
}
