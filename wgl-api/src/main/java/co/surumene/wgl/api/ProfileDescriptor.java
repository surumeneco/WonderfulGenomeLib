package co.surumene.wgl.api;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ProfileDescriptor {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private final String profileId;
    private final int profileVersion;
    private final byte[] semanticFingerprint;

    public ProfileDescriptor(String profileId, int profileVersion, byte[] semanticFingerprint) {
        this.profileId = Objects.requireNonNull(profileId, "profileId");
        if (!ID.matcher(profileId).matches()) throw new IllegalArgumentException("invalid profileId");
        if (profileVersion < 1) throw new IllegalArgumentException("profileVersion must be >= 1");
        Objects.requireNonNull(semanticFingerprint, "semanticFingerprint");
        if (semanticFingerprint.length != 32) throw new IllegalArgumentException("semanticFingerprint must be 32 bytes");
        this.profileVersion = profileVersion;
        this.semanticFingerprint = semanticFingerprint.clone();
    }

    public String profileId() { return profileId; }
    public int profileVersion() { return profileVersion; }
    public byte[] semanticFingerprint() { return semanticFingerprint.clone(); }

    @Override public boolean equals(Object o) {
        return o instanceof ProfileDescriptor that && profileVersion == that.profileVersion
                && profileId.equals(that.profileId) && Arrays.equals(semanticFingerprint, that.semanticFingerprint);
    }
    @Override public int hashCode() { return 31 * (31 * profileId.hashCode() + profileVersion) + Arrays.hashCode(semanticFingerprint); }
}
