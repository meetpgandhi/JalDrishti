package org.jaldrishti.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Rule R9 — Seismic Hint (Could — FR-19).
 *
 * <p>Fires when a seismic event of magnitude M ≥ 3.5 occurs inside the watched
 * headwater polygon.
 * <ul>
 *   <li>By default: opens internal WATCH</li>
 *   <li>If Rule R3 silence fires at an upstream station within 30 min: promotes to WARNING</li>
 * </ul>
 */
public final class R9SeismicRule {

    public static final String RULE_ID = "R9";

    // Watched headwater bounding polygon (Bhote Koshi / Langtang headwaters) [A]
    public static final double MIN_LAT = 28.0;
    public static final double MAX_LAT = 28.6;
    public static final double MIN_LON = 28.0; // wait, lon in Nepal is ~85
    public static final double MIN_LONGITUDE = 85.1;
    public static final double MAX_LONGITUDE = 85.8;

    private R9SeismicRule() {}

    public static RuleResult evaluate(SeismicObservation seismic, boolean r3FiredWithin30Min) {
        Objects.requireNonNull(seismic, "seismic must not be null");

        if (seismic.magnitude() < 3.5) {
            return RuleResult.notFired(RULE_ID, "Seismic magnitude " + seismic.magnitude() + " < 3.5");
        }

        boolean insidePolygon = seismic.lat() >= MIN_LAT && seismic.lat() <= MAX_LAT
                && seismic.lon() >= MIN_LONGITUDE && seismic.lon() <= MAX_LONGITUDE;

        if (!insidePolygon) {
            return RuleResult.notFired(
                    RULE_ID,
                    String.format("Seismic event (%.4f, %.4f) outside watched headwater polygon",
                            seismic.lat(), seismic.lon())
            );
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("eventId", seismic.eventId());
        evidence.put("magnitude", seismic.magnitude());
        evidence.put("lat", seismic.lat());
        evidence.put("lon", seismic.lon());
        evidence.put("originTime", seismic.originTime().toString());
        evidence.put("r3FiredWithin30Min", r3FiredWithin30Min);

        if (r3FiredWithin30Min) {
            return RuleResult.fired(
                    RULE_ID,
                    IncidentLevel.WARNING,
                    AlertTag.D,
                    String.format("Seismic event M%.1f in headwater corroborated by gauge silence within 30 min",
                            seismic.magnitude()),
                    evidence
            );
        }

        return RuleResult.fired(
                RULE_ID,
                IncidentLevel.WATCH,
                AlertTag.D,
                String.format("Seismic event M%.1f detected in watched headwater polygon", seismic.magnitude()),
                evidence
        );
    }
}
