package org.jaldrishti.domain;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Result of evaluating a trigger rule against hydrological telemetry or field evidence.
 *
 * @param ruleId            Identifier of the rule (e.g. "R1", "R5")
 * @param fired             Whether the rule conditions were satisfied
 * @param resultingLevel    Alert level produced by the rule, or null if no level change
 * @param tag               Provenance tag ([P] published, [D] derived, [A] assumed)
 * @param description       Human-readable explanation of why the rule fired or did not fire
 * @param evidence          Structured map of inputs, parameters, and calculation details
 * @param nonRain           Whether rule tagged the incident as non-rain flood (Rule R6)
 * @param suppressesSilence Whether rule suppresses silence checks for an outage group (Rule R4)
 */
public record RuleResult(
        String ruleId,
        boolean fired,
        IncidentLevel resultingLevel,
        AlertTag tag,
        String description,
        Map<String, Object> evidence,
        boolean nonRain,
        boolean suppressesSilence
) {
    public RuleResult {
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        Objects.requireNonNull(tag, "tag must not be null");
        evidence = evidence == null ? Collections.emptyMap() : Collections.unmodifiableMap(evidence);
    }

    public static RuleResult notFired(String ruleId, String reason) {
        return new RuleResult(
                ruleId,
                false,
                null,
                AlertTag.D,
                reason,
                Collections.emptyMap(),
                false,
                false
        );
    }

    public static RuleResult fired(
            String ruleId,
            IncidentLevel level,
            AlertTag tag,
            String description,
            Map<String, Object> evidence
    ) {
        return new RuleResult(
                ruleId,
                true,
                level,
                tag,
                description,
                evidence,
                false,
                false
        );
    }

    public static RuleResult firedNonRain(
            String ruleId,
            IncidentLevel level,
            AlertTag tag,
            String description,
            Map<String, Object> evidence
    ) {
        return new RuleResult(
                ruleId,
                true,
                level,
                tag,
                description,
                evidence,
                true,
                false
        );
    }

    public static RuleResult firedSuppression(
            String ruleId,
            String description,
            Map<String, Object> evidence
    ) {
        return new RuleResult(
                ruleId,
                true,
                null,
                AlertTag.D,
                description,
                evidence,
                false,
                true
        );
    }
}
