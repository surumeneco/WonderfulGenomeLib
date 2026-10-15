package co.surumene.wgl.plugin;

import co.surumene.wgl.api.GenomeProfile;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class ProfileRegistry {
    private record Registration(Plugin owner, GenomeProfile<?> profile) {}
    private final ConcurrentHashMap<String, Registration> profiles = new ConcurrentHashMap<>();

    void register(Plugin owner, GenomeProfile<?> profile) {
        if (owner == null || profile == null) throw new IllegalArgumentException("owner/profile must not be null");
        String id = profile.descriptor().profileId();
        Registration prior = profiles.putIfAbsent(id, new Registration(owner, profile));
        if (prior != null) throw new IllegalStateException("profile is already registered: " + id);
    }

    void replace(Plugin owner, GenomeProfile<?> profile) {
        if (owner == null || profile == null) throw new IllegalArgumentException("owner/profile must not be null");
        String id = profile.descriptor().profileId();
        profiles.compute(id, (key, existing) -> {
            if (existing == null) throw new IllegalStateException("profile is not registered: " + id);
            if (existing.owner() != owner) throw new IllegalStateException("profile belongs to another plugin: " + id);
            return new Registration(owner, profile);
        });
    }

    Optional<GenomeProfile<?>> get(String id) {
        Registration value = profiles.get(id);
        return value == null ? Optional.empty() : Optional.of(value.profile());
    }

    void unregisterOwner(Plugin owner) { profiles.entrySet().removeIf(e -> e.getValue().owner() == owner); }
    void clear() { profiles.clear(); }
}
