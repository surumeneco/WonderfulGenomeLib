package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import java.util.*;

final class TestBackbones {
    private TestBackbones(){}

    static BackboneDefinition singlePair(int length){
        if(length<384)throw new IllegalArgumentException("test backbone must be >=384 bits");
        BitSequence bits=TestSequences.patterned(length,0x51A7E11L);
        List<AnchorSeed> anchors=new ArrayList<>();
        for(int p=0;p+48<=length;p+=128) {
            anchors.add(new AnchorSeed(p,bits.slice(p,p+48)));
        }
        if(anchors.size()<2)throw new IllegalArgumentException("test backbone needs at least two anchors");

        int best=0;
        double bestDistance=Double.POSITIVE_INFINITY;
        for(int i=0;i<anchors.size()-1;i++){
            double center=(anchors.get(i).position()+anchors.get(i+1).position()+48)/2.0;
            double distance=StrictMath.abs(center-length/2.0);
            if(distance<bestDistance){
                bestDistance=distance;
                best=i;
            }
        }
        MarkerLocus marker=new MarkerLocus(anchors.get(best),anchors.get(best+1));
        ChromosomeTemplate chromosome=new ChromosomeTemplate(bits,anchors,marker);
        return new BackboneDefinition("test-backbone",1,List.of(chromosome),new StandardGenomeSafetyPolicyV1());
    }
}
