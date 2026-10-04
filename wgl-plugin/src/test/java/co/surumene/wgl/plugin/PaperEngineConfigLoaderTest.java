package co.surumene.wgl.plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PaperEngineConfigLoaderTest {
    @Test
    void rejectsMissingProbabilityEvenThoughZeroWouldOtherwiseBeValid() throws Exception {
        YamlConfiguration config = loadDefault();
        config.set("engine.mutation.structural.insertion-probability", null);

        assertThrows(IllegalArgumentException.class, () -> PaperEngineConfigLoader.load(config));
    }

    @Test
    void rejectsWrongNumericTypeInsteadOfCoercingItToZero() throws Exception {
        YamlConfiguration config = loadDefault();
        config.set("engine.mutation.structural.insertion-probability", "not-a-number");

        assertThrows(IllegalArgumentException.class, () -> PaperEngineConfigLoader.load(config));
    }

    @Test
    void loadsTheDistributedDefaultConfiguration() throws Exception {
        assertDoesNotThrow(() -> PaperEngineConfigLoader.load(loadDefault()));
    }

    @Test
    void distributedConfigUsesLocalAdjustmentContributionRatioKey() throws Exception {
        YamlConfiguration config = loadDefault();

        assertTrue(config.contains("engine.synthesizer.local-adjustment-max-contribution-ratio"));
        assertFalse(config.contains("engine.synthesizer.micro-correction-max-ratio"));
    }

    private static YamlConfiguration loadDefault() throws Exception {
        try (var stream = PaperEngineConfigLoaderTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(stream, "config.yml must be on the test runtime classpath");
            YamlConfiguration config = new YamlConfiguration();
            config.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return config;
        }
    }
}
