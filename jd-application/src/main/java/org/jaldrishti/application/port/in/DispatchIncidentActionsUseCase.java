package org.jaldrishti.application.port.in;

import org.jaldrishti.domain.DispatchedAction;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port to assemble, localise, and dispatch duty holder actions for an active incident.
 */
public interface DispatchIncidentActionsUseCase {

    /**
     * Identifies eligible duty holders, generates action assignments based on approved duty matrix,
     * runs Gemini localisation and numeric safety guards, and dispatches to channels.
     *
     * @param runId      Simulation run ID or 'live'
     * @param incidentId Active incident ID
     * @return List of created dispatched action records
     */
    List<DispatchedAction> dispatchActionsForIncident(String runId, UUID incidentId);
}
