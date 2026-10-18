package org.jaldrishti.adapter.rest;

import org.jaldrishti.application.port.in.RunSilenceSweepUseCase;
import org.jaldrishti.application.port.in.SweepResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import org.jaldrishti.application.port.in.EscalateActionsUseCase;
import org.jaldrishti.domain.DispatchedAction;
import java.util.List;

/**
 * Internal scheduled sweep and escalation endpoints.
 *
 * <p>In production (live profile), invoked once per minute by Cloud Scheduler (OIDC-authenticated).
 * In simulation mode, invoked once per simulated minute by SimScheduler.
 */
@RestController
@RequestMapping("/internal")
public class InternalSweepController {

    private final RunSilenceSweepUseCase silenceSweepUseCase;
    private final EscalateActionsUseCase escalateActionsUseCase;

    public InternalSweepController(
            RunSilenceSweepUseCase silenceSweepUseCase,
            EscalateActionsUseCase escalateActionsUseCase
    ) {
        this.silenceSweepUseCase = silenceSweepUseCase;
        this.escalateActionsUseCase = escalateActionsUseCase;
    }

    @PostMapping("/sweep")
    public ResponseEntity<SweepResult> runSilenceSweep(
            @RequestParam(name = "runId", defaultValue = "live") String runId,
            @RequestParam(name = "corridorId", defaultValue = "bhote-trishuli-narayani") String corridorId,
            @RequestParam(name = "now", required = false) String nowIso
    ) {
        Instant now = nowIso != null ? Instant.parse(nowIso) : null;
        SweepResult result = silenceSweepUseCase.sweep(runId, corridorId, now);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/escalate")
    public ResponseEntity<List<DispatchedAction>> runEscalationSweep(
            @RequestParam(name = "runId", defaultValue = "live") String runId,
            @RequestParam(name = "now", required = false) String nowIso
    ) {
        Instant now = nowIso != null ? Instant.parse(nowIso) : null;
        List<DispatchedAction> escalated = escalateActionsUseCase != null
                ? escalateActionsUseCase.checkAndEscalateActions(runId, now)
                : List.of();
        return ResponseEntity.ok(escalated);
    }
}
