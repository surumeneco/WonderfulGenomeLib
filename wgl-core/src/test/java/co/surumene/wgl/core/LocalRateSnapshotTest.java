package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalRateSnapshotTest {
    @Test
    void hotspotAndColdspotUseConfiguredRadiusStrengthAndFinalClamp() {
        EngineConfig config = EngineConfig.defaults();
        TestProfile profile = TestProfile.defining(new GenomeAddress(0x00, 0x00));
        PhysicalGenomeDecoder parser = new PhysicalGenomeDecoder(config);

        BitSequence hotspot = GeneCodecV1.encodeRawEffect(new GenomeAddress(0x08, 0x08), 0xFF, 15, BitSequence.empty());
        BitSequence coldspot = GeneCodecV1.encodeRawEffect(new GenomeAddress(0x08, 0x09), 0x0F, 15, BitSequence.empty());
        BitSequence sequence = BitSequence.fromBits("0".repeat(64)).concat(hotspot)
                .concat(BitSequence.fromBits("0".repeat(600))).concat(coldspot)
                .concat(BitSequence.fromBits("0".repeat(640)));
        LocalRateSnapshot rates = LocalRateSnapshot.capture(sequence, profile, parser, config);

        int hotStart = 64;
        assertEquals(config.localRates().point().hotspotMax(),
                rates.multiplier(LocalRateSnapshot.Kind.POINT, hotStart), 1e-12);
        int coldStart = 64 + hotspot.bitLength() + 600;
        assertEquals(config.localRates().point().coldspotMin(),
                rates.multiplier(LocalRateSnapshot.Kind.POINT, coldStart), 1e-12);
        assertEquals(1.0, rates.multiplier(LocalRateSnapshot.Kind.POINT, sequence.bitLength() - 1), 1e-12);
    }
}
