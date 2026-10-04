package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;

public final class MarkerEngine implements MarkerScheme {
    @Override public MarkerResult marker(BackboneDefinition backbone,DiploidGenome genome){
        Objects.requireNonNull(backbone);Objects.requireNonNull(genome);
        if(backbone.genomeFormatVersion()!=WonderfulGenomeEngine.GENOME_FORMAT_VERSION
                || genome.genomeFormatVersion()!=WonderfulGenomeEngine.GENOME_FORMAT_VERSION)
            throw new IllegalArgumentException("MarkerEngine V1 supports Genome Format V1 only");
        if(genome.chromosomePairCount()!=backbone.chromosomes().size())throw new IllegalArgumentException("chromosome count does not match backbone");
        List<Integer> segments=new ArrayList<>();
        for(int i=0;i<genome.chromosomePairCount();i++){
            ChromosomeTemplate t=backbone.chromosomes().get(i);ChromosomePair p=genome.chromosomePairs().get(i);
            int a=fingerprint(i,p.haplotypeA(),t.markerLocus()),b=fingerprint(i,p.haplotypeB(),t.markerLocus());
            segments.add((Math.min(a,b)<<4)|Math.max(a,b));
        }
        return new MarkerResult(segments);
    }
    private int fingerprint(int chromosome,BitSequence seq,MarkerLocus locus){
        if(locus!=null){BitSequence material=resolveLocus(seq,locus);if(material!=null)return digestNibble(material.packedBits());}
        ByteBuffer buf=ByteBuffer.allocate(2+4+seq.packedBits().length);buf.putShort((short)chromosome).putInt(seq.bitLength()).put(seq.packedBits());return digestNibble(buf.array());
    }
    private BitSequence resolveLocus(BitSequence seq,MarkerLocus locus){
        List<Hit> first=findHits(seq,locus.first().canonicalBits()),second=findHits(seq,locus.second().canonicalBits());
        int canonicalGap=locus.second().position()-locus.first().position();Pair best=null;
        for(Hit a:first)for(Hit b:second){if(a.reverse!=b.reverse)continue;boolean ordered=a.reverse?a.position>b.position:a.position<b.position;if(!ordered)continue;
            int physicalGap=Math.abs(b.position-a.position);Pair p=new Pair(a,b,a.hamming+b.hamming,Math.abs(physicalGap-canonicalGap),Math.min(a.position,b.position));if(best==null||p.compareTo(best)<0)best=p;}
        if(best==null)return null;
        BitSequence x=seq.slice(best.a.position,best.a.position+48),y=seq.slice(best.b.position,best.b.position+48);if(best.a.reverse){x=x.reverse();y=y.reverse();}
        return x.concat(y);
    }
    private List<Hit> findHits(BitSequence seq,BitSequence anchor){List<Hit> out=new ArrayList<>();if(seq.bitLength()<48)return out;for(int i=0;i<=seq.bitLength()-48;i++){BitSequence w=seq.slice(i,i+48);int f=w.hammingDistance(anchor);if(f<=2)out.add(new Hit(i,false,f));int r=w.reverse().hammingDistance(anchor);if(r<=2)out.add(new Hit(i,true,r));}return out;}
    private static int digestNibble(byte[] data){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(data);return (d[0]>>>4)&0xF;}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private record Hit(int position,boolean reverse,int hamming){}
    private record Pair(Hit a,Hit b,int hamming,int gapError,int position) implements Comparable<Pair>{public int compareTo(Pair o){int c=Integer.compare(hamming,o.hamming);if(c!=0)return c;c=Integer.compare(gapError,o.gapError);if(c!=0)return c;return Integer.compare(position,o.position);}}
}
