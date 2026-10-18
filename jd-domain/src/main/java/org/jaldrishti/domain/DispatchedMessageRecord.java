package org.jaldrishti.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain record representing an outbound message attempt for a dispatched action.
 */
public record DispatchedMessageRecord(
        UUID id,
        UUID dispatchedActionId,
        String channel,
        Language language,
        String text,
        GuardResult guardResult,
        boolean fallbackUsed,
        String modelId,
        Instant sentAt
) {
    public DispatchedMessageRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(dispatchedActionId, "dispatchedActionId must not be null");
        Objects.requireNonNull(channel, "channel must not be null");
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(guardResult, "guardResult must not be null");
        Objects.requireNonNull(sentAt, "sentAt must not be null");
    }
}
