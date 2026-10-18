package org.jaldrishti.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Rule R4 — Telemetry Outage Filter.
 *
 * <p>Fires when more than a configured fraction (default: > 50% [A]) of stations in a
 * telemetry group become silent in the same slot.
 *
 * <p>Result: Suppresses Rule R3 silence alerts for that group, sets station states
 * to TELEMETRY_OUTAGE, opens an outage ticket, and emits ZERO warnings.
 */
public final class R4OutageRule {

    public static final String RULE_ID = "R4";

    private R4OutageRule() {}

    public static RuleResult evaluate(
            String telemetryGroup,
            List<StationObservationState> groupStationStates,
            CorridorConfig config,
            Instant now
    ) {
        Objects.requireNonNull(telemetryGroup, "telemetryGroup must not be null");
        Objects.requireNonNull(groupStationStates, "groupStationStates must not be null");
        Objects.requireNonNull(config, "CorridorConfig must not be null");

        if (groupStationStates.isEmpty()) {
            return RuleResult.notFired(RULE_ID, "No stations in telemetry group " + telemetryGroup);
        }

        int total = groupStationStates.size();
        List<String> silentStationIds = new ArrayList<>();

        for (StationObservationState state : groupStationStates) {
            if (state.silent() || state.telemetryOutage()) {
                silentStationIds.add(state.stationId());
            }
        }

        double fractionSilent = (double) silentStationIds.size() / total;
        double threshold = config.outageFraction();

        if (fractionSilent > threshold) {
            String ticketId = "TICKET-OUTAGE-" + telemetryGroup + "-" + (now != null ? now.getEpochSecond() : System.currentTimeMillis());

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("telemetryGroup", telemetryGroup);
            evidence.put("totalStations", total);
            evidence.put("silentStations", silentStationIds);
            evidence.put("fractionSilent", fractionSilent);
            evidence.put("thresholdFraction", threshold);
            evidence.put("outageTicket", ticketId);

            return RuleResult.firedSuppression(
                    RULE_ID,
                    String.format("Telemetry outage detected for group %s: %d of %d (%.1f%%) stations silent; R3 suppressed; ticket %s",
                            telemetryGroup, silentStationIds.size(), total, fractionSilent * 100.0, ticketId),
                    evidence
            );
        }

        return RuleResult.notFired(
                RULE_ID,
                String.format("Telemetry group %s is healthy: %d of %d (%.1f%%) stations silent (threshold %.1f%%)",
                        telemetryGroup, silentStationIds.size(), total, fractionSilent * 100.0, threshold * 100.0)
        );
    }
}
