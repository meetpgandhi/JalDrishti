package org.jaldrishti.domain;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Rule R8 — Observer Reports.
 *
 * <p>Fires when community or gauge observer reports corroborate a flood condition:
 * <ul>
 *   <li>One OBSERVER report + any sensor evidence (R1, R2, or R3) → WARNING</li>
 *   <li>Two independent OBSERVER reports within 30 min and 20 km → WARNING</li>
 * </ul>
 */
public final class R8ObserverRule {

    public static final String RULE_ID = "R8";

    private R8ObserverRule() {}

    public static RuleResult evaluate(
            FieldReportObservation currentReport,
            List<FieldReportObservation> priorObserverReports,
            Map<String, Site> sitesById,
            boolean sensorEvidencePresent,
            CorridorConfig config
    ) {
        Objects.requireNonNull(currentReport, "currentReport must not be null");
        Objects.requireNonNull(config, "CorridorConfig must not be null");

        if (currentReport.trust() != SenderTrust.OBSERVER) {
            return RuleResult.notFired(RULE_ID, "Report is not from an OBSERVER sender: " + currentReport.trust());
        }

        double confidenceFloor = config.parseConfidenceFloor();
        if (currentReport.confidence() < confidenceFloor) {
            return RuleResult.notFired(RULE_ID, "Report confidence below floor: " + currentReport.confidence());
        }

        if (!currentReport.isHazardous()) {
            return RuleResult.notFired(RULE_ID, "Report does not describe flood hazard: " + currentReport.hazardType());
        }

        // Branch 1: One OBSERVER report + any sensor evidence (R1, R2, or R3)
        if (sensorEvidencePresent) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("reportId", currentReport.reportId().toString());
            evidence.put("senderHash", currentReport.senderHash());
            evidence.put("hazardType", currentReport.hazardType().name());
            evidence.put("locationId", currentReport.locationId());
            evidence.put("sensorEvidencePresent", true);

            return RuleResult.fired(
                    RULE_ID,
                    IncidentLevel.WARNING,
                    AlertTag.D,
                    String.format("Observer report from %s at %s corroborated by active sensor evidence",
                            currentReport.senderHash(), currentReport.locationId()),
                    evidence
            );
        }

        // Branch 2: Two independent OBSERVER reports within 30 min and 20 km
        if (priorObserverReports != null && !priorObserverReports.isEmpty()) {
            for (FieldReportObservation prior : priorObserverReports) {
                if (prior.reportId().equals(currentReport.reportId())) {
                    continue;
                }
                if (prior.trust() != SenderTrust.OBSERVER) {
                    continue;
                }
                if (prior.senderHash().equals(currentReport.senderHash())) {
                    continue; // Must be independent senders
                }
                if (prior.confidence() < confidenceFloor || !prior.isHazardous()) {
                    continue;
                }

                long diffMin = Math.abs(Duration.between(prior.receivedAt(), currentReport.receivedAt()).toMinutes());
                if (diffMin > 30) {
                    continue;
                }

                double distanceKm = calculateDistanceKm(currentReport.locationId(), prior.locationId(), sitesById);
                if (distanceKm <= 20.0) {
                    Map<String, Object> evidence = new LinkedHashMap<>();
                    evidence.put("report1Id", prior.reportId().toString());
                    evidence.put("report2Id", currentReport.reportId().toString());
                    evidence.put("sender1Hash", prior.senderHash());
                    evidence.put("sender2Hash", currentReport.senderHash());
                    evidence.put("elapsedMinutes", diffMin);
                    evidence.put("distanceKm", distanceKm);

                    return RuleResult.fired(
                            RULE_ID,
                            IncidentLevel.WARNING,
                            AlertTag.D,
                            String.format("Two independent observer reports within %d min and %.1f km (%s and %s)",
                                    diffMin, distanceKm, prior.locationId(), currentReport.locationId()),
                            evidence
                    );
                }
            }
        }

        return RuleResult.notFired(RULE_ID, "Single observer report without sensor corroboration or secondary observer report");
    }

    private static double calculateDistanceKm(String siteId1, String siteId2, Map<String, Site> sitesById) {
        if (siteId1 == null || siteId2 == null) {
            return 0.0; // Assume same general area if location unresolved
        }
        if (siteId1.equals(siteId2)) {
            return 0.0;
        }
        if (sitesById != null) {
            Site s1 = sitesById.get(siteId1);
            Site s2 = sitesById.get(siteId2);
            if (s1 != null && s2 != null) {
                return Math.abs(s1.riverKm() - s2.riverKm());
            }
        }
        return 0.0;
    }
}
