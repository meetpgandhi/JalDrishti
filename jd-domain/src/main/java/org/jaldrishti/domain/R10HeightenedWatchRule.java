package org.jaldrishti.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Rule R10 — Heightened Watch.
 *
 * <p>Declared by the forecaster when an upstream hazard is known (e.g., dammed landslide,
 * glacial lake moraine instability, or second wave after initial breach).
 * <ul>
 *   <li>Effect 1: Rate of rise (Rule R2) thresholds are halved (multiplied by 0.5).</li>
 *   <li>Effect 2: Rule R3 silence at the uppermost station directly triggers a WARNING
 *       without requiring downstream cascade corroboration.</li>
 * </ul>
 */
public final class R10HeightenedWatchRule {

    public static final String RULE_ID = "R10";

    private R10HeightenedWatchRule() {}

    public static RuleResult evaluate(
            boolean heightenedWatchActive,
            Station station,
            boolean isTopStation,
            boolean r3FiredAtStation
    ) {
        Objects.requireNonNull(station, "station must not be null");

        if (!heightenedWatchActive) {
            return RuleResult.notFired(RULE_ID, "Heightened watch is not currently active");
        }

        if (isTopStation && r3FiredAtStation) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("stationId", station.id());
            evidence.put("riverKm", station.riverKm());
            evidence.put("heightenedWatchActive", true);
            evidence.put("isTopStation", true);
            evidence.put("r3Fired", true);

            return RuleResult.fired(
                    RULE_ID,
                    IncidentLevel.WARNING,
                    AlertTag.D,
                    String.format("Heightened watch active: top station %s went SILENT; directly elevated to WARNING without downstream corroboration",
                            station.id()),
                    evidence
            );
        }

        return RuleResult.notFired(
                RULE_ID,
                String.format("Heightened watch active but conditions for direct warning not met (topStation=%b, r3Fired=%b)",
                        isTopStation, r3FiredAtStation)
        );
    }
}
