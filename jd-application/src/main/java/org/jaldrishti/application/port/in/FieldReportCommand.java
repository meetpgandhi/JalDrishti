package org.jaldrishti.application.port.in;

import org.jaldrishti.domain.EvidenceType;
import org.jaldrishti.domain.HazardType;
import org.jaldrishti.domain.Language;
import org.jaldrishti.domain.SenderTrust;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Command representing an incoming raw or parsed field report to be processed and evaluated.
 */
public record FieldReportCommand(
        UUID reportId,
        String runId,
        Instant receivedAt,
        String senderHash,
        SenderTrust trust,
        String channel,
        String audioUri,
        String rawText,
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
    }

    public FieldReportCommand(
            String runId,
            Instant receivedAt,
            String senderHash,
            SenderTrust trust,
            String channel,
            String audioUri,
            String rawText
    ) {
        this(
                UUID.randomUUID(),
                runId != null ? runId : "sim",
                receivedAt != null ? receivedAt : Instant.now(),
                senderHash != null ? senderHash : "anonymous",
                trust != null ? trust : SenderTrust.OBSERVER,
                channel != null ? channel : "web",
                audioUri,
                rawText,
                Language.ne,
                rawText != null ? rawText : "",
                rawText != null ? rawText : "",
                null,
                null,
                HazardType.UNCLEAR,
                Collections.emptyList(),
                receivedAt != null ? receivedAt : Instant.now(),
                null,
                0.8,
                false
        );
    }
}
