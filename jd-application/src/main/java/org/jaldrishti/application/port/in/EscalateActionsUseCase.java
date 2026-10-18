package org.jaldrishti.application.port.in;

import org.jaldrishti.domain.DispatchedAction;

import java.time.Instant;
import java.util.List;

/**
 * Inbound port for scheduled sweeps to evaluate escalation ladder steps
 * for unacknowledged actions (+5m voice, +10m deputy, +15m CDO flag).
 */
public interface EscalateActionsUseCase {

    /**
     * Checks unacknowledged actions against deadline and escalation intervals.
     *
     * @param runId Simulation run ID or 'live'
     * @param now   Current clock timestamp
     * @return List of escalated action records
     */
    List<DispatchedAction> checkAndEscalateActions(String runId, Instant now);
}
