package co.surumene.wgl.core;

import co.surumene.wgl.api.DecodedGene;
import co.surumene.wgl.api.GenomeProfile;
import co.surumene.wgl.api.BitSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable local recombination/mutation rate multipliers resolved at meiosis start. */
final class LocalRateSnapshot {
    enum Kind { RECOMBINATION, POINT, STRUCTURAL }

    private final EngineConfig config;
    private final int bitLength;
    private final List<Element> elements;

    private LocalRateSnapshot(EngineConfig config, int bitLength, List<Element> elements) {
        this.config = config;
        this.bitLength = bitLength;
        this.elements = List.copyOf(elements);
    }

    static LocalRateSnapshot capture(BitSequence sequence, GenomeProfile<?> profile,
                                     PhysicalGenomeDecoder parser, EngineConfig config) {
        Objects.requireNonNull(sequence, "sequence");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(parser, "parser");
        Objects.requireNonNull(config, "config");
        List<Element> elements = new ArrayList<>();
        for (DecodedGene gene : parser.parseChromosome(sequence, profile)) {
            if (!gene.addressValid() || !gene.regulation()) continue;
            int target = gene.address().target();
            Kind kind;
            boolean hotspot;
            if (target == 0x04 || target == 0x05) {
                kind = Kind.RECOMBINATION; hotspot = target == 0x04;
            } else if (target == 0x08 || target == 0x09) {
                kind = Kind.POINT; hotspot = target == 0x08;
            } else if (target == 0x0A || target == 0x0B) {
                kind = Kind.STRUCTURAL; hotspot = target == 0x0A;
            } else continue;
            int raw = gene.rawEffectByte();
            int radiusCode = (raw >>> 4) & 0xF;
            int strength = raw & 0xF;
            int radius = 32 * (radiusCode + 1);
            double q = StrictMath.pow(strength / 15.0, config.regulation().strengthExponent())
                    * (gene.expressionCode() / 15.0);
            elements.add(new Element(kind, hotspot, gene.startBit(), radius, q));
        }
        return new LocalRateSnapshot(config, sequence.bitLength(), elements);
    }

    double multiplier(Kind kind, int position) {
        if (bitLength == 0) return 1.0;
        int p = Math.max(0, Math.min(bitLength - 1, position));
        EngineConfig.RateBand band = switch (kind) {
            case RECOMBINATION -> config.localRates().recombination();
            case POINT -> config.localRates().point();
            case STRUCTURAL -> config.localRates().structural();
        };
        double result = 1.0;
        for (Element element : elements) {
            if (element.kind() != kind) continue;
            if (StrictMath.abs(p - element.start()) > element.radius()) continue;
            double factor = element.hotspot()
                    ? 1.0 + (band.hotspotMax() - 1.0) * element.q()
                    : 1.0 - (1.0 - band.coldspotMin()) * element.q();
            result *= factor;
        }
        return clamp(result, band.finalMin(), band.finalMax());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Element(Kind kind, boolean hotspot, int start, int radius, double q) {}
}
