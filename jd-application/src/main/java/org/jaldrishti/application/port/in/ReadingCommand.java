package org.jaldrishti.application.port.in;

import org.jaldrishti.domain.AlertTag;

import java.time.Instant;
import java.util.Objects;

/**
 * Inbound command containing a hydrological sensor reading to ingest.
 */
public record ReadingCommand(
        String runId,
        String stationId,
        Instant observedAt,
        double levelM,
        double rainMm,
        Instant receivedAt,
        String source,
        AlertTag tag
) {
    public ReadingCommand {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(stationId, "stationId must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(tag, "tag must not be null");
    }
}
