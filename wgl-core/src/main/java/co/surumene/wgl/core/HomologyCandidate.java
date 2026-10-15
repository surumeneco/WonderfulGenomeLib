package co.surumene.wgl.core;
public record HomologyCandidate(int positionA, int positionB, HomologyOrientation orientation, int hammingDistance) {
    public HomologyCandidate { if(positionA<0||positionB<0||hammingDistance<0||hammingDistance>2)throw new IllegalArgumentException(); }
    public double anchorQuality(){ return switch(hammingDistance){case 0->1.0;case 1->0.75;case 2->0.50;default->throw new IllegalStateException();}; }
}
