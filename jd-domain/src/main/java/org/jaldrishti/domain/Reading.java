package org.jaldrishti.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable hydrological reading from a river gauge station.
 *
 * @param runId      Simulation run ID or "live" for production
 * @param stationId  Station identifier (e.g., "RSW")
 * @param observedAt Timestamp when measurement was taken at the sensor
 * @param levelM     Water level in metres
 * @param rainMm     Incremental rainfall in millimetres
 * @param receivedAt Timestamp when measurement was ingested by the platform
 * @param source     Origin of reading ("DHM", "SIM", "LIVE_INJECTION")
 * @param tag        Provenance tag ([P] published, [D] derived, [A] assumption)
 */
public record Reading(
        String runId,
        String stationId,
        Instant observedAt,
        double levelM,
        double rainMm,
        Instant receivedAt,
        String source,
        AlertTag tag
) {
    public Reading {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(stationId, "stationId must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(tag, "tag must not be null");
    }
}
