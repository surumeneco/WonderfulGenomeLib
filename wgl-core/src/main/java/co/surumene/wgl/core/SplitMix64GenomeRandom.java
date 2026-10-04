package co.surumene.wgl.core;

import co.surumene.wgl.api.GenomeRandom;

public final class SplitMix64GenomeRandom implements GenomeRandom {
    private long state;
    public SplitMix64GenomeRandom(long seed){this.state=seed;}
    @Override public long nextLong(){
        long z=(state += 0x9E3779B97F4A7C15L);
        z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;
        z=(z^(z>>>27))*0x94D049BB133111EBL;
        return z^(z>>>31);
    }
    @Override public double nextDouble(){ return (nextLong()>>>11)*0x1.0p-53; }
    @Override public boolean nextBoolean(){ return (nextLong()&1L)!=0; }
    @Override public int nextInt(int bound){
        if(bound<=0)throw new IllegalArgumentException("bound must be > 0");
        long m=Integer.toUnsignedLong(bound);
        long threshold=Long.remainderUnsigned(-m,m);
        while(true){ long x=nextLong()>>>1; long r=x%m; if(x-r+(m-1)>=0) return (int)r; }
    }
}
