package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import java.util.*;

final class GenomeDecoderEngine {
    private static final double EPS=1.0e-12;
    private final EngineConfig config;
    private final PhysicalGenomeDecoder parser;
    GenomeDecoderEngine(EngineConfig config){this.config=config;this.parser=new PhysicalGenomeDecoder(config);}

    <P> DecodeResult<P> decode(GenomeProfile<P> profile,DiploidGenome genome){
        List<LocatedGene> genes=parseGenome(profile,genome);
        List<LocatedGene> direct=genes.stream().filter(g->g.gene.addressValid()&&!g.gene.regulation()).toList();
        List<LocatedGene> regulation=genes.stream().filter(g->g.gene.regulation()).toList();

        Map<LocatedGene,Double> dLocal=new IdentityHashMap<>();
        for(LocatedGene g:direct){
            DirectContributionModel model=profile.contributionModel(g.gene.address());
            double d0=model.baseEffect(g.gene.address(),g.gene.negative(),g.gene.magnitudeCode(),g.gene.expressionCode());
            double cis=cisMultiplier(g,regulation);
            dLocal.put(g,d0*cis);
        }

        Map<GenomeAddress,Double> conditionScores=conditionSnapshot(direct,dLocal);
        Map<GenomeAddress,Double> trans=transMultipliers(profile,regulation);
        Map<GenomeAddress,Double> epi=epistasisMultipliers(profile,regulation,conditionScores);

        List<RawContribution> raws=new ArrayList<>();
        for(LocatedGene g:direct){
            GenomeAddress address=g.gene.address();
            double d=dLocal.get(g)*trans.getOrDefault(address,1.0)*epi.getOrDefault(address,1.0);
            d=applyFinalClamp(d,dLocal.get(g));
            raws.add(new RawContribution(address,d,g.chromosome,g.haplotype,g.gene.startBit(),false));
        }
        raws.addAll(relayContributions(profile,genes,dLocal,trans,epi));

        Map<GenomeAddress,List<EffectiveContribution>> byAddress=new TreeMap<>();
        for(RawContribution r:raws){
            if(!profile.isDefinedAddress(r.address))continue;
            DirectContributionModel model=profile.contributionModel(r.address);
            double u=model.saturation(r.address,StrictMath.abs(r.effect));
            if(!Double.isFinite(u)||u<0||u>=1)throw new IllegalStateException("profile saturation must be finite in [0,1)");
            EffectiveContribution ec=new EffectiveContribution(r.address,r.effect,u,r.chromosome,r.haplotype,r.start,r.secondary);
            byAddress.computeIfAbsent(r.address,k->new ArrayList<>()).add(ec);
        }
        Map<GenomeAddress,AddressAggregate> aggregates=new TreeMap<>();
        for(var entry:byAddress.entrySet()) aggregates.put(entry.getKey(),aggregate(entry.getValue()));
        List<DecodedGene> physical=genes.stream().sorted(LocatedGene.ORDER).map(x->x.gene).toList();
        DecodedGenome decoded=new DecodedGenome(aggregates,physical);
        P phenotype=profile.mapPhenotype(decoded);
        return new DecodeResult<>(decoded,phenotype,new DecoderIdentity(WonderfulGenomeEngine.ENGINE_REVISION,config.decoderFingerprint(),profile.descriptor()));
    }

    private List<LocatedGene> parseGenome(GenomeProfile<?> profile,DiploidGenome genome){
        List<LocatedGene> out=new ArrayList<>();
        for(int c=0;c<genome.chromosomePairs().size();c++){
            ChromosomePair pair=genome.chromosomePairs().get(c);
            for(DecodedGene g:parser.parseChromosome(pair.haplotypeA(),profile))out.add(new LocatedGene(c,0,g.withGenomeLocation(c,0)));
            for(DecodedGene g:parser.parseChromosome(pair.haplotypeB(),profile))out.add(new LocatedGene(c,1,g.withGenomeLocation(c,1)));
        }
        out.sort(LocatedGene.ORDER);return out;
    }

