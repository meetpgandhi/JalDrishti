package org.jaldrishti.application.port.in;

import org.jaldrishti.domain.EvidenceType;
import org.jaldrishti.domain.HazardType;
import org.jaldrishti.domain.Language;
import org.jaldrishti.domain.SenderTrust;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Command representing an incoming parsed field report to be evaluated by the trigger engine.
 */
public record FieldReportCommand(
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
    public FieldReportCommand {
        Objects.requireNonNull(reportId, "reportId must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(senderHash, "senderHash must not be null");
        Objects.requireNonNull(trust, "trust must not be null");
        Objects.requireNonNull(hazardType, "hazardType must not be null");
    }
}
