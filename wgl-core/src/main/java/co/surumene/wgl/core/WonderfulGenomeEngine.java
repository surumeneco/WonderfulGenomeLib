package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import java.util.Objects;

public final class WonderfulGenomeEngine implements GenomeEngine {
    public static final int ENGINE_REVISION=1;
    public static final int GENOME_FORMAT_VERSION=1;
    private final EngineConfig config;private final BinaryGenomeCodecV1 codec=new BinaryGenomeCodecV1();private final GenomeDecoderEngine decoder;private final GenomeSynthesizer synthesizer;private final BreedingEngine breeding;private final MarkerEngine marker=new MarkerEngine();private final GenomeSequenceCodec sequenceCodec=new GenomeSequenceCodecV1();
    private WonderfulGenomeEngine(EngineConfig config){this.config=Objects.requireNonNull(config);decoder=new GenomeDecoderEngine(config);synthesizer=new GenomeSynthesizer(config,decoder);breeding=new BreedingEngine(config,decoder);}
    public static WonderfulGenomeEngine create(EngineConfig config){return new WonderfulGenomeEngine(config);}
    public EngineConfig config(){return config;}
    @Override public GenomeRandom standardRandom(long seed){return new SplitMix64GenomeRandom(seed);}
    @Override public <P> DecodeResult<P> decode(GenomeProfile<P> profile,DiploidGenome genome){requireGenomeV1(genome);return decoder.decode(profile,genome);}
    @Override public SynthesisResult synthesize(GenomeProfile<?> profile,BackboneDefinition backbone,SynthesisTarget target,SynthesisContext context,GenomeRandom random){requireBackboneV1(backbone);return synthesizer.synthesize(profile,backbone,target,context,random);}
    @Override public CompatibilityReport assessCompatibility(DiploidGenome a,DiploidGenome b,CompatibilityPolicy policy){requireGenomeV1(a);requireGenomeV1(b);return (policy!=null?policy:new HomologyCompatibilityPolicyV1(config)).assess(a,b);}
    @Override public BreedingResult breed(GenomeProfile<?> profile,DiploidGenome a,DiploidGenome b,BreedingContext context,GenomeRandom random){requireGenomeV1(a);requireGenomeV1(b);requireBackboneV1(context.backbone());return breeding.breed(profile,a,b,context,random);}
    @Override public byte[] encode(DiploidGenome genome){requireGenomeV1(genome);return codec.encode(genome);}
    @Override public DiploidGenome decodeBinary(byte[] bytes){return codec.decode(bytes);}
    @Override public MarkerResult marker(BackboneDefinition backbone,DiploidGenome genome){requireBackboneV1(backbone);requireGenomeV1(genome);return marker.marker(backbone,genome);}
    @Override public MarkerResult marker(BackboneDefinition backbone,DiploidGenome genome,MarkerScheme scheme){requireBackboneV1(backbone);requireGenomeV1(genome);return Objects.requireNonNull(scheme).marker(backbone,genome);}
    @Override public GenomeSequenceCodec sequenceCodec(){return sequenceCodec;}

    private static void requireGenomeV1(DiploidGenome genome){
        Objects.requireNonNull(genome,"genome");
        requireFormatV1(genome.genomeFormatVersion());
    }
    private static void requireBackboneV1(BackboneDefinition backbone){
        Objects.requireNonNull(backbone,"backbone");
        requireFormatV1(backbone.genomeFormatVersion());
    }
    private static void requireFormatV1(int version){
        if(version!=GENOME_FORMAT_VERSION)throw new IllegalArgumentException("unsupported genome format version: "+version);
    }
}
