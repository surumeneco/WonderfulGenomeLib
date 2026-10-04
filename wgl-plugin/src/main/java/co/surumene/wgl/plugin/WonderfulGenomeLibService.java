package co.surumene.wgl.plugin;

import co.surumene.wgl.api.GenomeEngine;
import co.surumene.wgl.api.GenomeProfile;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

public interface WonderfulGenomeLibService {
    GenomeEngine engine();
    void registerProfile(Plugin owner, GenomeProfile<?> profile);
    void replaceProfile(Plugin owner, GenomeProfile<?> profile);
    Optional<GenomeProfile<?>> profile(String profileId);
    void unregisterOwner(Plugin owner);
    boolean reloadEngineConfiguration();
}
