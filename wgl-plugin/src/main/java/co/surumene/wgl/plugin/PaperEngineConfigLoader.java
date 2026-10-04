package co.surumene.wgl.plugin;

import co.surumene.wgl.core.EngineConfig;
import org.bukkit.configuration.file.FileConfiguration;

final class PaperEngineConfigLoader {
    private PaperEngineConfigLoader() {}

    static EngineConfig load(FileConfiguration c) {
        if (requiredInt(c, "config-version") != 1) throw new IllegalArgumentException("unsupported config-version");
        return new EngineConfig(
                new EngineConfig.Homology(
                        requiredInt(c, "engine.homology.max-anchor-gap-bits"),
                        requiredInt(c, "engine.homology.q-local-window-bits"),
                        requiredDouble(c, "engine.homology.q-local-decay")),
                new EngineConfig.Recombination(
                        requiredInt(c, "engine.recombination.extra-crossover-start-bits"),
                        requiredInt(c, "engine.recombination.extra-crossover-scale-bits"),
                        requiredInt(c, "engine.recombination.interference-distance-bits")),
                new EngineConfig.Mutation(
                        requiredDouble(c, "engine.mutation.point-per-bit-probability"),
                        new EngineConfig.Structural(
                                requiredDouble(c, "engine.mutation.structural.insertion-probability"),
                                requiredDouble(c, "engine.mutation.structural.deletion-probability"),
                                requiredDouble(c, "engine.mutation.structural.duplication-probability"),
                                requiredDouble(c, "engine.mutation.structural.inversion-probability"),
                                requiredDouble(c, "engine.mutation.structural.translocation-probability"),
                                requiredDouble(c, "engine.mutation.structural.insertion-random-sequence-ratio"),
                                requiredDouble(c, "engine.mutation.structural.duplication-same-chromosome-ratio"),
                                requiredDouble(c, "engine.mutation.structural.translocation-reciprocal-ratio"),
                                requiredDouble(c, "engine.mutation.structural.translocation-other-chromosome-ratio"),
                                requiredDouble(c, "engine.mutation.structural.insertion-length-p"),
                                requiredInt(c, "engine.mutation.structural.insertion-length-max-bits"),
                                requiredDouble(c, "engine.mutation.structural.deletion-length-p"),
                                requiredInt(c, "engine.mutation.structural.deletion-length-max-bits"),
                                requiredDouble(c, "engine.mutation.structural.duplication-length-p"),
                                requiredInt(c, "engine.mutation.structural.duplication-length-max-bits"),
                                requiredDouble(c, "engine.mutation.structural.inversion-length-p"),
                                requiredInt(c, "engine.mutation.structural.inversion-length-max-bits"),
                                requiredDouble(c, "engine.mutation.structural.translocation-length-p"),
                                requiredInt(c, "engine.mutation.structural.translocation-length-max-bits")),
                        new EngineConfig.Nahr(
                                requiredDouble(c, "engine.mutation.nahr.base-probability"),
                                requiredDouble(c, "engine.mutation.nahr.structure-multiplier-max"))),
                new EngineConfig.Regulation(
                        requiredDouble(c, "engine.regulation.final-multiplier-min"),
                        requiredDouble(c, "engine.regulation.final-multiplier-max"),
                        requiredDouble(c, "engine.regulation.strength-exponent"),
                        requiredDouble(c, "engine.regulation.cis-enhancer-max"),
                        requiredDouble(c, "engine.regulation.cis-silencer-min"),
                        requiredDouble(c, "engine.regulation.trans-enhancer-max"),
                        requiredDouble(c, "engine.regulation.trans-silencer-min"),
                        requiredDouble(c, "engine.regulation.epistasis-enhancer-max"),
                        requiredDouble(c, "engine.regulation.epistasis-silencer-min")),
                new EngineConfig.LocalRates(
                        new EngineConfig.RateBand(
                                requiredDouble(c, "engine.local-rate-regulation.recombination-hotspot-max"),
                                requiredDouble(c, "engine.local-rate-regulation.recombination-coldspot-min"),
                                requiredDouble(c, "engine.local-rate-regulation.recombination-final-min"),
                                requiredDouble(c, "engine.local-rate-regulation.recombination-final-max")),
                        new EngineConfig.RateBand(
                                requiredDouble(c, "engine.local-rate-regulation.point-hotspot-max"),
                                requiredDouble(c, "engine.local-rate-regulation.point-coldspot-min"),
                                requiredDouble(c, "engine.local-rate-regulation.point-final-min"),
                                requiredDouble(c, "engine.local-rate-regulation.point-final-max")),
                        new EngineConfig.RateBand(
                                requiredDouble(c, "engine.local-rate-regulation.structural-hotspot-max"),
                                requiredDouble(c, "engine.local-rate-regulation.structural-coldspot-min"),
                                requiredDouble(c, "engine.local-rate-regulation.structural-final-min"),
                                requiredDouble(c, "engine.local-rate-regulation.structural-final-max"))),
                new EngineConfig.Synthesizer(
                        requiredDouble(c, "engine.synthesizer.convergence-tolerance"),
                        requiredInt(c, "engine.synthesizer.local-adjustment-max-iterations"),
                        requiredDouble(c, "engine.synthesizer.micro-correction-max-ratio")),
                requiredInt(c, "engine.event-retry-max"));
    }

    private static int requiredInt(FileConfiguration config, String path) {
        Object raw = config.get(path);
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("missing or non-numeric integer config key: " + path);
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value != StrictMath.rint(value)
                || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("config key must be an integer: " + path);
        }
        return (int) value;
    }

    private static double requiredDouble(FileConfiguration config, String path) {
        Object raw = config.get(path);
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("missing or non-numeric config key: " + path);
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("config key must be finite: " + path);
        }
        return value;
    }

}