    private double cisMultiplier(LocatedGene target,List<LocatedGene> regs){
        double mult=1.0;
        for(LocatedGene reg:regs){
            if(reg.chromosome!=target.chromosome||reg.haplotype!=target.haplotype)continue;
            int t=reg.gene.address().target(); if(t!=0x00&&t!=0x01)continue;
            int raw=reg.gene.rawEffectByte(); int radiusCode=(raw>>>4)&0xF, strength=raw&0xF;
            int radius=32*(radiusCode+1); int distance=StrictMath.abs(reg.gene.startBit()-target.gene.startBit());
            if(distance>radius)continue;
            double z=Math.min(1.0,distance/(double)radius);
            double attenuation=1-(3*z*z-2*z*z*z);
            double q=StrictMath.pow(strength/15.0,config.regulation().strengthExponent())*(reg.gene.expressionCode()/15.0)*attenuation;
            double transmission=insulatorTransmission(target,reg,regs);
            q*=transmission;
            if(t==0x00)mult*=1+(config.regulation().cisEnhancerMax()-1)*q;
            else mult*=1-(1-config.regulation().cisSilencerMin())*q;
        }
        return clamp(mult,config.regulation().finalMultiplierMin(),config.regulation().finalMultiplierMax());
    }

    private double insulatorTransmission(LocatedGene target,LocatedGene cis,List<LocatedGene> regs){
        int lo=Math.min(target.gene.startBit(),cis.gene.startBit()),hi=Math.max(target.gene.startBit(),cis.gene.startBit()); double transmission=1.0;
        for(LocatedGene ins:regs){
            if(ins.chromosome!=target.chromosome||ins.haplotype!=target.haplotype||ins.gene.address().target()!=0x07)continue;
            int p=ins.gene.startBit();if(p<=lo||p>=hi)continue;
            double q=StrictMath.pow(ins.gene.rawEffectByte()/255.0,config.regulation().strengthExponent())*(ins.gene.expressionCode()/15.0);
            transmission*=1-q;
        }
        return transmission;
    }

    private Map<GenomeAddress,Double> conditionSnapshot(List<LocatedGene> direct,Map<LocatedGene,Double>dLocal){
        Map<GenomeAddress,List<Double>> vals=new HashMap<>();
        for(LocatedGene g:direct)vals.computeIfAbsent(g.gene.address(),k->new ArrayList<>()).add(dLocal.get(g));
        Map<GenomeAddress,Double> out=new HashMap<>();
        for(var e:vals.entrySet()){
            double posSurvival=1,negSurvival=1;
            for(double d:e.getValue()){
                double u=-StrictMath.expm1(-StrictMath.abs(d));
                if(d>=0)posSurvival*=1-u; else negSurvival*=1-u;
            }
            out.put(e.getKey(),(1-posSurvival)*negSurvival);
        }
        return out;
    }

    private Map<GenomeAddress,Double> transMultipliers(GenomeProfile<?> profile,List<LocatedGene> regs){
        Map<GenomeAddress,Double> out=new HashMap<>();
        for(LocatedGene r:regs){int t=r.gene.address().target();if(t!=0x02&&t!=0x03)continue;
            GenomeAddress target=extensionAddress(r.gene.extension(),0,profile);if(target==null)continue;
            double q=StrictMath.pow(r.gene.rawEffectByte()/255.0,config.regulation().strengthExponent())*(r.gene.expressionCode()/15.0);
            double m=t==0x02?1+(config.regulation().transEnhancerMax()-1)*q:1-(1-config.regulation().transSilencerMin())*q;
            out.merge(target,m,(a,b)->a*b);
        }
        out.replaceAll((k,v)->clamp(v,config.regulation().finalMultiplierMin(),config.regulation().finalMultiplierMax()));
        return out;
    }

