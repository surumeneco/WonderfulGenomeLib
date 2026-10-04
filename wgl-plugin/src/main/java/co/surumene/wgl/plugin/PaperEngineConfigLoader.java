package co.surumene.wgl.plugin;

import co.surumene.wgl.core.EngineConfig;
import org.bukkit.configuration.file.FileConfiguration;

final class PaperEngineConfigLoader {
    private PaperEngineConfigLoader() {}

    static EngineConfig load(FileConfiguration c) {
        if (c.getInt("config-version", -1) != 1) throw new IllegalArgumentException("unsupported config-version");
        return new EngineConfig(
                new EngineConfig.Homology(
                        c.getInt("engine.homology.max-anchor-gap-bits"),
                        c.getInt("engine.homology.q-local-window-bits"),
                        c.getDouble("engine.homology.q-local-decay")),
                new EngineConfig.Recombination(
                        c.getInt("engine.recombination.extra-crossover-start-bits"),
                        c.getInt("engine.recombination.extra-crossover-scale-bits"),
                        c.getInt("engine.recombination.interference-distance-bits")),
                new EngineConfig.Mutation(
                        c.getDouble("engine.mutation.point-per-bit-probability"),
                        new EngineConfig.Structural(
                                c.getDouble("engine.mutation.structural.insertion-probability"),
                                c.getDouble("engine.mutation.structural.deletion-probability"),
                                c.getDouble("engine.mutation.structural.duplication-probability"),
                                c.getDouble("engine.mutation.structural.inversion-probability"),
                                c.getDouble("engine.mutation.structural.translocation-probability"),
                                c.getDouble("engine.mutation.structural.insertion-random-sequence-ratio"),
                                c.getDouble("engine.mutation.structural.duplication-same-chromosome-ratio"),
                                c.getDouble("engine.mutation.structural.translocation-reciprocal-ratio"),
                                c.getDouble("engine.mutation.structural.translocation-other-chromosome-ratio"),
                                c.getDouble("engine.mutation.structural.insertion-length-p"),
                                c.getInt("engine.mutation.structural.insertion-length-max-bits"),
                                c.getDouble("engine.mutation.structural.deletion-length-p"),
                                c.getInt("engine.mutation.structural.deletion-length-max-bits"),
                                c.getDouble("engine.mutation.structural.duplication-length-p"),
                                c.getInt("engine.mutation.structural.duplication-length-max-bits"),
                                c.getDouble("engine.mutation.structural.inversion-length-p"),
                                c.getInt("engine.mutation.structural.inversion-length-max-bits"),
                                c.getDouble("engine.mutation.structural.translocation-length-p"),
                                c.getInt("engine.mutation.structural.translocation-length-max-bits")),
                        new EngineConfig.Nahr(
                                c.getDouble("engine.mutation.nahr.base-probability"),
                                c.getDouble("engine.mutation.nahr.structure-multiplier-max"))),
                new EngineConfig.Regulation(
                        c.getDouble("engine.regulation.final-multiplier-min"),
                        c.getDouble("engine.regulation.final-multiplier-max"),
                        c.getDouble("engine.regulation.strength-exponent"),
                        c.getDouble("engine.regulation.cis-enhancer-max"),
                        c.getDouble("engine.regulation.cis-silencer-min"),
                        c.getDouble("engine.regulation.trans-enhancer-max"),
                        c.getDouble("engine.regulation.trans-silencer-min"),
                        c.getDouble("engine.regulation.epistasis-enhancer-max"),
                        c.getDouble("engine.regulation.epistasis-silencer-min")),
                new EngineConfig.LocalRates(
                        new EngineConfig.RateBand(
                                c.getDouble("engine.local-rate-regulation.recombination-hotspot-max"),
                                c.getDouble("engine.local-rate-regulation.recombination-coldspot-min"),
                                c.getDouble("engine.local-rate-regulation.recombination-final-min"),
                                c.getDouble("engine.local-rate-regulation.recombination-final-max")),
                        new EngineConfig.RateBand(
                                c.getDouble("engine.local-rate-regulation.point-hotspot-max"),
                                c.getDouble("engine.local-rate-regulation.point-coldspot-min"),
                                c.getDouble("engine.local-rate-regulation.point-final-min"),
                                c.getDouble("engine.local-rate-regulation.point-final-max")),
                        new EngineConfig.RateBand(
                                c.getDouble("engine.local-rate-regulation.structural-hotspot-max"),
                                c.getDouble("engine.local-rate-regulation.structural-coldspot-min"),
                                c.getDouble("engine.local-rate-regulation.structural-final-min"),
                                c.getDouble("engine.local-rate-regulation.structural-final-max"))),
                new EngineConfig.Synthesizer(
                        c.getDouble("engine.synthesizer.convergence-tolerance"),
                        c.getInt("engine.synthesizer.local-adjustment-max-iterations"),
                        c.getDouble("engine.synthesizer.micro-correction-max-ratio")),
                c.getInt("engine.event-retry-max"));
    }

}
