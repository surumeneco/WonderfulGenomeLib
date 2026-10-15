package co.surumene.wgl.api;

/** Lossless typed binary codec for breeding parent sources. */
public interface BreedingParentSourceCodec {
    byte[] encode(BreedingParentSource source);
    BreedingParentSource decode(byte[] bytes);
}
