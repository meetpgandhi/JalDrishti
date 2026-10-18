package org.jaldrishti.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Rule R11 — Auto-Cancel.
 *
 * <p>Fires when evidence demonstrates that a silent gauge condition did not indicate a flood:
 * <ul>
 *   <li>The silent station returns with a normal reading (below warning threshold).</li>
 *   <li>An OBSERVER at that site explicitly reports a normal river ({@code NO_HAZARD}).</li>
 *   <li>The downstream cascade order-consistency window passes with no downstream alert.</li>
 * </ul>
 *
 * <p><strong>CRITICAL RULE:</strong> R11 closes a {@code WATCH} ONLY.
 * A {@code WARNING} never cancels automatically; it can only be stood down by a forecaster.
 */
public final class R11AutoCancelRule {

    public static final String RULE_ID = "R11";

    private R11AutoCancelRule() {}

    public static RuleResult evaluate(
            Incident incident,
            Station station,
            Reading newReading,
            FieldReportObservation observerReport,
            boolean downstreamWindowPassed
    ) {
        Objects.requireNonNull(incident, "Incident must not be null");

        // Rule R11 strictly applies ONLY to internal WATCH incidents.
        if (incident.level() != IncidentLevel.WATCH) {
            return RuleResult.notFired(RULE_ID, "Auto-cancel applies only to WATCH incidents (current level: " + incident.level() + ")");
        }

        // Branch 1: Silent station returns with a normal reading
        if (newReading != null && station != null) {
            boolean normal = station.warningM() == null || newReading.levelM() < station.warningM();
            if (normal) {
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("stationId", station.id());
                evidence.put("levelM", newReading.levelM());
                evidence.put("warningM", station.warningM());
                evidence.put("observedAt", newReading.observedAt().toString());

                return RuleResult.fired(
                        RULE_ID,
                        IncidentLevel.STAND_DOWN,
                        AlertTag.D,
                        String.format("Silent station %s returned with normal reading %.2fm; closing internal WATCH",
                                station.id(), newReading.levelM()),
                        evidence
                );
            }
        }

        // Branch 2: Observer reports normal river (NO_HAZARD)
        if (observerReport != null && observerReport.hazardType() == HazardType.NO_HAZARD) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("reportId", observerReport.reportId().toString());
            evidence.put("senderHash", observerReport.senderHash());
            evidence.put("locationId", observerReport.locationId());
            evidence.put("hazardType", observerReport.hazardType().name());

            return RuleResult.fired(
                    RULE_ID,
                    IncidentLevel.STAND_DOWN,
                    AlertTag.D,
                    String.format("Observer at %s confirmed normal river conditions (NO_HAZARD); closing internal WATCH",
                            observerReport.locationId()),
                    evidence
            );
        }

        // Branch 3: Downstream arrival window passed with no corroborating evidence
        if (downstreamWindowPassed) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("downstreamWindowPassed", true);

            return RuleResult.fired(
                    RULE_ID,
                    IncidentLevel.STAND_DOWN,
                    AlertTag.D,
                    "Downstream cascade consistency window passed with no downstream silence or rate alert; closing internal WATCH",
                    evidence
            );
        }

        return RuleResult.notFired(RULE_ID, "No auto-cancel conditions met");
    }
}
