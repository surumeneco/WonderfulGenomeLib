package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;

final class TestSequences {
    private TestSequences(){}
    static BitSequence patterned(int length,long seed){
        SplitMix64GenomeRandom random=new SplitMix64GenomeRandom(seed);StringBuilder s=new StringBuilder(length);
        long word=0;int left=0;for(int i=0;i<length;i++){if(left==0){word=random.nextLong();left=64;}s.append((word&1L)!=0?'1':'0');word>>>=1;left--;}
        return BitSequence.fromBits(s.toString());
    }
}
