package co.surumene.wgl.plugin;

import co.surumene.wgl.api.*;
import co.surumene.wgl.core.*;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

final class DefaultWonderfulGenomeLibService implements WonderfulGenomeLibService {
    private final WonderfulGenomeLibPlugin plugin;
    private final ProfileRegistry profiles;
    private final AtomicReference<WonderfulGenomeEngine> engine;

    DefaultWonderfulGenomeLibService(WonderfulGenomeLibPlugin plugin, ProfileRegistry profiles, EngineConfig initial) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.engine = new AtomicReference<>(WonderfulGenomeEngine.create(initial));
    }

    @Override public GenomeEngine engine() { return engine.get(); }
    @Override public void registerProfile(Plugin owner, GenomeProfile<?> profile) { profiles.register(owner, profile); }
    @Override public void replaceProfile(Plugin owner, GenomeProfile<?> profile) { profiles.replace(owner, profile); }
    @Override public Optional<GenomeProfile<?>> profile(String profileId) { return profiles.get(profileId); }
    @Override public void unregisterOwner(Plugin owner) { profiles.unregisterOwner(owner); }

    @Override public synchronized boolean reloadEngineConfiguration() {
        plugin.reloadConfig();
        try {
            EngineConfig next = PaperEngineConfigLoader.load(plugin.getConfig());
            engine.set(WonderfulGenomeEngine.create(next));
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("WGL config reload rejected; previous engine snapshot remains active: " + ex.getMessage());
            return false;
        }
    }
}
