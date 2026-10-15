package co.surumene.wgl.api;

import java.util.List;

public record AddressAggregate(double positiveSaturation, double negativeSurvival, double score,
                               List<EffectiveContribution> contributions) {
    public AddressAggregate {
        contributions = List.copyOf(contributions);
    }

    public static AddressAggregate empty() {
        return new AddressAggregate(0.0, 1.0, 0.0, List.of());
    }
}
