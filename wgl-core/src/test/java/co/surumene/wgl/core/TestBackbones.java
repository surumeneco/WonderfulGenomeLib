package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import java.util.*;

final class TestBackbones {
    private TestBackbones(){}
    static BackboneDefinition singlePair(int length){
        if(length<384)throw new IllegalArgumentException("test backbone must be >=384 bits");
        BitSequence bits=TestSequences.patterned(length,0x51A7E11L);
        int first=Math.max(32,length/2-96),second=Math.min(length-48,first+144);
        AnchorSeed a=new AnchorSeed(first,bits.slice(first,first+48));AnchorSeed b=new AnchorSeed(second,bits.slice(second,second+48));
        List<AnchorSeed> anchors=new ArrayList<>();for(int p=0;p+48<=length;p+=128)anchors.add(new AnchorSeed(p,bits.slice(p,p+48)));
        ChromosomeTemplate chromosome=new ChromosomeTemplate(bits,anchors,new MarkerLocus(a,b));
        return new BackboneDefinition("test-backbone",1,List.of(chromosome),new StandardGenomeSafetyPolicyV1());
    }
}
