package co.surumene.wgl.api;

import java.util.Arrays;

public final class DecoderIdentity {
    private final int engineRevision;
    private final byte[] engineDecoderConfigFingerprint;
    private final ProfileDescriptor profileDescriptor;

    public DecoderIdentity(int engineRevision, byte[] engineDecoderConfigFingerprint, ProfileDescriptor profileDescriptor) {
        if (engineRevision < 1) throw new IllegalArgumentException("engineRevision must be >= 1");
        if (engineDecoderConfigFingerprint == null || engineDecoderConfigFingerprint.length != 32) {
            throw new IllegalArgumentException("engine fingerprint must be 32 bytes");
        }
        this.engineRevision = engineRevision;
        this.engineDecoderConfigFingerprint = engineDecoderConfigFingerprint.clone();
        this.profileDescriptor = java.util.Objects.requireNonNull(profileDescriptor, "profileDescriptor");
    }

    public int engineRevision() { return engineRevision; }
    public byte[] engineDecoderConfigFingerprint() { return engineDecoderConfigFingerprint.clone(); }
    public ProfileDescriptor profileDescriptor() { return profileDescriptor; }

    @Override public boolean equals(Object o) {
        return o instanceof DecoderIdentity that && engineRevision == that.engineRevision
                && Arrays.equals(engineDecoderConfigFingerprint, that.engineDecoderConfigFingerprint)
                && profileDescriptor.equals(that.profileDescriptor);
    }
    @Override public int hashCode() { return 31 * (31 * engineRevision + Arrays.hashCode(engineDecoderConfigFingerprint)) + profileDescriptor.hashCode(); }
}
