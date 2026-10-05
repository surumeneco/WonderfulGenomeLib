package co.surumene.wgl.api;

/**
 * Canonical encoder for physical Genome Format gene sequences used by consumer-supplied
 * synthesis blocks. Consumers must use this contract instead of reproducing physical
 * address-header, Gray-code, START/END, or related format details.
 */
public interface GeneSequenceCodec {
    BitSequence encodeDirectGene(
            GenomeAddress address,
            boolean negative,
            int magnitudeCode,
            int expressionCode,
            BitSequence extension);

    BitSequence encodeRawGene(
            GenomeAddress address,
            int rawEffectByte,
            int expressionCode,
            BitSequence extension);

    BitSequence encodeAddressHeader(GenomeAddress address);
}
