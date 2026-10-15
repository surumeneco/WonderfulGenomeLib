package co.surumene.wgl.api;

import java.util.Locale;

public record GenomeAddress(int type, int target) implements Comparable<GenomeAddress> {
    public GenomeAddress {
        if (type < 0 || type > 0xFF || target < 0 || target > 0xFF) {
            throw new IllegalArgumentException("address bytes must be 0..255");
        }
    }

    public static GenomeAddress fromUnsignedShort(int value) {
        if (value < 0 || value > 0xFFFF) throw new IllegalArgumentException("value must be 0..65535");
        return new GenomeAddress((value >>> 8) & 0xFF, value & 0xFF);
    }

    public static GenomeAddress parse(String text) {
        if (text == null || !text.matches("(?i)[0-9a-f]{2}:[0-9a-f]{2}")) {
            throw new IllegalArgumentException("address must be TT:GG hex");
        }
        return new GenomeAddress(Integer.parseInt(text.substring(0, 2), 16), Integer.parseInt(text.substring(3, 5), 16));
    }

    public int unsignedShort() {
        return (type << 8) | target;
    }

    public boolean isRegulation() {
        return type == 0x08;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%02X:%02X", type, target);
    }

    @Override
    public int compareTo(GenomeAddress other) {
        return Integer.compareUnsigned(unsignedShort(), other.unsignedShort());
    }
}
