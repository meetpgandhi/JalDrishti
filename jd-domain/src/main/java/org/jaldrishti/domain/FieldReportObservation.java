package org.jaldrishti.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Structured observation parsed from an inbound voice or text field report.
 *
 * @param reportId          Report UUID
 * @param runId             Simulation run ID or "live"
 * @param receivedAt        Timestamp report was received
 * @param senderHash        Salted cryptographic hash of sender's phone/chat identity
 * @param trust             Verified trust classification of sender
 * @param channel           Inbound channel ("telegram", "sms", "phone_sim")
 * @param language          Language of report
 * @param transcript        Original transcript (Nepali/Hindi/English)
 * @param translationEn     Faithful English translation
 * @param locationId        Resolved gazetteer site identifier (e.g., "SYB"), or null
 * @param locationText      Raw location name as extracted from text if unresolved
 * @param hazardType        Classified hazard type
 * @param evidence          List of sensory evidence tokens reported
 * @param observedAt        Timestamp when hazard was observed in the field
 * @param peopleAtRiskCount Estimated count of individuals directly at risk, or null
 * @param confidence        Extraction confidence score [0.0, 1.0]
 * @param needsCallback     Whether report flagged ambiguity requiring callback
 */
public record FieldReportObservation(
        UUID reportId,
        String runId,
        Instant receivedAt,
        String senderHash,
        SenderTrust trust,
        String channel,
        Language language,
        String transcript,
        String translationEn,
        String locationId,
        String locationText,
        HazardType hazardType,
        List<EvidenceType> evidence,
        Instant observedAt,
        Integer peopleAtRiskCount,
        double confidence,
        boolean needsCallback
) {
    public FieldReportObservation {
        Objects.requireNonNull(reportId, "reportId must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(senderHash, "senderHash must not be null");
        Objects.requireNonNull(trust, "trust must not be null");
        Objects.requireNonNull(channel, "channel must not be null");
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(hazardType, "hazardType must not be null");
        evidence = evidence == null ? Collections.emptyList() : List.copyOf(evidence);
    }

    public boolean isHazardous() {
        return hazardType == HazardType.DEBRIS_FLOOD
                || hazardType == HazardType.RAPID_RISE
                || hazardType == HazardType.LANDSLIDE
                || hazardType == HazardType.BRIDGE_DAMAGE
                || hazardType == HazardType.PEOPLE_TRAPPED;
    }
}
