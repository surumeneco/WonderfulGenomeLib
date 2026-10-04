package co.surumene.wgl.api;

/** Profile-owned numeric meaning of a non-regulation direct gene. */
public interface DirectContributionModel {
    /** Signed, unregulated d0 for the raw gene fields. */
    double baseEffect(GenomeAddress address, boolean negative, int magnitudeCode, int expressionCode);

    /** Convert absolute final effect |d| into bounded contribution u in [0,1). */
    double saturation(GenomeAddress address, double absoluteFinalEffect);

    /** Select the magnitude code that most closely produces desired absolute base effect for the given expression. */
    int closestMagnitudeCode(GenomeAddress address, double desiredAbsoluteBaseEffect, int expressionCode);
}
