package org.jaldrishti.application.port.out;

import org.jaldrishti.domain.StationObservationState;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying station telemetry state projections.
 */
public interface StationStateRepository {

    Optional<StationObservationState> find(String runId, String stationId);

    List<StationObservationState> findByRunId(String runId);

    void save(StationObservationState state);
}
