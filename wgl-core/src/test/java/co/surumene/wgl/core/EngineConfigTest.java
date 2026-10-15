package co.surumene.wgl.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EngineConfigTest {
    @Test
    void decoderFingerprintChangesOnlyForDecoderRelevantValues() {
        EngineConfig a = EngineConfig.defaults();
        EngineConfig.Mutation m = a.mutation();
        EngineConfig b = new EngineConfig(a.homology(), a.recombination(),
                new EngineConfig.Mutation(0.5, m.structural(), m.nahr()),
                a.regulation(), a.localRates(), a.synthesizer(), a.eventRetryMax());
        assertArrayEquals(a.decoderFingerprint(), b.decoderFingerprint());

        EngineConfig.Regulation r = a.regulation();
        EngineConfig c = new EngineConfig(a.homology(), a.recombination(), a.mutation(),
                new EngineConfig.Regulation(r.finalMultiplierMin(), r.finalMultiplierMax(), r.strengthExponent(),
                        r.cisEnhancerMax() + 0.01, r.cisSilencerMin(), r.transEnhancerMax(), r.transSilencerMin(),
                        r.epistasisEnhancerMax(), r.epistasisSilencerMin()),
                a.localRates(), a.synthesizer(), a.eventRetryMax());
        assertFalse(java.util.Arrays.equals(a.decoderFingerprint(), c.decoderFingerprint()));
    }

    @Test
    void rejectsNaNAndInvalidGeometricParameters() {
        EngineConfig d = EngineConfig.defaults();
        assertThrows(IllegalArgumentException.class, () -> new EngineConfig(
                d.homology(), d.recombination(),
                new EngineConfig.Mutation(Double.NaN, d.mutation().structural(), d.mutation().nahr()),
                d.regulation(), d.localRates(), d.synthesizer(), d.eventRetryMax()));

        EngineConfig.Structural s = d.mutation().structural();
        assertThrows(IllegalArgumentException.class, () -> new EngineConfig(
                d.homology(), d.recombination(),
                new EngineConfig.Mutation(d.mutation().pointPerBitProbability(),
                        new EngineConfig.Structural(s.insertionProbability(), s.deletionProbability(), s.duplicationProbability(),
                                s.inversionProbability(), s.translocationProbability(), s.insertionRandomSequenceRatio(),
                                s.duplicationSameChromosomeRatio(), s.translocationReciprocalRatio(), s.translocationOtherChromosomeRatio(),
                                0.0, s.insertionLengthMaxBits(), s.deletionLengthP(), s.deletionLengthMaxBits(),
                                s.duplicationLengthP(), s.duplicationLengthMaxBits(), s.inversionLengthP(), s.inversionLengthMaxBits(),
                                s.translocationLengthP(), s.translocationLengthMaxBits()),
                        d.mutation().nahr()), d.regulation(), d.localRates(), d.synthesizer(), d.eventRetryMax()));
    }
}
