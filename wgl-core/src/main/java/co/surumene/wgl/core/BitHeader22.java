package co.surumene.wgl.core;

public record BitHeader22(int bits) {
    public BitHeader22 { if (bits < 0 || bits >= (1 << 22)) throw new IllegalArgumentException("22-bit header required"); }
    public boolean bit(int zeroBased) { if(zeroBased<0||zeroBased>=22)throw new IndexOutOfBoundsException(zeroBased); return ((bits >>> (21-zeroBased)) & 1) != 0; }
    public BitHeader22 flip(int zeroBased) { return new BitHeader22(bits ^ (1 << (21-zeroBased))); }
    public String toBitString(){ return String.format("%22s", Integer.toBinaryString(bits)).replace(' ','0'); }
}
