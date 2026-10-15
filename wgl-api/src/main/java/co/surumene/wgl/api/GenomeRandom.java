package co.surumene.wgl.api;

public interface GenomeRandom {
    long nextLong();
    double nextDouble();
    int nextInt(int bound);
    boolean nextBoolean();
}
