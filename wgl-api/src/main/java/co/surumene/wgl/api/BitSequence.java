package co.surumene.wgl.api;

import java.util.Arrays;
import java.util.Objects;

/** Immutable MSB-first bit sequence with an explicit bit length. */
public final class BitSequence {
    private static final BitSequence EMPTY = new BitSequence(new byte[0], 0, false);
    private final byte[] packedBits;
    private final int bitLength;

    private BitSequence(byte[] packedBits, int bitLength, boolean copy) {
        this.bitLength = bitLength;
        this.packedBits = copy ? packedBits.clone() : packedBits;
    }

    public static BitSequence empty() {
        return EMPTY;
    }

    public static BitSequence ofPacked(byte[] packedBits, int bitLength) {
        Objects.requireNonNull(packedBits, "packedBits");
        if (bitLength < 0) throw new IllegalArgumentException("bitLength must be >= 0");
        int byteLength = packedLength(bitLength);
        if (packedBits.length != byteLength) {
            throw new IllegalArgumentException("packed byte length does not match bitLength");
        }
        if (bitLength == 0) return EMPTY;
        byte[] canonical = packedBits.clone();
        int used = bitLength & 7;
        if (used != 0) {
            int mask = 0xFF << (8 - used);
            canonical[canonical.length - 1] &= (byte) mask;
        }
        return new BitSequence(canonical, bitLength, false);
    }

    /** Strict variant used by codecs: unused low tail bits must already be zero. */
    public static BitSequence ofCanonicalPacked(byte[] packedBits, int bitLength) {
        Objects.requireNonNull(packedBits, "packedBits");
        if (bitLength < 0) throw new IllegalArgumentException("bitLength must be >= 0");
        if (packedBits.length != packedLength(bitLength)) {
            throw new IllegalArgumentException("packed byte length does not match bitLength");
        }
        if (bitLength > 0 && (bitLength & 7) != 0) {
            int unused = 8 - (bitLength & 7);
            int mask = (1 << unused) - 1;
            if ((packedBits[packedBits.length - 1] & mask) != 0) {
                throw new IllegalArgumentException("non-canonical non-zero tail bits");
            }
        }
        return bitLength == 0 ? EMPTY : new BitSequence(packedBits, bitLength, true);
    }

    public static BitSequence fromBits(String bits) {
        Objects.requireNonNull(bits, "bits");
        if (bits.isEmpty()) return EMPTY;
        byte[] bytes = new byte[packedLength(bits.length())];
        for (int i = 0; i < bits.length(); i++) {
            char c = bits.charAt(i);
            if (c != '0' && c != '1') throw new IllegalArgumentException("bits must contain only 0 or 1");
            if (c == '1') set(bytes, i, true);
        }
        return new BitSequence(bytes, bits.length(), false);
    }

    public static BitSequence fromLong(long value, int bitLength) {
        if (bitLength < 0 || bitLength > 64) throw new IllegalArgumentException("bitLength must be 0..64");
        byte[] bytes = new byte[packedLength(bitLength)];
        for (int i = 0; i < bitLength; i++) {
            int sourceShift = bitLength - 1 - i;
            if (((value >>> sourceShift) & 1L) != 0) set(bytes, i, true);
        }
        return bitLength == 0 ? EMPTY : new BitSequence(bytes, bitLength, false);
    }

    public int bitLength() {
        return bitLength;
    }

    public byte[] packedBits() {
        return packedBits.clone();
    }

    public boolean bitAt(int index) {
        checkIndex(index);
        return ((packedBits[index >>> 3] >>> (7 - (index & 7))) & 1) != 0;
    }

    public BitSequence flip(int index) {
        checkIndex(index);
        byte[] copy = packedBits.clone();
        copy[index >>> 3] ^= (byte) (1 << (7 - (index & 7)));
        return new BitSequence(copy, bitLength, false);
    }

    public BitSequence slice(int fromInclusive, int toExclusive) {
        if (fromInclusive < 0 || toExclusive < fromInclusive || toExclusive > bitLength) {
            throw new IndexOutOfBoundsException("invalid bit slice");
        }
        int length = toExclusive - fromInclusive;
        if (length == 0) return EMPTY;
        byte[] out = new byte[packedLength(length)];
        for (int i = 0; i < length; i++) {
            if (bitAt(fromInclusive + i)) set(out, i, true);
        }
        return new BitSequence(out, length, false);
    }

