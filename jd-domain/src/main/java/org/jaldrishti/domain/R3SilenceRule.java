package org.jaldrishti.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Rule R3 — Silence Detection.
 *
 * <p>Evaluated on a periodic sweep (default: every 60s [A]).
 * <p>Fires when a station's expected reading is overdue by more than the grace period:
 * {@code now > lastGoodReadingAt + cadenceMin + graceMin}.
 *
 * <p>Result: Internal WATCH (notifies forecaster and nearest gauge reader only).
 * Station state transitions to SILENT.
 */
public final class R3SilenceRule {

    public static final String RULE_ID = "R3";

    private R3SilenceRule() {}

    public static RuleResult evaluate(
            Station station,
            Instant lastGoodReadingAt,
            Instant now,
            CorridorConfig config
    ) {
        Objects.requireNonNull(station, "Station must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(config, "CorridorConfig must not be null");

        if (lastGoodReadingAt == null) {
            return RuleResult.notFired(RULE_ID, "No last good reading available for station " + station.id());
        }

        int cadenceMin = station.cadenceMin();
        int graceMin = config.graceMin();

        Instant expectedSlot = lastGoodReadingAt.plus(Duration.ofMinutes(cadenceMin));
        Instant overdueThreshold = expectedSlot.plus(Duration.ofMinutes(graceMin));

        if (now.isAfter(overdueThreshold)) {
            long overdueSec = Duration.between(overdueThreshold, now).toSeconds();

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("stationId", station.id());
            evidence.put("lastGoodReadingAt", lastGoodReadingAt.toString());
            evidence.put("firstMissedSlotAt", expectedSlot.toString());
            evidence.put("overdueThreshold", overdueThreshold.toString());
            evidence.put("now", now.toString());
            evidence.put("cadenceMin", cadenceMin);
            evidence.put("graceMin", graceMin);
            evidence.put("overdueSeconds", overdueSec);

            return RuleResult.fired(
                    RULE_ID,
                    IncidentLevel.WATCH,
                    AlertTag.D,
                    String.format("Station %s is SILENT: last reading was at %s, overdue by %d sec (grace %d min)",
                            station.id(), lastGoodReadingAt, overdueSec, graceMin),
                    evidence
            );
        }

        return RuleResult.notFired(
                RULE_ID,
                String.format("Station %s is within grace window (next overdue threshold: %s)",
                        station.id(), overdueThreshold)
        );
    }
}
