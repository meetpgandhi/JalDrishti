package org.jaldrishti.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Audit explainability record justifying why an automated trigger engine decision was made (FR-15).
 */
public record IncidentWhyExplanation(
        UUID incidentId,
        long eventSeq,
        Instant at,
        String timeNpt,
        String ruleId,
        String ruleName,
        String explanation,
        Map<String, Object> mathematicalInputs,
        AlertTag tag,
        int configVersion
) {}
