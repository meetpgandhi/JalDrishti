package org.jaldrishti.application.port.out;

import org.jaldrishti.domain.Incident;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and querying corridor incidents.
 */
public interface IncidentRepository {

    Optional<Incident> findOpenIncident(String runId, String corridorId);

    Optional<Incident> findById(UUID incidentId);

    void save(Incident incident);
}
