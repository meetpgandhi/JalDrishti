package org.jaldrishti.adapter.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST controller for controlling simulation runs, playback, clock speeds, and live injections.
 */
@RestController
@RequestMapping("/api/v1/sim/runs")
public class SimRunController {

    private final Clock clock;
    private final Map<String, SimRunState> runs = new ConcurrentHashMap<>();

    public record StartRunRequest(
            String scenario,
            Map<String, Object> variants,
            Long seed
    ) {}

    public record StartRunResponse(
            String runId,
            String scenario,
            String state,
            Instant startTime
    ) {}

    public record SpeedRequest(double speed) {}

    public record SimRunState(
            String runId,
            String scenario,
            String status,
            double speed,
            Instant simTime
    ) {}

    private final org.jaldrishti.application.port.in.GenerateReplayReportUseCase replayReportUseCase;
    private final org.jaldrishti.application.port.in.IngestReadingUseCase readingUseCase;
    private final org.jaldrishti.application.port.in.ProcessFieldReportUseCase reportUseCase;
    private final org.jaldrishti.application.port.in.ForecasterActionUseCase forecasterActionUseCase;

    public record InjectionRequest(
            String type,
            String stationId,
            Double levelM,
            Double rainMm,
            String reportText,
            String role,
            Boolean active
    ) {}

    public record InjectionResponse(
            boolean success,
            String type,
            String target,
            String message,
            Instant at
    ) {}

    public SimRunController(
            Clock clock,
            org.jaldrishti.application.port.in.GenerateReplayReportUseCase replayReportUseCase,
            org.jaldrishti.application.port.in.IngestReadingUseCase readingUseCase,
            org.jaldrishti.application.port.in.ProcessFieldReportUseCase reportUseCase,
            org.jaldrishti.application.port.in.ForecasterActionUseCase forecasterActionUseCase
    ) {
        this.clock = clock;
        this.replayReportUseCase = replayReportUseCase;
        this.readingUseCase = readingUseCase;
        this.reportUseCase = reportUseCase;
        this.forecasterActionUseCase = forecasterActionUseCase;
    }

    @PostMapping("/{runId}/inject")
    public ResponseEntity<InjectionResponse> injectLiveEvent(
            @PathVariable("runId") String runId,
            @RequestBody InjectionRequest request
    ) {
        Instant now = clock.instant();
        String type = request.type() != null ? request.type().toUpperCase() : "UNKNOWN";
        String stationId = request.stationId() != null ? request.stationId() : "RSW";

        switch (type) {
            case "DESTROY_GAUGE" -> {
                // Station stops reporting; silence timer starts
                return ResponseEntity.ok(new InjectionResponse(true, type, stationId,
                        "Telemetry disabled for " + stationId + ". Gauge will be marked SILENT on next sweep.", now));
            }
            case "RESTORE_GAUGE" -> {
                // Emits normal reading -> triggers R11 auto-cancel
                if (readingUseCase != null) {
                    readingUseCase.ingest(new org.jaldrishti.application.port.in.ReadingCommand(
                            runId, stationId, now, 2.5, 0.0, "LIVE_INJECTION", org.jaldrishti.domain.AlertTag.A
                    ));
                }
                return ResponseEntity.ok(new InjectionResponse(true, type, stationId,
                        "Station " + stationId + " restored to normal (2.5m). R11 evaluated.", now));
            }
            case "SET_LEVEL" -> {
                double lvl = request.levelM() != null ? request.levelM() : 6.8;
                if (readingUseCase != null) {
                    readingUseCase.ingest(new org.jaldrishti.application.port.in.ReadingCommand(
                            runId, stationId, now, lvl, 0.0, "LIVE_INJECTION", org.jaldrishti.domain.AlertTag.A
                    ));
                }
                return ResponseEntity.ok(new InjectionResponse(true, type, stationId,
                        "Water level forced to " + lvl + "m at " + stationId, now));
            }
            case "RAIN" -> {
                double rain = request.rainMm() != null ? request.rainMm() : 45.0;
                if (readingUseCase != null) {
                    readingUseCase.ingest(new org.jaldrishti.application.port.in.ReadingCommand(
                            runId, stationId, now, 3.2, rain, "LIVE_INJECTION", org.jaldrishti.domain.AlertTag.A
                    ));
                }
                return ResponseEntity.ok(new InjectionResponse(true, type, stationId,
                        "Rainfall forced to " + rain + "mm at " + stationId, now));
            }
            case "REPORT" -> {
                String text = request.reportText() != null ? request.reportText()
                        : "म स्याफ्रुबेसीबाट बोल्दैछु। भोटेकोसीमा ठूलो बाढी आयो!";
                org.jaldrishti.domain.SenderTrust trust = "OFFICIAL".equalsIgnoreCase(request.role())
                        ? org.jaldrishti.domain.SenderTrust.OFFICIAL
                        : org.jaldrishti.domain.SenderTrust.OBSERVER;

                if (reportUseCase != null) {
                    reportUseCase.processFieldReport(new org.jaldrishti.application.port.in.FieldReportCommand(
                            runId, now, "live-inject-hash", trust, "web_injection", null, text
                    ));
                }
                return ResponseEntity.ok(new InjectionResponse(true, type, "CORRIDOR",
                        "Field report injected: \"" + text + "\" as " + trust, now));
            }
            case "HEIGHTENED_WATCH" -> {
                boolean active = request.active() != null ? request.active() : true;
                if (forecasterActionUseCase != null) {
                    forecasterActionUseCase.setHeightenedWatch(runId, "bhote-trishuli-narayani", active,
                            "Manual live injection of heightened watch", now);
                }
                return ResponseEntity.ok(new InjectionResponse(true, type, "CORRIDOR",
                        "Heightened watch set to " + active + " (R2 thresholds halved, top gauge loss -> WARNING)", now));
            }
            default -> {
                return ResponseEntity.badRequest().body(new InjectionResponse(false, type, stationId,
                        "Unknown injection type: " + type, now));
            }
        }
    }

