package org.jaldrishti.sim.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Typed scenario representation loaded from scenarios/*.yaml.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Scenario(
        String scenario,
        int version,
        String corridor,
        int configVersion,
        String start,
        String end,
        long seed,
        Map<String, Object> variants,
        List<SpeedBand> speedProfile,
        List<Bookmark> bookmarks,
        Map<String, StationConfig> stations,
        WeatherConfig weather,
        List<ScenarioEvent> events,
        List<Map<String, Object>> asHappened,
        Map<String, Map<String, Object>> groundTruthArrivals
) {
    public Scenario {
        variants = variants == null ? Collections.emptyMap() : Map.copyOf(variants);
        speedProfile = speedProfile == null ? Collections.emptyList() : List.copyOf(speedProfile);
        bookmarks = bookmarks == null ? Collections.emptyList() : List.copyOf(bookmarks);
        stations = stations == null ? Collections.emptyMap() : Map.copyOf(stations);
        events = events == null ? Collections.emptyList() : List.copyOf(events);
        asHappened = asHappened == null ? Collections.emptyList() : List.copyOf(asHappened);
        groundTruthArrivals = groundTruthArrivals == null ? Collections.emptyMap() : Map.copyOf(groundTruthArrivals);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpeedBand(String from, String to, double speed) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bookmark(String at, String label) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StationConfig(
            Map<String, Object> generator,
            List<PinnedReading> pinned,
            OutageConfig outage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PinnedReading(String t, double level, String tag, String src) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutageConfig(String fromSlot, boolean afterLastPinned, String reason, String tag) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WeatherConfig(Map<String, Object> catchmentRainMm3h) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScenarioEvent(
            String t,
            String type,
            Double magnitude,
            Double lat,
            Double lon,
            Integer publishDelayMin,
            String sender,
            String trust,
            String lang,
            String text,
            String tag,
            String enabledWhen
    ) {}
}
