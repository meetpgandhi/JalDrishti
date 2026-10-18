package org.jaldrishti.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Earthquake or seismic telemetry observation in the catchment.
 *
 * @param eventId     Seismic event identifier
 * @param runId       Simulation run ID or "live"
 * @param originTime  Seismic origin time
 * @param publishedAt Timestamp event was published by seismic network
 * @param magnitude   Richter/moment magnitude
 * @param lat         Epicentre latitude
 * @param lon         Epicentre longitude
 * @param source      Source provider (e.g. "USGS", "NSC")
 */
public record SeismicObservation(
        String eventId,
        String runId,
        Instant originTime,
        Instant publishedAt,
        double magnitude,
        double lat,
        double lon,
        String source
) {
    public SeismicObservation {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(originTime, "originTime must not be null");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
    }
}
