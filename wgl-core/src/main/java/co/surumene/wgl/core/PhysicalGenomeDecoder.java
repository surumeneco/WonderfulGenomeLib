package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import java.util.*;

public final class PhysicalGenomeDecoder {
    private final EngineConfig config;
    public PhysicalGenomeDecoder(EngineConfig config){this.config=Objects.requireNonNull(config);}

    public List<DecodedGene> parseChromosome(BitSequence sequence, GenomeProfile<?> profile){
        Objects.requireNonNull(sequence); Objects.requireNonNull(profile);
        List<DecodedGene> all=new ArrayList<>();
        all.addAll(parseForward(sequence,profile,GeneOrientation.FORWARD));
        BitSequence reversed=sequence.reverse();
        for(DecodedGene g:parseForward(reversed,profile,GeneOrientation.REVERSE)){
            int mappedStart=sequence.bitLength()-g.endBitExclusive();
            int mappedEnd=sequence.bitLength()-g.startBit();
            all.add(new DecodedGene(g.address(),g.addressValid(),g.addressCorrected(),g.negative(),g.magnitudeCode(),
                    g.rawEffectByte(),g.expressionCode(),g.extension(),-1,-1,mappedStart,mappedEnd,GeneOrientation.REVERSE,g.motifHammingDistance()));
        }
        Map<Range,DecodedGene> best=new HashMap<>();
        for(DecodedGene g:all){
            Range r=new Range(g.startBit(),g.endBitExclusive());
            DecodedGene old=best.get(r);
            if(old==null || compareCandidate(g,old)<0) best.put(r,g);
        }
        List<DecodedGene> out=new ArrayList<>(best.values());
        out.sort(Comparator.comparingInt(DecodedGene::startBit).thenComparing(g->g.orientation()==GeneOrientation.FORWARD?0:1));
        return List.copyOf(out);
    }

    private List<DecodedGene> parseForward(BitSequence seq, GenomeProfile<?> profile, GeneOrientation orientation){
        List<DecodedGene> out=new ArrayList<>();
        int i=0;
        while(i+66<=seq.bitLength()){
            int startHam=hammingWindow(seq,i,GeneCodecV1.START);
            if(startHam>1){i++;continue;}
            int core=i+16;
            if(core+34+16>seq.bitLength()){i++;continue;}
            BitHeader22 header=new BitHeader22((int)seq.toLong(core,22));
            Secded22.Decoded decoded=Secded22.decode(header);
            GenomeAddress address=decoded.valid()?decoded.address():null;
            boolean meaningful=decoded.valid() && isKnownAddress(address,profile);
            int minExt=meaningful?minimumExtension(address,profile):0;
            int selectedExt=-1, endHam=0;
            for(int ext=minExt;ext<=64;ext++){
                int endStart=core+34+ext;
                if(endStart+16>seq.bitLength())break;
                int h=hammingWindow(seq,endStart,GeneCodecV1.END);
                if(h<=1){selectedExt=ext;endHam=h;break;}
            }
            if(selectedExt<0){i++;continue;}
            int raw=(int)seq.toLong(core+22,8);
            int expr=(int)seq.toLong(core+30,4);
            boolean negative=(raw&0x80)!=0;
            int magnitude=GrayCode.decode7(raw&0x7F);
            int end=core+34+selectedExt+16;
            BitSequence extension=seq.slice(core+34,core+34+selectedExt);
            out.add(new DecodedGene(address,meaningful,decoded.corrected(),negative,magnitude,raw,expr,extension,-1,-1,i,end,orientation,startHam+endHam));
            i=end; // selected physical candidate owns its internal sequence
        }
        return out;
    }

    private static boolean isKnownAddress(GenomeAddress a,GenomeProfile<?> profile){
        if(a==null||a.target()==0xFF||a.type()==0xFF)return false;
        if(a.type()==0x08)return a.target()<=0x0D;
        return profile.isDefinedAddress(a);
    }
    private static int minimumExtension(GenomeAddress a,GenomeProfile<?> profile){
        if(a.type()==0x08){return switch(a.target()){case 0x02,0x03,0x06->22;case 0x0C,0x0D->52;default->0;};}
        return Math.max(0,Math.min(64,profile.minimumExtensionBits(a)));
    }
    private static int hammingWindow(BitSequence seq,int offset,BitSequence motif){
        if(offset<0||offset+motif.bitLength()>seq.bitLength())return Integer.MAX_VALUE;
        int d=0;for(int j=0;j<motif.bitLength();j++){if(seq.bitAt(offset+j)!=motif.bitAt(j)&&++d>1)return d;}return d;
    }
    private static int compareCandidate(DecodedGene a,DecodedGene b){
        int c=Integer.compare(a.motifHammingDistance(),b.motifHammingDistance());
        if(c!=0)return c;
        return Integer.compare(a.orientation()==GeneOrientation.FORWARD?0:1,b.orientation()==GeneOrientation.FORWARD?0:1);
    }
    private record Range(int start,int end){}
}
