package org.jaldrishti.application.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound port for manual forecaster actions and declarations.
 */
public interface ForecasterActionUseCase {

    void escalateToWarning(UUID incidentId, String reason, Instant now);

    void cancelWatch(UUID incidentId, String reason, Instant now);

    void standDown(UUID incidentId, String reason, Instant now);

    void setHeightenedWatch(String runId, String corridorId, boolean active, String reason, Instant now);
}
