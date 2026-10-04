package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import java.util.*;

public final class HomologyCompatibilityPolicyV1 implements CompatibilityPolicy {
    private final HomologyEngine homology;
    public HomologyCompatibilityPolicyV1(EngineConfig config){homology=new HomologyEngine(config);}
    @Override public CompatibilityReport assess(DiploidGenome a,DiploidGenome b){
        Objects.requireNonNull(a,"a");Objects.requireNonNull(b,"b");
        if(a.genomeFormatVersion()!=WonderfulGenomeEngine.GENOME_FORMAT_VERSION
                || b.genomeFormatVersion()!=WonderfulGenomeEngine.GENOME_FORMAT_VERSION)
            throw new IllegalArgumentException("HomologyCompatibilityPolicyV1 supports Genome Format V1 only");
        if(a.chromosomePairCount()!=b.chromosomePairCount())return new CompatibilityReport(false,"CHROMOSOME_COUNT_MISMATCH",List.of());
        List<Boolean> flags=new ArrayList<>();boolean all=true;
        for(int i=0;i<a.chromosomePairCount();i++){
            ChromosomePair x=a.chromosomePairs().get(i),y=b.chromosomePairs().get(i);boolean ok=false;
            for(BitSequence hx:List.of(x.haplotypeA(),x.haplotypeB()))for(BitSequence hy:List.of(y.haplotypeA(),y.haplotypeB()))if(!homology.analyze(hx,hy).blocks().isEmpty())ok=true;
            flags.add(ok);all&=ok;
        }
        return new CompatibilityReport(all,all?"COMPATIBLE":"INSUFFICIENT_CROSS_PARENT_HOMOLOGY",flags);
    }
}
