package org.jaldrishti.application.port.out;

import org.jaldrishti.domain.DispatchedAction;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for saving and querying dispatched actions.
 */
public interface DispatchedActionRepository {

    void save(DispatchedAction action);

    Optional<DispatchedAction> findById(UUID id);

    List<DispatchedAction> findByIncidentId(UUID incidentId);

    List<DispatchedAction> findByRunId(String runId);

    List<DispatchedAction> findPendingEscalation(String runId, Instant now);

    boolean existsByIncidentAndHolderAndCode(UUID incidentId, UUID dutyHolderId, String actionCode);
}
