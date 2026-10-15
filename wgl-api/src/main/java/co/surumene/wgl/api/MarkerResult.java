package co.surumene.wgl.api;

import java.util.List;
import java.util.Locale;

public record MarkerResult(List<Integer> segments) {
    public MarkerResult {
        segments = List.copyOf(segments);
        for (int segment : segments) if (segment < 0 || segment > 0xFF) throw new IllegalArgumentException("segment must be byte-sized");
    }

    public String formatted() {
        return segments.stream().map(v -> String.format(Locale.ROOT, "%02X", v)).collect(java.util.stream.Collectors.joining("-"));
    }

    @Override public String toString() { return formatted(); }
}
