package org.jaldrishti.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An action assigned to a duty holder as a result of an incident alert.
 */
public record DispatchedAction(
        UUID id,
        String runId,
        UUID incidentId,
        UUID dutyHolderId,
        String actionCode,
        int matrixVersion,
        Instant deadlineAt,
        ActionState state,
        Instant sentAt,
        Instant ackedAt,
        String ackDataJson,
        int escalationStep
) {
    public DispatchedAction {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(incidentId, "incidentId must not be null");
        Objects.requireNonNull(dutyHolderId, "dutyHolderId must not be null");
        Objects.requireNonNull(actionCode, "actionCode must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(sentAt, "sentAt must not be null");
    }

    public DispatchedAction withState(ActionState newState, Instant when, String ackData) {
        return new DispatchedAction(
                id,
                runId,
                incidentId,
                dutyHolderId,
                actionCode,
                matrixVersion,
                deadlineAt,
                newState,
                sentAt,
                when,
                ackData,
                escalationStep
        );
    }

    public DispatchedAction withEscalation(ActionState newState, int newStep) {
        return new DispatchedAction(
                id,
                runId,
                incidentId,
                dutyHolderId,
                actionCode,
                matrixVersion,
                deadlineAt,
                newState,
                sentAt,
                ackedAt,
                ackDataJson,
                newStep
        );
    }
}
