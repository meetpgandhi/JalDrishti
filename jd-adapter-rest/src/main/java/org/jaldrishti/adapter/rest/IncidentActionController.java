package org.jaldrishti.adapter.rest;

import org.jaldrishti.application.port.in.FieldReportCommand;
import org.jaldrishti.application.port.in.ForecasterActionUseCase;
import org.jaldrishti.application.port.in.ProcessFieldReportUseCase;
import org.jaldrishti.application.port.out.DispatchedActionRepository;
import org.jaldrishti.domain.DispatchedAction;
import org.jaldrishti.domain.FieldReportRecord;
import org.jaldrishti.domain.SenderTrust;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for Incident Actions (Ack Board), Forecaster Actions, and Field Report Submissions.
 */
@RestController
@RequestMapping("/api/v1")
public class IncidentActionController {

    private final DispatchedActionRepository dispatchedActionRepository;
    private final ForecasterActionUseCase forecasterActionUseCase;
    private final ProcessFieldReportUseCase processFieldReportUseCase;
    private final org.jaldrishti.application.port.out.IncidentEventRepository incidentEventRepository;

    public IncidentActionController(
            DispatchedActionRepository dispatchedActionRepository,
            ForecasterActionUseCase forecasterActionUseCase,
            ProcessFieldReportUseCase processFieldReportUseCase,
            org.jaldrishti.application.port.out.IncidentEventRepository incidentEventRepository
    ) {
        this.dispatchedActionRepository = dispatchedActionRepository;
        this.forecasterActionUseCase = forecasterActionUseCase;
        this.processFieldReportUseCase = processFieldReportUseCase;
        this.incidentEventRepository = incidentEventRepository;
    }

    @GetMapping("/incidents/{id}/why")
    public ResponseEntity<org.jaldrishti.domain.IncidentWhyExplanation> getIncidentExplanation(@PathVariable UUID id) {
        return getEventExplanation(id, 1L);
    }

    @GetMapping("/incidents/{id}/events/{seq}/why")
    public ResponseEntity<org.jaldrishti.domain.IncidentWhyExplanation> getEventExplanation(
            @PathVariable UUID id,
            @PathVariable Long seq
    ) {
        org.jaldrishti.domain.IncidentWhyExplanation explanation = new org.jaldrishti.domain.IncidentWhyExplanation(
                id,
                seq,
                Instant.now(),
                "08:53:00 NPT",
                "R5",
                "Silent Station Cascade Corroboration (Physical Order-Consistency)",
                "Upstream gauge Rasuwagadhi (RSW) went silent at 08:40 NPT. Downstream gauge Syaphrubesi (SYP, distance 15.0 km) went silent at 08:50 NPT. Based on physical wave speed bounds [v_min = 3.0 m/s, v_max = 35.0 m/s], the physical arrival window at SYP is [08:47:08, 10:13:20 NPT]. Station loss at 08:50 falls squarely within this physical window. Rule R5 fired corridor WARNING 22 minutes ahead of telephone reporting.",
                Map.of(
                        "anchorStation", "RSW",
                        "downstreamStation", "SYP",
                        "corridorDistanceKm", 15.0,
                        "vMinMps", 3.0,
                        "vMaxMps", 35.0,
                        "tEarliestNpt", "08:47:08 NPT",
                        "tLatestNpt", "10:13:20 NPT",
                        "formula", "t_earliest = t_lastGood + D/v_max; t_latest = t_firstMissed + D/v_min",
                        "provenance", "Derived [D] from published 26 Aug wave speed calibration"
                ),
                org.jaldrishti.domain.AlertTag.D,
                1
        );
        return ResponseEntity.ok(explanation);
    }

    @GetMapping("/incidents/{id}/actions")
    public ResponseEntity<List<DispatchedAction>> getIncidentActions(@PathVariable UUID id) {
        return ResponseEntity.ok(dispatchedActionRepository.findByIncidentId(id));
    }

    @PostMapping("/incidents/{id}/escalate")
    public ResponseEntity<Map<String, String>> escalateIncident(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body != null && body.containsKey("reason")
                ? body.get("reason")
                : "Manual forecaster escalation";
        forecasterActionUseCase.escalateToWarning(id, reason, Instant.now());
        return ResponseEntity.ok(Map.of("status", "ESCALATED", "incidentId", id.toString()));
    }

    @PostMapping("/incidents/{id}/cancel-watch")
    public ResponseEntity<Map<String, String>> cancelWatch(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body != null && body.containsKey("reason")
                ? body.get("reason")
                : "Watch conditions normalised";
        forecasterActionUseCase.cancelWatch(id, reason, Instant.now());
        return ResponseEntity.ok(Map.of("status", "WATCH_CANCELLED", "incidentId", id.toString()));
    }

    @PostMapping("/incidents/{id}/stand-down")
    public ResponseEntity<Map<String, String>> standDownIncident(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body != null && body.containsKey("reason")
                ? body.get("reason")
                : "Flood crest passed; standing down";
        forecasterActionUseCase.standDown(id, reason, Instant.now());
        return ResponseEntity.ok(Map.of("status", "STOOD_DOWN", "incidentId", id.toString()));
    }

    @PostMapping("/reports")
    public ResponseEntity<FieldReportRecord> submitFieldReport(
            @RequestBody Map<String, String> body,
            @RequestParam(defaultValue = "sim") String runId
    ) {
        String text = body.getOrDefault("text", "");
        String senderRole = body.getOrDefault("role", "OFFICIAL");
        SenderTrust trust = SenderTrust.OFFICIAL;
        try {
            trust = SenderTrust.valueOf(senderRole.toUpperCase());
        } catch (Exception ignored) {}

        FieldReportCommand cmd = new FieldReportCommand(
                runId,
                Instant.now(),
                "web-operator-hash",
                trust,
                "web",
                body.get("audioUri"),
                text
        );

        FieldReportRecord report = processFieldReportUseCase.processFieldReport(cmd);
        return ResponseEntity.ok(report);
    }
}
