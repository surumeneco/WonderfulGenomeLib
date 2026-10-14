package co.surumene.wgl.core;

/** Invocation-local, unboxed memoization of exact Homology gap weights. */
final class GapWeightCache {
    private int[] keys = new int[16];
    private double[] values = new double[16];
    private int size;

    double get(int displacement) {
        int key = displacement + 1;
        int slot = bucket(key, keys.length);
        while (keys[slot] != 0) {
            if (keys[slot] == key) return values[slot];
            slot = (slot + 1) & (keys.length - 1);
        }
        double value = StrictMath.exp(-displacement / 256.0);
        keys[slot] = key;
        values[slot] = value;
        if (++size * 2 >= keys.length) grow();
        return value;
    }

    private void grow() {
        int[] oldKeys = keys;
        double[] oldValues = values;
        keys = new int[oldKeys.length << 1];
        values = new double[keys.length];
        for (int i = 0; i < oldKeys.length; i++) {
            int key = oldKeys[i];
            if (key == 0) continue;
            int slot = bucket(key, keys.length);
            while (keys[slot] != 0) slot = (slot + 1) & (keys.length - 1);
            keys[slot] = key;
            values[slot] = oldValues[i];
        }
    }

    private static int bucket(int key, int length) {
        int mixed = key * 0x9E3779B9;
        return (mixed ^ (mixed >>> 16)) & (length - 1);
    }
}
