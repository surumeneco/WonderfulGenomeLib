package co.surumene.wgl.api;

/** Consumer-supplied founder chromosome-length tolerance relative to a chromosome template baseline. */
public record FounderScaffoldTolerance(
        double standardDeviationRatio,
        double minLengthRatio,
        double maxLengthRatio) {

    public FounderScaffoldTolerance {
        if (!Double.isFinite(standardDeviationRatio) || standardDeviationRatio < 0.0) {
            throw new IllegalArgumentException("standardDeviationRatio must be finite and >= 0");
        }
        if (!Double.isFinite(minLengthRatio) || !Double.isFinite(maxLengthRatio)
                || minLengthRatio <= 0.0
                || minLengthRatio > 1.0
                || maxLengthRatio < 1.0
                || minLengthRatio > maxLengthRatio) {
            throw new IllegalArgumentException("length ratios must satisfy 0 < min <= 1 <= max");
        }
        if (standardDeviationRatio == 0.0
                && (Double.compare(minLengthRatio, 1.0) != 0
                || Double.compare(maxLengthRatio, 1.0) != 0)) {
            throw new IllegalArgumentException("zero standard deviation requires exact 1.0 length ratios");
        }
    }

    public static FounderScaffoldTolerance exact() {
        return new FounderScaffoldTolerance(0.0, 1.0, 1.0);
    }

    public boolean exactLength() {
        return standardDeviationRatio == 0.0;
    }
}
