package org.jaldrishti.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Rule R2 — Rate of Rise.
 *
 * <p>Fires when a station's water level rises faster than configured thresholds over
 * 10, 30, or 60 minute intervals.
 * <ul>
 *   <li>Default thresholds [A]: 0.5m/10min, 0.8m/30min, 1.0m/60min</li>
 *   <li>Under heightened watch (Rule R10): thresholds multiplied by 0.5</li>
 *   <li>Result: WATCH + sets station RATE_ALERT flag</li>
 * </ul>
 */
public final class R2RateRule {

    public static final String RULE_ID = "R2";

    private R2RateRule() {}

    public static RuleResult evaluate(
            Reading currentReading,
            List<Reading> historicalReadings,
            CorridorConfig config,
            boolean heightenedWatch
    ) {
        Objects.requireNonNull(currentReading, "currentReading must not be null");
        Objects.requireNonNull(config, "config must not be null");

        double mult = heightenedWatch ? config.heightenedMultiplier() : 1.0;
        double th10 = config.rateThreshold10Min() * mult;
        double th30 = config.rateThreshold30Min() * mult;
        double th60 = config.rateThreshold60Min() * mult;

        Instant now = currentReading.observedAt();
        double currentLevel = currentReading.levelM();

        if (historicalReadings != null) {
            for (Reading past : historicalReadings) {
                if (!past.stationId().equals(currentReading.stationId())) {
                    continue;
                }
                if (past.observedAt().isAfter(now) || past.observedAt().equals(now)) {
                    continue;
                }

                long diffMinutes = Duration.between(past.observedAt(), now).toMinutes();
                double rise = currentLevel - past.levelM();

                boolean breached = false;
                int window = 0;
                double threshold = 0.0;

                if (diffMinutes <= 10 && rise >= th10) {
                    breached = true;
                    window = 10;
                    threshold = th10;
                } else if (diffMinutes <= 30 && rise >= th30) {
                    breached = true;
                    window = 30;
                    threshold = th30;
                } else if (diffMinutes <= 60 && rise >= th60) {
                    breached = true;
                    window = 60;
                    threshold = th60;
                }

                if (breached) {
                    Map<String, Object> evidence = new LinkedHashMap<>();
                    evidence.put("stationId", currentReading.stationId());
                    evidence.put("currentObservedAt", now.toString());
                    evidence.put("currentLevelM", currentLevel);
                    evidence.put("pastObservedAt", past.observedAt().toString());
                    evidence.put("pastLevelM", past.levelM());
                    evidence.put("riseM", rise);
                    evidence.put("elapsedMinutes", diffMinutes);
                    evidence.put("windowMinutes", window);
                    evidence.put("thresholdM", threshold);
                    evidence.put("heightenedWatch", heightenedWatch);

                    return RuleResult.fired(
                            RULE_ID,
                            IncidentLevel.WATCH,
                            AlertTag.D,
                            String.format("Station %s rose %.2fm in %d min (exceeded %d-min threshold of %.2fm)",
                                    currentReading.stationId(), rise, diffMinutes, window, threshold),
                            evidence
                    );
                }
            }
        }

        return RuleResult.notFired(
                RULE_ID,
                String.format("Station %s rate of rise is within normal limits", currentReading.stationId())
        );
    }
}
