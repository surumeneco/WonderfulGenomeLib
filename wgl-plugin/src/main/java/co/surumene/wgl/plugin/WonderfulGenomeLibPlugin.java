package co.surumene.wgl.plugin;

import co.surumene.wgl.core.EngineConfig;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class WonderfulGenomeLibPlugin extends JavaPlugin implements Listener {
    private ProfileRegistry profiles;
    private DefaultWonderfulGenomeLibService service;

    @Override public void onEnable() {
        saveDefaultConfig();
        final EngineConfig initial;
        try {
            initial = PaperEngineConfigLoader.load(getConfig());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Invalid WonderfulGenomeLib config; refusing to enable", ex);
        }
        profiles = new ProfileRegistry();
        service = new DefaultWonderfulGenomeLibService(this, profiles, initial);
        Bukkit.getServicesManager().register(WonderfulGenomeLibService.class, service, this, ServicePriority.Normal);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
        if (profiles != null) profiles.clear();
    }

    @EventHandler public void onPluginDisable(PluginDisableEvent event) {
        if (service != null && event.getPlugin() != this) service.unregisterOwner(event.getPlugin());
    }
}
