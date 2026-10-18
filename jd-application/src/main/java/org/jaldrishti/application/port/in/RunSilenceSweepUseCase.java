package org.jaldrishti.application.port.in;

import java.time.Instant;

/**
 * Inbound port called periodically by Cloud Scheduler or SimScheduler to evaluate
 * station silence, carrier outages, and cascade order consistency.
 */
public interface RunSilenceSweepUseCase {

    SweepResult sweep(String runId, String corridorId, Instant now);
}
