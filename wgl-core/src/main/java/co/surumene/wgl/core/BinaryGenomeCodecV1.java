package co.surumene.wgl.core;

import co.surumene.wgl.api.*;
import java.io.*;
import java.util.*;

public final class BinaryGenomeCodecV1 {
    private static final int MAGIC = 0x57474C47;
    public static final int CONTAINER_VERSION = 1;

    public byte[] encode(DiploidGenome genome) {
        Objects.requireNonNull(genome, "genome");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.writeInt(MAGIC); out.writeByte(CONTAINER_VERSION); out.writeShort(genome.genomeFormatVersion());
            out.writeShort(genome.chromosomePairCount());
            for (ChromosomePair pair : genome.chromosomePairs()) {
                writeSequence(out, pair.haplotypeA()); writeSequence(out, pair.haplotypeB());
            }
            out.flush(); return buffer.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    public DiploidGenome decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != MAGIC) throw new GenomeCodecException("invalid WGLG magic");
            int container = in.readUnsignedByte();
            if (container != CONTAINER_VERSION) throw new GenomeCodecException("unsupported container version: " + container);
            int format = in.readUnsignedShort();
            if (format != 1) throw new GenomeCodecException("unsupported genome format version: " + format);
            int count = in.readUnsignedShort();
            if (count < 1) throw new GenomeCodecException("chromosomePairCount must be >= 1");
            List<ChromosomePair> pairs = new ArrayList<>(count);
            for (int i=0;i<count;i++) pairs.add(new ChromosomePair(readSequence(in), readSequence(in)));
            if (in.available() != 0) throw new GenomeCodecException("trailing bytes are not allowed");
            return new DiploidGenome(format, pairs);
        } catch (EOFException e) { throw new GenomeCodecException("truncated genome container", e); }
        catch (IOException e) { throw new GenomeCodecException("failed to decode genome", e); }
        catch (IllegalArgumentException e) { throw new GenomeCodecException("malformed genome container: " + e.getMessage(), e); }
    }

    private static void writeSequence(DataOutputStream out, BitSequence seq) throws IOException {
        out.writeInt(seq.bitLength()); out.write(seq.packedBits());
    }
    private static BitSequence readSequence(DataInputStream in) throws IOException {
        long unsignedLength = Integer.toUnsignedLong(in.readInt());
        if (unsignedLength > Integer.MAX_VALUE) throw new GenomeCodecException("bit length exceeds Java model limit");
        int bitLength=(int)unsignedLength;
        int byteLength=BitSequence.packedLength(bitLength);
        if (byteLength > in.available()) throw new GenomeCodecException("packed bit data is truncated");
        byte[] packed=in.readNBytes(byteLength);
        return BitSequence.ofCanonicalPacked(packed, bitLength);
    }
}
