package co.surumene.wgl.api;

public interface GenomeSequenceCodec {
    record TextView(String text, String residualBits) {
        public TextView {
            if (text == null || residualBits == null || !residualBits.matches("[01]*")) throw new IllegalArgumentException("invalid text view");
        }
    }
    BitSequence decodeBits(String bits);
    BitSequence decodeHex(String hex);
    BitSequence decodeDna(String dna);
    String encodeBits(BitSequence bits);
    TextView encodeHex(BitSequence bits);
    TextView encodeDna(BitSequence bits);
}
