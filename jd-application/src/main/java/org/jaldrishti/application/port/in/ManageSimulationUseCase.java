package org.jaldrishti.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Inbound port for starting and controlling a scenario-driven simulation run:
 * loads the scenario, drives the shared sim clock, and replays its schedule
 * through the real ingestion use cases as sim time advances.
 *
 * <p>Implemented by {@code ScenarioRunEngine} in {@code jd-sim}. Adapters (REST)
 * depend only on this port, never on {@code jd-sim} directly, per the hexagonal
 * architecture rule that adapters never depend on one another.
 */
public interface ManageSimulationUseCase {

    record RunSnapshot(
            String runId,
            String scenario,
            String status,
            double speed,
            Instant simTime,
            Instant startTime,
            Instant endTime
    ) {}

    record TimelineMark(Instant at, String label, String tag) {}

    RunSnapshot start(String runId, String scenarioName, Map<String, Object> variantOverrides);

    RunSnapshot play(String runId);

    RunSnapshot pause(String runId);

    RunSnapshot setSpeed(String runId, double speed);

    RunSnapshot snapshot(String runId);

    List<TimelineMark> getAsHappenedTimeline(String runId);
}
