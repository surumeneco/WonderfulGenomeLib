package co.surumene.wgl.plugin;

import co.surumene.wgl.api.*;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class ProfileRegistryTest {
    @Test
    void duplicateRegistrationIsRejectedAndOriginalRemains() {
        ProfileRegistry registry = new ProfileRegistry();
        Plugin owner = plugin();
        GenomeProfile<?> first = profile("alpha", 1);
        GenomeProfile<?> second = profile("alpha", 2);

        registry.register(owner, first);

        assertThrows(IllegalStateException.class, () -> registry.register(owner, second));
        assertSame(first, registry.get("alpha").orElseThrow());
    }

    @Test
    void replacementRequiresSameOwnerAndIsAtomicForFutureLookups() {
        ProfileRegistry registry = new ProfileRegistry();
        Plugin owner = plugin();
        Plugin other = plugin();
        GenomeProfile<?> first = profile("alpha", 1);
        GenomeProfile<?> replacement = profile("alpha", 2);

        registry.register(owner, first);

        assertThrows(IllegalStateException.class, () -> registry.replace(other, replacement));
        assertSame(first, registry.get("alpha").orElseThrow());

        registry.replace(owner, replacement);
        assertSame(replacement, registry.get("alpha").orElseThrow());
    }

    @Test
    void unregisterOwnerRemovesOnlyThatOwnersProfiles() {
        ProfileRegistry registry = new ProfileRegistry();
        Plugin firstOwner = plugin();
        Plugin secondOwner = plugin();

        registry.register(firstOwner, profile("alpha", 1));
        registry.register(firstOwner, profile("beta", 1));
        GenomeProfile<?> gamma = profile("gamma", 1);
        registry.register(secondOwner, gamma);

        registry.unregisterOwner(firstOwner);

        assertTrue(registry.get("alpha").isEmpty());
        assertTrue(registry.get("beta").isEmpty());
        assertSame(gamma, registry.get("gamma").orElseThrow());
    }

    private static Plugin plugin() {
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    Class<?> type = method.getReturnType();
                    if (!type.isPrimitive()) return null;
                    if (type == boolean.class) return false;
                    if (type == char.class) return '\0';
                    return 0;
                });
    }

    private static GenomeProfile<DecodedGenome> profile(String id, int version) {
        return new GenomeProfile<>() {
            @Override public ProfileDescriptor descriptor() {
                return new ProfileDescriptor(id, version, new byte[32]);
            }
            @Override public boolean isDefinedAddress(GenomeAddress address) { return false; }
            @Override public DirectContributionModel contributionModel(GenomeAddress address) {
                return StandardDirectContributionModel.defaultModel();
            }
            @Override public DecodedGenome mapPhenotype(DecodedGenome decodedGenome) {
                return decodedGenome;
            }
        };
    }
}
