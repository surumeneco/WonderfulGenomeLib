package co.surumene.wgl.core;

import co.surumene.wgl.api.GenomeAddress;
import co.surumene.wgl.api.GenomeProfile;

import java.util.Objects;

/** Fixed address-dependent extension grammar for Genome Format V1. */
final class GenomeFormatV1 {
    private GenomeFormatV1() {}

    static int minimumExtensionBits(GenomeAddress address, GenomeProfile<?> profile) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(profile, "profile");

        if (address.type() == 0x08) {
            return switch (address.target()) {
                case 0x02, 0x03, 0x06 -> 22;
                case 0x0C, 0x0D -> 52;
                default -> 0;
            };
        }

        int profileMinimum = profile.minimumExtensionBits(address);
        if (profileMinimum < 0 || profileMinimum > 64) {
            throw new IllegalStateException("profile minimumExtensionBits must be in [0,64] for " + address);
        }

        if (address.type() == 0x02 && address.target() <= 0x09) {
            return Math.max(16, profileMinimum);
        }
        return profileMinimum;
    }
}
