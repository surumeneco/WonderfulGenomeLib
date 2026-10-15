package co.surumene.wgl.core;
final class GrayCode {
    private GrayCode(){}
    static int decode7(int gray){ int n=gray&0x7F; for(int shift=1;shift<7;shift<<=1)n^=n>>>shift; return n&0x7F; }
    static int encode7(int binary){ if(binary<0||binary>127)throw new IllegalArgumentException(); return binary^(binary>>>1); }
}
