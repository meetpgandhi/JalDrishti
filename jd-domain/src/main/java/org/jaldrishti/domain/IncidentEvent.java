package org.jaldrishti.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable incident event in the append-only event stream.
 *
 * @param seq         Monotonic event sequence number (assigned by persistence or event generator)
 * @param incidentId  Associated incident UUID
 * @param at          Timestamp when event occurred
 * @param type        Type of incident change
 * @param ruleId      Trigger rule that caused this event (e.g., "R1", "R5", "R10")
 * @param payloadJson Serialized structured payload / evidence details
 * @param tag         Provenance tag ([P], [D], or [A])
 */
public record IncidentEvent(
        Long seq,
        UUID incidentId,
        Instant at,
        EventType type,
        String ruleId,
        String payloadJson,
        AlertTag tag
) {
    public IncidentEvent {
        Objects.requireNonNull(incidentId, "incidentId must not be null");
        Objects.requireNonNull(at, "at must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(tag, "tag must not be null");
    }
}
