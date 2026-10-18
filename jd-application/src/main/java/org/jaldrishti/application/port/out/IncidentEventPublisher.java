package org.jaldrishti.application.port.out;

import org.jaldrishti.domain.IncidentEvent;

/**
 * Outbound port for broadcasting incident events to Pub/Sub and SSE subscribers.
 */
public interface IncidentEventPublisher {

    void publish(String runId, IncidentEvent event);
}