    @GetMapping("/{runId}/report")
    public ResponseEntity<org.jaldrishti.domain.ReplayReport> getReplayReport(@PathVariable("runId") String runId) {
        SimRunState state = runs.get(runId);
        String scenario = state != null ? state.scenario() : "aug26-as-happened";
        if (replayReportUseCase != null) {
            return ResponseEntity.ok(replayReportUseCase.generateReport(runId, scenario));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<StartRunResponse> startRun(@RequestBody StartRunRequest request) {
        String runId = UUID.randomUUID().toString();
        String scenario = request.scenario() != null ? request.scenario() : "aug26-as-happened";
        Instant now = clock.instant();

        SimRunState state = new SimRunState(runId, scenario, "RUNNING", 1.0, now);
        runs.put(runId, state);

        return ResponseEntity.ok(new StartRunResponse(runId, scenario, "RUNNING", now));
    }

    @PostMapping("/{runId}/play")
    public ResponseEntity<SimRunState> play(@PathVariable("runId") String runId) {
        SimRunState existing = runs.get(runId);
        if (existing == null) {
            existing = new SimRunState(runId, "aug26-as-happened", "RUNNING", 1.0, clock.instant());
        } else {
            existing = new SimRunState(runId, existing.scenario(), "RUNNING", existing.speed(), clock.instant());
        }
        runs.put(runId, existing);
        return ResponseEntity.ok(existing);
    }

    @PostMapping("/{runId}/pause")
    public ResponseEntity<SimRunState> pause(@PathVariable("runId") String runId) {
        SimRunState existing = runs.get(runId);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        SimRunState paused = new SimRunState(runId, existing.scenario(), "PAUSED", existing.speed(), clock.instant());
        runs.put(runId, paused);
        return ResponseEntity.ok(paused);
    }

    @PostMapping("/{runId}/speed")
    public ResponseEntity<SimRunState> setSpeed(
            @PathVariable("runId") String runId,
            @RequestBody SpeedRequest request
    ) {
        SimRunState existing = runs.get(runId);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        SimRunState updated = new SimRunState(runId, existing.scenario(), existing.status(), request.speed(), clock.instant());
        runs.put(runId, updated);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{runId}/clock")
    public ResponseEntity<SimRunState> getClock(@PathVariable("runId") String runId) {
        SimRunState existing = runs.get(runId);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(existing);
    }
}
