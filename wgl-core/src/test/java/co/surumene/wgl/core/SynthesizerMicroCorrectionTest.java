package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SynthesizerMicroCorrectionTest {
    @Test
    void refinesAnAlreadySatisfiedContinuousTargetWithASeparateMicroGene() {
        GenomeAddress address = new GenomeAddress(0x00, 0x05);
        double targetScore = 0.5002434945452243;

        GenomeProfile<Double> profile = new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor("micro-correction", 1, new byte[32]);
            }

            @Override public boolean isDefinedAddress(GenomeAddress candidate) {
                return address.equals(candidate);
            }

            @Override public DirectContributionModel contributionModel(GenomeAddress candidate) {
                return StandardDirectContributionModel.defaultModel();
            }

            @Override public SynthesisAddressPlan synthesisPlan(
                    GenomeAddress candidate, double target,
                    SynthesisContext context, GenomeRandom random) {
                return new SynthesisAddressPlan(targetScore, 0.0, 2, 2, 0, 0);
            }

            @Override public Double mapPhenotype(DecodedGenome decodedGenome) {
                return decodedGenome.aggregate(address).score();
            }
        };

        ChromosomeTemplate template = new ChromosomeTemplate(
                BitSequence.fromBits("0".repeat(2048)), List.of(), null);
        BackboneDefinition backbone = new BackboneDefinition(
                "micro-correction-backbone",
                1,
                List.of(template),
                new StandardGenomeSafetyPolicyV1());

        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
        SynthesisResult.Success success = assertInstanceOf(
                SynthesisResult.Success.class,
                engine.synthesize(
                        profile,
                        backbone,
                        new BasicSynthesisTarget(Map.of(address, targetScore)),
                        new SynthesisContext(2, 2, 0.0, 0.0, 1),
                        new SplitMix64GenomeRandom(2026100401L)));

        AddressAggregate aggregate = success.decoded().decodedGenome().aggregate(address);
        assertTrue(StrictMath.abs(aggregate.score() - targetScore) < 0.0005,
                "micro-correction should improve the already-valid residual");

        long targetGenes = success.decoded().decodedGenome().physicalGenes().stream()
                .filter(gene -> address.equals(gene.address()))
                .count();
        assertTrue(targetGenes >= 3, "micro-correction must remain a normal physical gene");
        assertTrue(success.decoded().decodedGenome().physicalGenes().stream()
                        .filter(gene -> address.equals(gene.address()))
                        .anyMatch(gene -> gene.expressionCode() < 15),
                "micro-correction should use finer Expression resolution when needed");
    }
}