    private Map<GenomeAddress,Double> epistasisMultipliers(GenomeProfile<?> profile,List<LocatedGene> regs,Map<GenomeAddress,Double> scores){
        Map<GenomeAddress,Double> out=new HashMap<>();
        for(LocatedGene r:regs){int t=r.gene.address().target();if(t!=0x0C&&t!=0x0D)continue;BitSequence ext=r.gene.extension();if(ext.bitLength()<52)continue;
            GenomeAddress cond=extensionAddress(ext,0,profile),target=extensionAddress(ext,22,profile);if(cond==null||target==null)continue;
            boolean comparator=ext.bitAt(44);int rawThreshold=(int)ext.toLong(45,7);double threshold=rawThreshold/127.0;double value=scores.getOrDefault(cond,0.0);
            boolean ge=value>=threshold-EPS; boolean fires=comparator?!ge:ge;if(!fires)continue;
            double q=StrictMath.pow(r.gene.rawEffectByte()/255.0,config.regulation().strengthExponent())*(r.gene.expressionCode()/15.0);
            double m=t==0x0C?1+(config.regulation().epistasisEnhancerMax()-1)*q:1-(1-config.regulation().epistasisSilencerMin())*q;
            out.merge(target,m,(a,b)->a*b);
        }
        out.replaceAll((k,v)->clamp(v,config.regulation().finalMultiplierMin(),config.regulation().finalMultiplierMax()));return out;
    }

    private List<RawContribution> relayContributions(GenomeProfile<?> profile,List<LocatedGene> all,Map<LocatedGene,Double>dLocal,
                                                     Map<GenomeAddress,Double>trans,Map<GenomeAddress,Double>epi){
        List<RawContribution> out=new ArrayList<>();
        Map<String,List<LocatedGene>> lanes=new HashMap<>();for(LocatedGene g:all)lanes.computeIfAbsent(g.chromosome+":"+g.haplotype,k->new ArrayList<>()).add(g);
        for(List<LocatedGene> lane:lanes.values()){
            lane.sort(Comparator.comparingInt(x->x.gene.startBit()));
            for(int i=0;i<lane.size();i++){
                LocatedGene relay=lane.get(i);if(!relay.gene.regulation()||relay.gene.address().target()!=0x06)continue;
                LocatedGene source=null;
                if(relay.gene.orientation()==GeneOrientation.FORWARD){if(i>0)source=lane.get(i-1);} else {if(i+1<lane.size())source=lane.get(i+1);}
                if(source==null||source.gene.regulation()||!dLocal.containsKey(source))continue;
                GenomeAddress target=extensionAddress(relay.gene.extension(),0,profile);if(target==null)continue;
                double ratio=relay.gene.magnitudeCode()/127.0*(relay.gene.negative()?-1:1)*(relay.gene.expressionCode()/15.0);
                double base=dLocal.get(source)*ratio;double d=base*trans.getOrDefault(target,1.0)*epi.getOrDefault(target,1.0);d=applyFinalClamp(d,base);
                out.add(new RawContribution(target,d,relay.chromosome,relay.haplotype,relay.gene.startBit(),true));
            }
        }return out;
    }

    private GenomeAddress extensionAddress(BitSequence ext,int offset,GenomeProfile<?> profile){
        if(offset+22>ext.bitLength())return null;Secded22.Decoded d=Secded22.decode(new BitHeader22((int)ext.toLong(offset,22)));
        if(!d.valid())return null;GenomeAddress a=d.address();if(a.isRegulation()||a.target()==0xFF||!profile.isDefinedAddress(a))return null;return a;
    }
    private double applyFinalClamp(double adjusted,double local){
        if(local==0)return adjusted;double ratio=adjusted/local;ratio=clamp(ratio,config.regulation().finalMultiplierMin(),config.regulation().finalMultiplierMax());return local*ratio;
    }
    private static AddressAggregate aggregate(List<EffectiveContribution> contributions){
        double lp=0,ln=0;for(EffectiveContribution c:contributions){double u=c.saturation();if(c.effect()>=0)lp+=StrictMath.log1p(-u);else ln+=StrictMath.log1p(-u);}double p=-StrictMath.expm1(lp),n=StrictMath.exp(ln);return new AddressAggregate(p,n,p*n,contributions);
    }
    private static double clamp(double v,double min,double max){return Math.max(min,Math.min(max,v));}
    private record RawContribution(GenomeAddress address,double effect,int chromosome,int haplotype,int start,boolean secondary){}
    private record LocatedGene(int chromosome,int haplotype,DecodedGene gene){
        static final Comparator<LocatedGene> ORDER=Comparator.comparingInt(LocatedGene::chromosome).thenComparingInt(LocatedGene::haplotype).thenComparingInt(x->x.gene.startBit());
    }
}
