package co.surumene.wgl.core;

import co.surumene.wgl.api.*;

import java.io.*;
import java.util.*;

public final class BinaryBreedingParentSourceCodecV1 implements BreedingParentSourceCodec {
    private static final int MAGIC = 0x57474C50; // WGLP
    public static final int CONTAINER_VERSION = 1;
    private static final int DIPLOID_PARENT = 0;
    private static final int GAMETE = 1;

    private final BinaryGenomeCodecV1 diploidCodec = new BinaryGenomeCodecV1();

    @Override
    public byte[] encode(BreedingParentSource source) {
        Objects.requireNonNull(source, "source");
        byte[] payload;
        int sourceType;
        if (source instanceof BreedingParentSource.DiploidParent diploid) {
            sourceType = DIPLOID_PARENT;
            payload = diploidCodec.encode(diploid.genome());
        } else if (source instanceof BreedingParentSource.Gamete gamete) {
            sourceType = GAMETE;
            payload = encodeHaploid(gamete.genome());
        } else {
            throw new IllegalArgumentException("unsupported breeding parent source");
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.writeInt(MAGIC);
            out.writeByte(CONTAINER_VERSION);
            out.writeByte(sourceType);
            out.writeInt(payload.length);
            out.write(payload);
            out.flush();
            return buffer.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    @Override
    public BreedingParentSource decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != MAGIC) {
                throw new GenomeCodecException("invalid WGLP magic");
            }
            int containerVersion = in.readUnsignedByte();
            if (containerVersion != CONTAINER_VERSION) {
                throw new GenomeCodecException(
                        "unsupported parent source container version: " + containerVersion);
            }
            int sourceType = in.readUnsignedByte();
            long unsignedPayloadLength = Integer.toUnsignedLong(in.readInt());
            if (unsignedPayloadLength > Integer.MAX_VALUE) {
                throw new GenomeCodecException("payload length exceeds Java model limit");
            }
            int payloadLength = (int) unsignedPayloadLength;
            if (payloadLength > in.available()) {
                throw new GenomeCodecException("parent source payload is truncated");
            }
            byte[] payload = in.readNBytes(payloadLength);
            if (in.available() != 0) {
                throw new GenomeCodecException("trailing bytes are not allowed");
            }

            return switch (sourceType) {
                case DIPLOID_PARENT ->
                        new BreedingParentSource.DiploidParent(diploidCodec.decode(payload));
                case GAMETE ->
                        new BreedingParentSource.Gamete(decodeHaploid(payload));
                default -> throw new GenomeCodecException(
                        "unsupported parent source type: " + sourceType);
            };
        } catch (EOFException e) {
            throw new GenomeCodecException("truncated parent source container", e);
        } catch (IOException e) {
            throw new GenomeCodecException("failed to decode parent source", e);
        } catch (IllegalArgumentException e) {
            throw new GenomeCodecException(
                    "malformed parent source container: " + e.getMessage(), e);
        }
    }

    private static byte[] encodeHaploid(HaploidGenome genome) {
        Objects.requireNonNull(genome, "genome");
        if (genome.genomeFormatVersion() != WonderfulGenomeEngine.GENOME_FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported genome format version: " + genome.genomeFormatVersion());
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.writeShort(genome.genomeFormatVersion());
            out.writeShort(genome.chromosomeCount());
            for (BitSequence chromosome : genome.chromosomes()) {
                out.writeInt(chromosome.bitLength());
                out.write(chromosome.packedBits());
            }
            out.flush();
            return buffer.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static HaploidGenome decodeHaploid(byte[] payload) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            int format = in.readUnsignedShort();
            if (format != WonderfulGenomeEngine.GENOME_FORMAT_VERSION) {
                throw new GenomeCodecException(
                        "unsupported genome format version: " + format);
            }
            int chromosomeCount = in.readUnsignedShort();
            if (chromosomeCount < 1) {
                throw new GenomeCodecException("chromosomeCount must be >= 1");
            }

            List<BitSequence> chromosomes = new ArrayList<>(chromosomeCount);
            for (int i = 0; i < chromosomeCount; i++) {
                long unsignedBitLength = Integer.toUnsignedLong(in.readInt());
                if (unsignedBitLength > Integer.MAX_VALUE) {
                    throw new GenomeCodecException(
                            "bit length exceeds Java model limit");
                }
                int bitLength = (int) unsignedBitLength;
                int byteLength = BitSequence.packedLength(bitLength);
                if (byteLength > in.available()) {
                    throw new GenomeCodecException("packed bit data is truncated");
                }
                chromosomes.add(BitSequence.ofCanonicalPacked(
                        in.readNBytes(byteLength), bitLength));
            }
            if (in.available() != 0) {
                throw new GenomeCodecException(
                        "trailing bytes are not allowed in haploid payload");
            }
            return new HaploidGenome(format, chromosomes);
        } catch (EOFException e) {
            throw new GenomeCodecException("truncated haploid genome payload", e);
        } catch (IOException e) {
            throw new GenomeCodecException("failed to decode haploid genome", e);
        } catch (IllegalArgumentException e) {
            throw new GenomeCodecException(
                    "malformed haploid genome payload: " + e.getMessage(), e);
        }
    }
}
