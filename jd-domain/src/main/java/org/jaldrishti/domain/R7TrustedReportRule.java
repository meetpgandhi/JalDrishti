package org.jaldrishti.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Rule R7 — Trusted Report (OFFICIAL).
 *
 * <p>Fires when a field report arrives from an OFFICIAL registered sender (e.g., District
 * Administration Office, hydropower operator, local police) with parse confidence ≥ 0.7 [A]
 * reporting an active flood hazard.
 *
 * <p>Result: WARNING ([P] published source).
 */
public final class R7TrustedReportRule {

    public static final String RULE_ID = "R7";

    private R7TrustedReportRule() {}

    public static RuleResult evaluate(
            FieldReportObservation report,
            CorridorConfig config
    ) {
        Objects.requireNonNull(report, "report must not be null");
        Objects.requireNonNull(config, "CorridorConfig must not be null");

        if (report.trust() != SenderTrust.OFFICIAL) {
            return RuleResult.notFired(RULE_ID, "Report sender trust is " + report.trust() + " (requires OFFICIAL)");
        }

        double confidenceFloor = config.parseConfidenceFloor();
        if (report.confidence() < confidenceFloor) {
            return RuleResult.notFired(
                    RULE_ID,
                    String.format("Report confidence %.2f is below threshold %.2f", report.confidence(), confidenceFloor)
            );
        }

        if (!report.isHazardous()) {
            return RuleResult.notFired(RULE_ID, "Report does not indicate active flood hazard: " + report.hazardType());
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("reportId", report.reportId().toString());
        evidence.put("senderHash", report.senderHash());
        evidence.put("trust", report.trust().name());
        evidence.put("hazardType", report.hazardType().name());
        evidence.put("locationId", report.locationId());
        evidence.put("confidence", report.confidence());
        evidence.put("translationEn", report.translationEn());

        return RuleResult.fired(
                RULE_ID,
                IncidentLevel.WARNING,
                AlertTag.P,
                String.format("Official field report received from %s (%s): %s at %s (confidence %.2f)",
                        report.senderHash(), report.channel(), report.hazardType(),
                        report.locationId() != null ? report.locationId() : report.locationText(), report.confidence()),
                evidence
        );
    }
}
