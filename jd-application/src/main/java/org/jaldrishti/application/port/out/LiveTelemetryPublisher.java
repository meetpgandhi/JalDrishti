package org.jaldrishti.application.port.out;

import org.jaldrishti.domain.ArrivalBand;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for pushing live per-reading telemetry, station-state transitions,
 * sim-clock ticks, and computed arrival bands to connected consoles (over SSE).
 *
 * <p>Distinct from {@link IncidentEventPublisher}, which carries the append-only
 * incident_event stream. This port carries ephemeral, high-frequency UI state that
 * is never persisted as a domain event.
 */
public interface LiveTelemetryPublisher {

    record ReadingUpdate(String stationId, Instant observedAt, double levelM, double rainMm, String tag) {}

    record StationStateUpdate(
            String stationId,
            String state,
            boolean silent,
            boolean rateAlert,
            Double lastLevelM,
            Instant lastObservedAt
    ) {}

    record ClockUpdate(String runId, String scenario, Instant simTime, double speed, String status) {}

    void publishReading(String runId, ReadingUpdate update);

    void publishStationState(String runId, StationStateUpdate update);

    void publishClock(String runId, ClockUpdate update);

    void publishArrivalBands(String runId, List<ArrivalBand> bands);
}