    public BitSequence concat(BitSequence other) {
        Objects.requireNonNull(other, "other");
        if (other.bitLength == 0) return this;
        if (bitLength == 0) return other;
        long total = (long) bitLength + other.bitLength;
        if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("bit sequence too large");
        byte[] out = new byte[packedLength((int) total)];
        copyBits(this, 0, out, 0, bitLength);
        copyBits(other, 0, out, bitLength, other.bitLength);
        return new BitSequence(out, (int) total, false);
    }

    public BitSequence insert(int position, BitSequence inserted) {
        Objects.requireNonNull(inserted, "inserted");
        if (position < 0 || position > bitLength) throw new IndexOutOfBoundsException("invalid insert position");
        return slice(0, position).concat(inserted).concat(slice(position, bitLength));
    }

    public BitSequence delete(int fromInclusive, int toExclusive) {
        if (fromInclusive < 0 || toExclusive < fromInclusive || toExclusive > bitLength) {
            throw new IndexOutOfBoundsException("invalid delete range");
        }
        return slice(0, fromInclusive).concat(slice(toExclusive, bitLength));
    }

    public BitSequence replace(int fromInclusive, int toExclusive, BitSequence replacement) {
        Objects.requireNonNull(replacement, "replacement");
        if (fromInclusive < 0 || toExclusive < fromInclusive || toExclusive > bitLength) {
            throw new IndexOutOfBoundsException("invalid replace range");
        }
        return slice(0, fromInclusive).concat(replacement).concat(slice(toExclusive, bitLength));
    }

    public BitSequence reverse() {
        if (bitLength <= 1) return this;
        byte[] out = new byte[packedBits.length];
        for (int i = 0; i < bitLength; i++) {
            if (bitAt(bitLength - 1 - i)) set(out, i, true);
        }
        return new BitSequence(out, bitLength, false);
    }

    public long toLong(int offset, int length) {
        if (length < 0 || length > 64 || offset < 0 || offset + length > bitLength) {
            throw new IndexOutOfBoundsException("invalid long window");
        }
        long value = 0;
        for (int i = 0; i < length; i++) {
            value = (value << 1) | (bitAt(offset + i) ? 1L : 0L);
        }
        return value;
    }

    public int hammingDistance(BitSequence other) {
        Objects.requireNonNull(other, "other");
        if (bitLength != other.bitLength) throw new IllegalArgumentException("bit lengths differ");
        int distance = 0;
        for (int i = 0; i < packedBits.length; i++) {
            distance += Integer.bitCount((packedBits[i] ^ other.packedBits[i]) & 0xFF);
        }
        return distance;
    }

    public String toBitString() {
        StringBuilder sb = new StringBuilder(bitLength);
        for (int i = 0; i < bitLength; i++) sb.append(bitAt(i) ? '1' : '0');
        return sb.toString();
    }

    public static int packedLength(int bitLength) {
        if (bitLength < 0) throw new IllegalArgumentException("bitLength must be >= 0");
        return (bitLength + 7) >>> 3;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= bitLength) throw new IndexOutOfBoundsException(index);
    }

    private static void set(byte[] bytes, int bitIndex, boolean value) {
        int mask = 1 << (7 - (bitIndex & 7));
        if (value) bytes[bitIndex >>> 3] |= (byte) mask;
        else bytes[bitIndex >>> 3] &= (byte) ~mask;
    }

    private static void copyBits(BitSequence source, int sourceOffset, byte[] destination, int destinationOffset, int length) {
        for (int i = 0; i < length; i++) {
            if (source.bitAt(sourceOffset + i)) set(destination, destinationOffset + i, true);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BitSequence that)) return false;
        return bitLength == that.bitLength && Arrays.equals(packedBits, that.packedBits);
    }

    @Override
    public int hashCode() {
        return 31 * bitLength + Arrays.hashCode(packedBits);
    }

    @Override
    public String toString() {
        return toBitString();
    }
}
