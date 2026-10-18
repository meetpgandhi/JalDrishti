package org.jaldrishti.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Rule R5 — Cascade Corroboration (THE CRITICAL RULE).
 *
 * <p>Fires when an upstream station i goes SILENT, followed downstream by a station j
 * that is either SILENT or has a RATE_ALERT, and station j's evidence overlaps the physical
 * flood wave order-consistency window:
 *
 * <pre>
 *   t_earliest_j = t_lastGood_i   + D_ij / v_max   (v_max = 35 m/s)
 *   t_latest_j   = t_firstMissed_i + D_ij / v_min   (v_min = 3 m/s)
 * </pre>
 *
 * <p>Evidence test:
 * <ul>
 *   <li>For SILENT j: loss interval (t_lastGood_j, t_firstMissed_j] overlaps [t_earliest_j, t_latest_j]</li>
 *   <li>For RATE_ALERT j: reading timestamp t_rateAlert_j lies in [t_earliest_j, t_latest_j]</li>
 * </ul>
 *
 * <p>Result: WARNING (corridor-level, [D]).
 */
public final class R5CascadeRule {

    public static final String RULE_ID = "R5";

    private R5CascadeRule() {}

    /**
     * Evaluates whether upstream station i and downstream station j satisfy cascade corroboration.
     *
     * @param upstreamStation    Upstream gauge station i
     * @param upstreamLastGood   t_lastGood_i
     * @param upstreamFirstMissed t_firstMissed_i
     * @param downstreamStation  Downstream gauge station j (must have riverKm > upstreamStation.riverKm())
     * @param downstreamState    Current observation state of downstream station j
     * @param config             CorridorConfig containing vMinMs and vMaxMs
     * @return RuleResult indicating if R5 fired
     */
    public static RuleResult evaluate(
            Station upstreamStation,
            Instant upstreamLastGood,
            Instant upstreamFirstMissed,
            Station downstreamStation,
            StationObservationState downstreamState,
            CorridorConfig config
    ) {
        Objects.requireNonNull(upstreamStation, "upstreamStation must not be null");
        Objects.requireNonNull(upstreamLastGood, "upstreamLastGood must not be null");
        Objects.requireNonNull(upstreamFirstMissed, "upstreamFirstMissed must not be null");
        Objects.requireNonNull(downstreamStation, "downstreamStation must not be null");
        Objects.requireNonNull(downstreamState, "downstreamState must not be null");
        Objects.requireNonNull(config, "CorridorConfig must not be null");

        // Station j must be strictly downstream of station i
        double distanceKm = downstreamStation.riverKm() - upstreamStation.riverKm();
        if (distanceKm <= 0) {
            return RuleResult.notFired(
                    RULE_ID,
                    String.format("Station %s (km %.1f) is not downstream of %s (km %.1f)",
                            downstreamStation.id(), downstreamStation.riverKm(),
                            upstreamStation.id(), upstreamStation.riverKm())
            );
        }

        double distanceMeters = distanceKm * 1000.0;
        double vMin = config.vMinMs();
        double vMax = config.vMaxMs();

        // Window calculation:
        // t_earliest_j = t_lastGood_i + D_ij / v_max
        // t_latest_j   = t_firstMissed_i + D_ij / v_min
        long earliestSec = Math.round(distanceMeters / vMax);
        long latestSec = Math.round(distanceMeters / vMin);

        Instant tEarliestJ = upstreamLastGood.plus(Duration.ofSeconds(earliestSec));
        Instant tLatestJ = upstreamFirstMissed.plus(Duration.ofSeconds(latestSec));

        boolean overlaps = false;
        String evidenceType = null;

        if (downstreamState.silent() && downstreamState.lastGoodReadingAt() != null
                && downstreamState.firstMissedSlotAt() != null) {
            evidenceType = "SILENCE";
            Instant jLastGood = downstreamState.lastGoodReadingAt();
            Instant jFirstMissed = downstreamState.firstMissedSlotAt();

            // Interval (jLastGood, jFirstMissed] overlaps [tEarliestJ, tLatestJ]
            // iff jFirstMissed >= tEarliestJ and jLastGood <= tLatestJ
            boolean afterStart = !jFirstMissed.isBefore(tEarliestJ);
            boolean beforeEnd = !jLastGood.isAfter(tLatestJ);
            overlaps = afterStart && beforeEnd;

        } else if (downstreamState.rateAlert() && downstreamState.rateAlertAt() != null) {
            evidenceType = "RATE_ALERT";
            Instant jRateAlertAt = downstreamState.rateAlertAt();

            // Timestamp jRateAlertAt in [tEarliestJ, tLatestJ]
            boolean afterStart = !jRateAlertAt.isBefore(tEarliestJ);
            boolean beforeEnd = !jRateAlertAt.isAfter(tLatestJ);
            overlaps = afterStart && beforeEnd;
        }

        if (overlaps) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("upstreamStationId", upstreamStation.id());
            evidence.put("upstreamLastGoodAt", upstreamLastGood.toString());
            evidence.put("upstreamFirstMissedAt", upstreamFirstMissed.toString());
            evidence.put("downstreamStationId", downstreamStation.id());
            evidence.put("distanceMeters", distanceMeters);
            evidence.put("vMinMs", vMin);
            evidence.put("vMaxMs", vMax);
            evidence.put("tEarliestDownstream", tEarliestJ.toString());
            evidence.put("tLatestDownstream", tLatestJ.toString());
            evidence.put("downstreamEvidenceType", evidenceType);

            return RuleResult.fired(
                    RULE_ID,
                    IncidentLevel.WARNING,
                    AlertTag.D,
                    String.format("Cascade corroboration between %s and downstream %s: evidence %s overlaps order-consistency window [%s, %s]",
                            upstreamStation.id(), downstreamStation.id(), evidenceType, tEarliestJ, tLatestJ),
                    evidence
            );
        }

        return RuleResult.notFired(
                RULE_ID,
                String.format("No cascade corroboration between %s and %s: downstream state is not order-consistent (window: [%s, %s])",
                        upstreamStation.id(), downstreamStation.id(), tEarliestJ, tLatestJ)
        );
    }
}
