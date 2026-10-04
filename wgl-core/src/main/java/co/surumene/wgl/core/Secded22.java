package co.surumene.wgl.core;

import co.surumene.wgl.api.GenomeAddress;

public final class Secded22 {
    private static final int[] DATA_POS={3,5,6,7,9,10,11,12,13,14,15,17,18,19,20,21};
    private Secded22(){}
    public record Decoded(GenomeAddress address, boolean valid, boolean corrected, int correctedPosition) {}

    public static BitHeader22 encode(GenomeAddress address) {
        boolean[] p=new boolean[23];
        int v=address.unsignedShort();
        for(int i=0;i<16;i++) p[DATA_POS[i]] = ((v >>> i)&1)!=0;
        for(int parity=1;parity<=16;parity<<=1){ boolean x=false; for(int pos=1;pos<=21;pos++) if(pos!=parity && (pos&parity)!=0) x^=p[pos]; p[parity]=x; }
        boolean overall=false; for(int pos=1;pos<=21;pos++) overall^=p[pos]; p[22]=overall;
        int bits=0; for(int pos=1;pos<=22;pos++) if(p[pos]) bits|=1<<(22-pos);
        return new BitHeader22(bits);
    }

    public static Decoded decode(BitHeader22 header) {
        boolean[] p=new boolean[23]; for(int pos=1;pos<=22;pos++) p[pos]=header.bit(pos-1);
        int syndrome=0;
        for(int parity=1;parity<=16;parity<<=1){ boolean x=false; for(int pos=1;pos<=21;pos++) if((pos&parity)!=0)x^=p[pos]; if(x) syndrome|=parity; }
        boolean overall=false; for(int pos=1;pos<=22;pos++) overall^=p[pos];
        boolean corrected=false; int correctedPosition=0;
        if(syndrome==0 && overall) { p[22]=!p[22]; corrected=true; correctedPosition=22; }
        else if(syndrome!=0 && overall) {
            if(syndrome<1||syndrome>21) return new Decoded(null,false,false,0);
            p[syndrome]=!p[syndrome]; corrected=true; correctedPosition=syndrome;
        } else if(syndrome!=0) return new Decoded(null,false,false,0);
        int value=0; for(int i=0;i<16;i++) if(p[DATA_POS[i]]) value|=1<<i;
        return new Decoded(GenomeAddress.fromUnsignedShort(value),true,corrected,correctedPosition);
    }
}
