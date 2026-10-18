package org.jaldrishti.application.port.out;

import org.jaldrishti.domain.IncidentEvent;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port for the append-only incident_event stream.
 *
 * <p><strong>CRITICAL ARCHITECTURE RULE:</strong> This repository is append-only.
 * Events are never updated or deleted.
 */
public interface IncidentEventRepository {

    void append(IncidentEvent event);

    List<IncidentEvent> findByIncident(UUID incidentId);

    List<IncidentEvent> findByRunId(String runId);
}
