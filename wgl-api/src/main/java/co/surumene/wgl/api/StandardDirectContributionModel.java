package co.surumene.wgl.api;

/**
 * Reusable direct-effect model used by the current WWW profile: d0 = sign * alpha * m^gamma * e.
 * Saturation factor remains configurable per profile/address.
 */
public final class StandardDirectContributionModel implements DirectContributionModel {
    public static final double DEFAULT_ALPHA = -StrictMath.log(0.70);
    private final double alpha;
    private final double gamma;
    private final SaturationFactor saturationFactor;

    @FunctionalInterface
    public interface SaturationFactor { double factor(GenomeAddress address); }

    public StandardDirectContributionModel(double alpha, double gamma, SaturationFactor saturationFactor) {
        if (!Double.isFinite(alpha) || alpha <= 0) throw new IllegalArgumentException("alpha must be finite and > 0");
        if (!Double.isFinite(gamma) || gamma <= 0) throw new IllegalArgumentException("gamma must be finite and > 0");
        this.alpha = alpha;
        this.gamma = gamma;
        this.saturationFactor = java.util.Objects.requireNonNull(saturationFactor, "saturationFactor");
    }

    public static StandardDirectContributionModel defaultModel() {
        return new StandardDirectContributionModel(DEFAULT_ALPHA, 2.0, address -> 1.0);
    }

    @Override
    public double baseEffect(GenomeAddress address, boolean negative, int magnitudeCode, int expressionCode) {
        if (magnitudeCode < 0 || magnitudeCode > 127 || expressionCode < 0 || expressionCode > 15) {
            throw new IllegalArgumentException("raw gene codes out of range");
        }
        double m = magnitudeCode / 127.0;
        double e = expressionCode / 15.0;
        double d = alpha * StrictMath.pow(m, gamma) * e;
        return negative ? -d : d;
    }

    @Override
    public double saturation(GenomeAddress address, double absoluteFinalEffect) {
        if (!Double.isFinite(absoluteFinalEffect) || absoluteFinalEffect < 0) throw new IllegalArgumentException("effect must be finite and >= 0");
        double factor = saturationFactor.factor(address);
        if (!Double.isFinite(factor) || factor <= 0) throw new IllegalArgumentException("saturation factor must be finite and > 0");
        return -StrictMath.expm1(-factor * absoluteFinalEffect);
    }

    @Override
    public int closestMagnitudeCode(GenomeAddress address, double desiredAbsoluteBaseEffect, int expressionCode) {
        if (!Double.isFinite(desiredAbsoluteBaseEffect) || desiredAbsoluteBaseEffect < 0) {
            throw new IllegalArgumentException("desired effect must be finite and >= 0");
        }
        if (expressionCode <= 0 || expressionCode > 15) return 0;
        double e = expressionCode / 15.0;
        double m = StrictMath.pow(desiredAbsoluteBaseEffect / (alpha * e), 1.0 / gamma);
        int code = (int) StrictMath.round(m * 127.0);
        return Math.max(0, Math.min(127, code));
    }
}
