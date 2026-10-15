package co.surumene.wgl.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record EngineConfig(Homology homology, Recombination recombination, Mutation mutation,
                           Regulation regulation, LocalRates localRates, Synthesizer synthesizer,
                           int eventRetryMax) {
    public record Homology(int maxAnchorGapBits, int qLocalWindowBits, double qLocalDecay) {}
    public record Recombination(int extraCrossoverStartBits, int extraCrossoverScaleBits, int interferenceDistanceBits) {}
    public record Mutation(double pointPerBitProbability, Structural structural, Nahr nahr) {}
    public record Structural(double insertionProbability, double deletionProbability, double duplicationProbability,
                             double inversionProbability, double translocationProbability,
                             double insertionRandomSequenceRatio, double duplicationSameChromosomeRatio,
                             double translocationReciprocalRatio, double translocationOtherChromosomeRatio,
                             double insertionLengthP, int insertionLengthMaxBits,
                             double deletionLengthP, int deletionLengthMaxBits,
                             double duplicationLengthP, int duplicationLengthMaxBits,
                             double inversionLengthP, int inversionLengthMaxBits,
                             double translocationLengthP, int translocationLengthMaxBits) {}
    public record Nahr(double baseProbability, double structureMultiplierMax) {}
    public record Regulation(double finalMultiplierMin, double finalMultiplierMax, double strengthExponent,
                             double cisEnhancerMax, double cisSilencerMin, double transEnhancerMax,
                             double transSilencerMin, double epistasisEnhancerMax, double epistasisSilencerMin) {}
    public record RateBand(double hotspotMax, double coldspotMin, double finalMin, double finalMax) {}
    public record LocalRates(RateBand recombination, RateBand point, RateBand structural) {}
    public record Synthesizer(double convergenceTolerance, int localAdjustmentMaxIterations, double localAdjustmentMaxContributionRatio) {}

    public EngineConfig {
        if (homology == null || recombination == null || mutation == null || regulation == null || localRates == null || synthesizer == null) {
            throw new IllegalArgumentException("engine config sections must not be null");
        }
        validatePositive(homology.maxAnchorGapBits, "homology.maxAnchorGapBits");
        validatePositive(homology.qLocalWindowBits, "homology.qLocalWindowBits");
        validateFinitePositive(homology.qLocalDecay, "homology.qLocalDecay");
        if (recombination.extraCrossoverStartBits < 0) throw new IllegalArgumentException("recombination start must be >= 0");
        validatePositive(recombination.extraCrossoverScaleBits, "recombination scale");
        validatePositive(recombination.interferenceDistanceBits, "interference distance");
        probability(mutation.pointPerBitProbability, "point mutation probability");
        Structural s = mutation.structural;
        probability(s.insertionProbability, "insertion probability");
        probability(s.deletionProbability, "deletion probability");
        probability(s.duplicationProbability, "duplication probability");
        probability(s.inversionProbability, "inversion probability");
        probability(s.translocationProbability, "translocation probability");
        probability(s.insertionRandomSequenceRatio, "insertion random ratio");
        probability(s.duplicationSameChromosomeRatio, "duplication same-chromosome ratio");
        probability(s.translocationReciprocalRatio, "translocation reciprocal ratio");
        probability(s.translocationOtherChromosomeRatio, "translocation other-chromosome ratio");
        geometric(s.insertionLengthP, s.insertionLengthMaxBits, "insertion");
        geometric(s.deletionLengthP, s.deletionLengthMaxBits, "deletion");
        geometric(s.duplicationLengthP, s.duplicationLengthMaxBits, "duplication");
        geometric(s.inversionLengthP, s.inversionLengthMaxBits, "inversion");
        geometric(s.translocationLengthP, s.translocationLengthMaxBits, "translocation");
        probability(mutation.nahr.baseProbability, "NAHR probability");
        if (!finite(mutation.nahr.structureMultiplierMax) || mutation.nahr.structureMultiplierMax < 1) throw new IllegalArgumentException("NAHR multiplier max must be >= 1");
        if (!finite(regulation.finalMultiplierMin) || !finite(regulation.finalMultiplierMax)
                || regulation.finalMultiplierMin <= 0 || regulation.finalMultiplierMin > 1
                || regulation.finalMultiplierMax < 1 || regulation.finalMultiplierMin > regulation.finalMultiplierMax) {
            throw new IllegalArgumentException("invalid regulation final multiplier range");
        }
        validateFinitePositive(regulation.strengthExponent, "strength exponent");
        silencer(regulation.cisSilencerMin, "cis silencer"); enhancer(regulation.cisEnhancerMax, "cis enhancer");
        silencer(regulation.transSilencerMin, "trans silencer"); enhancer(regulation.transEnhancerMax, "trans enhancer");
        silencer(regulation.epistasisSilencerMin, "epistasis silencer"); enhancer(regulation.epistasisEnhancerMax, "epistasis enhancer");
        validateRateBand(localRates.recombination, "recombination local rate");
        validateRateBand(localRates.point, "point local rate");
        validateRateBand(localRates.structural, "structural local rate");
        if (!finite(synthesizer.convergenceTolerance) || synthesizer.convergenceTolerance < 0) throw new IllegalArgumentException("invalid convergence tolerance");
        validatePositive(synthesizer.localAdjustmentMaxIterations, "synthesizer iterations");
        probability(synthesizer.localAdjustmentMaxContributionRatio, "local adjustment max contribution ratio");
        validatePositive(eventRetryMax, "event retry max");
    }

    public static EngineConfig defaults() {
        return new EngineConfig(
                new Homology(1024, 64, 8.0),
                new Recombination(4096, 5120, 384),
                new Mutation(0.000002,
                        new Structural(0.0015, 0.0015, 0.0010, 0.0005, 0.0003,
                                0.80, 0.80, 0.25, 0.75,
                                0.03125, 1024, 0.03125, 1024, 0.015625, 2048,
                                0.015625, 2048, 0.0078125, 4096),
                        new Nahr(0.0005, 8.0)),
                new Regulation(0.20, 3.00, 3.0, 1.50, 0.50, 1.25, 0.75, 1.75, 0.35),
                new LocalRates(new RateBand(3.0, 0.25, 0.10, 4.0),
                        new RateBand(6.0, 0.20, 0.10, 8.0),
                        new RateBand(4.0, 0.25, 0.10, 6.0)),
                new Synthesizer(0.002, 128, 0.05), 32);
    }

    public byte[] decoderFingerprint() {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            put(d, "wgl-engine-decoder-config-v1");
            Regulation r = regulation;
            put(d, r.finalMultiplierMin); put(d, r.finalMultiplierMax); put(d, r.strengthExponent);
            put(d, r.cisEnhancerMax); put(d, r.cisSilencerMin); put(d, r.transEnhancerMax); put(d, r.transSilencerMin);
            put(d, r.epistasisEnhancerMax); put(d, r.epistasisSilencerMin);
            return d.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void put(MessageDigest d, String s) { byte[] b=s.getBytes(StandardCharsets.UTF_8); d.update(ByteBuffer.allocate(4).putInt(b.length).array()); d.update(b); }
    private static void put(MessageDigest d, double v) { d.update(ByteBuffer.allocate(8).putLong(Double.doubleToLongBits(v)).array()); }
    private static boolean finite(double v) { return Double.isFinite(v); }
    private static void probability(double v, String n) { if (!finite(v)||v<0||v>1) throw new IllegalArgumentException(n+" must be in [0,1]"); }
    private static void geometric(double p, int max, String n) { if(!finite(p)||p<=0||p>1||max<1) throw new IllegalArgumentException("invalid "+n+" geometric config"); }
    private static void validatePositive(int v,String n){if(v<1)throw new IllegalArgumentException(n+" must be >= 1");}
    private static void validateFinitePositive(double v,String n){if(!finite(v)||v<=0)throw new IllegalArgumentException(n+" must be finite and > 0");}
    private static void silencer(double v,String n){if(!finite(v)||v<0||v>1)throw new IllegalArgumentException(n+" min must be [0,1]");}
    private static void enhancer(double v,String n){if(!finite(v)||v<1)throw new IllegalArgumentException(n+" max must be >= 1");}
    private static void validateRateBand(RateBand b,String n){ if(b==null||!finite(b.hotspotMax)||!finite(b.coldspotMin)||!finite(b.finalMin)||!finite(b.finalMax)||b.hotspotMax<1||b.coldspotMin<0||b.coldspotMin>1||b.finalMin<=0||b.finalMin>b.finalMax) throw new IllegalArgumentException("invalid "+n); }
}
