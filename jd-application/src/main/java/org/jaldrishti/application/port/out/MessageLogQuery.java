package org.jaldrishti.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbound read port feeding the operator console's message log and CAP log.
 */
public interface MessageLogQuery {

    List<MessageLogView> findMessagesByRunId(String runId);

    List<CapMessageView> findCapMessagesByRunId(String runId);

    /** One dispatched message joined with its action and recipient. */
    record MessageLogView(
            UUID messageId,
            UUID actionId,
            UUID incidentId,
            Instant sentAt,
            String kind,
            String channel,
            String language,
            String text,
            UUID recipientId,
            String recipientName,
            String recipientActorType,
            String recipientSiteId,
            UUID principalId,
            String principalName,
            String actionCode,
            Instant deadlineAt,
            String actionState,
            int escalationStep,
            Instant ackedAt
    ) {}

    record CapMessageView(
            String identifier,
            UUID incidentId,
            String msgType,
            String status,
            Instant sentAt,
            String gatewayResponse,
            String xml
    ) {}
}
