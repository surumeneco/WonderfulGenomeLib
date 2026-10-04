package co.surumene.wgl.core;

import co.surumene.wgl.api.*;

public final class GeneCodecV1 {
    public static final BitSequence START=BitSequence.fromBits("0100111011110100");
    public static final BitSequence END=BitSequence.fromBits("0001100100100001");
    private GeneCodecV1(){}
    public static BitSequence encode(GenomeAddress address, boolean negative, int magnitudeCode, int expressionCode, BitSequence extension){
        if(magnitudeCode<0||magnitudeCode>127||expressionCode<0||expressionCode>15)throw new IllegalArgumentException("gene field out of range");
        int raw=(negative?0x80:0)|GrayCode.encode7(magnitudeCode);
        return encodeRawEffect(address, raw, expressionCode, extension);
    }

    public static BitSequence encodeRawEffect(GenomeAddress address, int rawEffectByte, int expressionCode, BitSequence extension) {
        if (rawEffectByte < 0 || rawEffectByte > 255 || expressionCode < 0 || expressionCode > 15) {
            throw new IllegalArgumentException("gene field out of range");
        }
        BitHeader22 h=Secded22.encode(address);
        return START.concat(BitSequence.fromBits(h.toBitString())).concat(BitSequence.fromLong(rawEffectByte,8))
                .concat(BitSequence.fromLong(expressionCode,4)).concat(extension).concat(END);
    }
}
