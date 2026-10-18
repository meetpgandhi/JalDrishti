package org.jaldrishti.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Rule R1 — Height Band.
 *
 * <p>Fires when a station's observed water level crosses its calibrated stage thresholds:
 * <ul>
 *   <li>reading >= danger_m  → WARNING (direct)</li>
 *   <li>reading >= warning_m → WATCH</li>
 * </ul>
 */
public final class R1HeightRule {

    public static final String RULE_ID = "R1";

    private R1HeightRule() {}

    public static RuleResult evaluate(Reading reading, Station station) {
        Objects.requireNonNull(reading, "Reading must not be null");
        Objects.requireNonNull(station, "Station must not be null");

        double level = reading.levelM();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("stationId", station.id());
        evidence.put("observedAt", reading.observedAt().toString());
        evidence.put("levelM", level);
        evidence.put("warningM", station.warningM());
        evidence.put("dangerM", station.dangerM());

        if (station.dangerM() != null && level >= station.dangerM()) {
            return RuleResult.fired(
                    RULE_ID,
                    IncidentLevel.WARNING,
                    reading.tag(),
                    String.format("Station %s water level %.2fm exceeded danger threshold %.2fm",
                            station.id(), level, station.dangerM()),
                    evidence
            );
        }

        if (station.warningM() != null && level >= station.warningM()) {
            return RuleResult.fired(
                    RULE_ID,
                    IncidentLevel.WATCH,
                    reading.tag(),
                    String.format("Station %s water level %.2fm exceeded warning threshold %.2fm",
                            station.id(), level, station.warningM()),
                    evidence
            );
        }

        return RuleResult.notFired(
                RULE_ID,
                String.format("Station %s water level %.2fm is below warning threshold (%s)",
                        station.id(), level, station.warningM() != null ? station.warningM() + "m" : "none")
        );
    }

    public static StationState determineBand(Reading reading, Station station) {
        Objects.requireNonNull(reading, "Reading must not be null");
        Objects.requireNonNull(station, "Station must not be null");

        double level = reading.levelM();
        if (station.dangerM() != null && level >= station.dangerM()) {
            return StationState.RED;
        }
        if (station.warningM() != null && level >= station.warningM()) {
            return StationState.AMBER;
        }
        return StationState.OK;
    }
}
