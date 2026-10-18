package org.jaldrishti.adapter.pubsub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jaldrishti.domain.AlertTag;

import java.time.Instant;

/**
 * Payload deserialized from the gauge-readings Pub/Sub topic.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GaugeReadingPushPayload(
        String runId,
        String stationId,
        String observedAt,
        double levelM,
        double rainMm,
        String source,
        String tag
) {
    public AlertTag parseTag() {
        if (tag == null) return AlertTag.A;
        try {
            return AlertTag.valueOf(tag);
        } catch (IllegalArgumentException e) {
            return AlertTag.A;
        }
    }

    public Instant parseObservedAt() {
        return observedAt != null ? Instant.parse(observedAt) : Instant.now();
    }
}
