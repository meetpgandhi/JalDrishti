package org.jaldrishti.sim;

import org.jaldrishti.application.port.in.EscalateActionsUseCase;
import org.jaldrishti.application.port.in.IngestReadingUseCase;
import org.jaldrishti.application.port.in.ReadingCommand;
import org.jaldrishti.application.port.in.RunSilenceSweepUseCase;
import org.jaldrishti.application.port.in.SweepResult;
import org.jaldrishti.domain.AlertTag;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * SimScheduler — Drives the simulated timeline.
 *
 * <p>In simulation mode, replaces Cloud Scheduler. Every simulated minute, SimScheduler:
 * <ul>
 *   <li>Evaluates whether any sensor readings should be emitted for current sim time.</li>
 *   <li>Executes silence sweep via {@link RunSilenceSweepUseCase}.</li>
 *   <li>Executes duty action escalation sweeps via {@link EscalateActionsUseCase}.</li>
 * </ul>
 */
public class SimScheduler {

    private final SimClock clock;
    private final RunSilenceSweepUseCase silenceSweepUseCase;
    private final IngestReadingUseCase readingUseCase;
    private final EscalateActionsUseCase escalateUseCase;
    private Instant lastSweepTick;

    public SimScheduler(
            SimClock clock,
            RunSilenceSweepUseCase silenceSweepUseCase,
            IngestReadingUseCase readingUseCase
    ) {
        this(clock, silenceSweepUseCase, readingUseCase, null);
    }

    public SimScheduler(
            SimClock clock,
            RunSilenceSweepUseCase silenceSweepUseCase,
            IngestReadingUseCase readingUseCase,
            EscalateActionsUseCase escalateUseCase
    ) {
        this.clock = Objects.requireNonNull(clock, "SimClock must not be null");
        this.silenceSweepUseCase = Objects.requireNonNull(silenceSweepUseCase, "silenceSweepUseCase must not be null");
        this.readingUseCase = readingUseCase;
        this.escalateUseCase = escalateUseCase;
        this.lastSweepTick = null;
    }

    /**
     * Executes one tick of the simulation scheduler.
     *
     * @param runId      Active simulation run ID
     * @param corridorId Corridor ID being simulated
     * @return Result of silence sweep if executed, or null
     */
    public SweepResult tick(String runId, String corridorId) {
        Instant currentSimTime = clock.instant();

        // Fire silence sweep and escalation once per simulated minute
        if (lastSweepTick == null || Duration.between(lastSweepTick, currentSimTime).toSeconds() >= 60) {
            lastSweepTick = currentSimTime;
            if (escalateUseCase != null) {
                escalateUseCase.checkAndEscalateActions(runId, currentSimTime);
            }
            return silenceSweepUseCase.sweep(runId, corridorId, currentSimTime);
        }

        return null;
    }

    /**
     * Helper to publish a simulated reading into the ingestion pipeline.
     */
    public void publishReading(String runId, String stationId, double levelM, double rainMm, AlertTag tag) {
        if (readingUseCase != null) {
            Instant now = clock.instant();
            readingUseCase.ingest(new ReadingCommand(
                    runId,
                    stationId,
                    now,
                    levelM,
                    rainMm,
                    now,
                    "SIM",
                    tag != null ? tag : AlertTag.A
            ));
        }
    }
}
