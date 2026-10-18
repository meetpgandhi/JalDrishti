package org.jaldrishti.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain record representation of an inbound field report.
 */
public record FieldReportRecord(
        UUID id,
        String runId,
        Instant receivedAt,
        String senderHash,
        SenderTrust trust,
        String channel,
        String audioUri,
        String rawText,
        String parsedJson,
        ReportStatus status,
        String modelId
) {
    public FieldReportRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(senderHash, "senderHash must not be null");
        Objects.requireNonNull(trust, "trust must not be null");
        Objects.requireNonNull(channel, "channel must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public FieldReportRecord withParsed(String parsedJson, ReportStatus status, String modelId) {
        return new FieldReportRecord(
                id,
                runId,
                receivedAt,
                senderHash,
                trust,
                channel,
                audioUri,
                rawText,
                parsedJson,
                status,
                modelId
        );
    }
}
