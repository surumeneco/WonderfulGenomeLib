package co.surumene.wgl.core;

import co.surumene.wgl.api.BitSequence;
import co.surumene.wgl.api.GeneSequenceCodec;
import co.surumene.wgl.api.GenomeAddress;

import java.util.Objects;

final class GeneSequenceCodecV1 implements GeneSequenceCodec {
    @Override
    public BitSequence encodeDirectGene(
            GenomeAddress address,
            boolean negative,
            int magnitudeCode,
            int expressionCode,
            BitSequence extension) {
        return GeneCodecV1.encode(
                Objects.requireNonNull(address, "address"),
                negative,
                magnitudeCode,
                expressionCode,
                Objects.requireNonNull(extension, "extension"));
    }

    @Override
    public BitSequence encodeRawGene(
            GenomeAddress address,
            int rawEffectByte,
            int expressionCode,
            BitSequence extension) {
        return GeneCodecV1.encodeRawEffect(
                Objects.requireNonNull(address, "address"),
                rawEffectByte,
                expressionCode,
                Objects.requireNonNull(extension, "extension"));
    }

    @Override
    public BitSequence encodeAddressHeader(GenomeAddress address) {
        return BitSequence.fromBits(
                Secded22.encode(Objects.requireNonNull(address, "address")).toBitString());
    }
}
