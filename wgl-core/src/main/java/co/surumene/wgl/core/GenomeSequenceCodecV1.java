package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import java.util.Locale;

public final class GenomeSequenceCodecV1 implements GenomeSequenceCodec {
    @Override public BitSequence decodeBits(String bits){return BitSequence.fromBits(bits);}
    @Override public BitSequence decodeHex(String hex){
        if(hex==null||!hex.matches("(?i)[0-9a-f]*"))throw new IllegalArgumentException("hex must contain only 0-9/A-F");
        StringBuilder b=new StringBuilder(hex.length()*4);for(int i=0;i<hex.length();i++){int v=Character.digit(hex.charAt(i),16);b.append(String.format(Locale.ROOT,"%4s",Integer.toBinaryString(v)).replace(' ','0'));}return BitSequence.fromBits(b.toString());
    }
    @Override public BitSequence decodeDna(String dna){
        if(dna==null)throw new IllegalArgumentException("dna must not be null");StringBuilder b=new StringBuilder(dna.length()*2);
        for(int i=0;i<dna.length();i++)b.append(switch(Character.toUpperCase(dna.charAt(i))){case 'T'->"00";case 'C'->"01";case 'G'->"10";case 'A'->"11";default->throw new IllegalArgumentException("dna must contain only T/C/G/A");});
        return BitSequence.fromBits(b.toString());
    }
    @Override public String encodeBits(BitSequence bits){return bits.toBitString();}
    @Override public TextView encodeHex(BitSequence bits){int units=bits.bitLength()/4;StringBuilder text=new StringBuilder(units);for(int i=0;i<units;i++)text.append(Integer.toHexString((int)bits.toLong(i*4,4)).toUpperCase(Locale.ROOT));return new TextView(text.toString(),bits.slice(units*4,bits.bitLength()).toBitString());}
    @Override public TextView encodeDna(BitSequence bits){int units=bits.bitLength()/2;StringBuilder text=new StringBuilder(units);for(int i=0;i<units;i++)text.append(switch((int)bits.toLong(i*2,2)){case 0->'T';case 1->'C';case 2->'G';case 3->'A';default->throw new AssertionError();});return new TextView(text.toString(),bits.slice(units*2,bits.bitLength()).toBitString());}
}
