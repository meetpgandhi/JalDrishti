package org.jaldrishti.adapter.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jaldrishti.application.port.in.FieldReportCommand;
import org.jaldrishti.application.port.in.ForecasterActionUseCase;
import org.jaldrishti.application.port.in.ProcessFieldReportUseCase;
import org.jaldrishti.application.port.out.DispatchedActionRepository;
import org.jaldrishti.application.port.out.IncidentRepository;
import org.jaldrishti.domain.AlertTag;
import org.jaldrishti.domain.DispatchedAction;
import org.jaldrishti.domain.FieldReportRecord;
import org.jaldrishti.domain.Incident;
import org.jaldrishti.domain.IncidentEvent;
import org.jaldrishti.domain.IncidentWhyExplanation;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Controller for Incident Actions (Ack Board), Forecaster Actions, and Field Report Submissions.
 */
@RestController
@RequestMapping("/api/v1")
public class IncidentActionController {

    private static final DateTimeFormatter NPT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneOffset.ofHoursMinutes(5, 45));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DispatchedActionRepository dispatchedActionRepository;
    private final ForecasterActionUseCase forecasterActionUseCase;
    private final ProcessFieldReportUseCase processFieldReportUseCase;
    private final org.jaldrishti.application.port.out.IncidentEventRepository incidentEventRepository;
    private final IncidentRepository incidentRepository;

    public IncidentActionController(
            DispatchedActionRepository dispatchedActionRepository,
            ForecasterActionUseCase forecasterActionUseCase,
            ProcessFieldReportUseCase processFieldReportUseCase,
            org.jaldrishti.application.port.out.IncidentEventRepository incidentEventRepository,
            IncidentRepository incidentRepository
    ) {
        this.dispatchedActionRepository = dispatchedActionRepository;
        this.forecasterActionUseCase = forecasterActionUseCase;
        this.processFieldReportUseCase = processFieldReportUseCase;
        this.incidentEventRepository = incidentEventRepository;
        this.incidentRepository = incidentRepository;
    }

    /** Events for the run's comparison timeline and other read-only consumers of the real event stream. */
    @GetMapping("/incidents/events")
    public ResponseEntity<List<IncidentEvent>> getEventsForRun(@RequestParam String runId) {
        List<IncidentEvent> events = new java.util.ArrayList<>(incidentEventRepository.findByRunId(runId));
        events.sort(Comparator.comparing(IncidentEvent::at));
        return ResponseEntity.ok(events);
    }

    @GetMapping("/incidents/{id}/why")
    public ResponseEntity<IncidentWhyExplanation> getIncidentExplanation(@PathVariable UUID id) {
        List<IncidentEvent> events = incidentEventRepository.findByIncident(id);
        Optional<IncidentEvent> latest = events.stream().max(Comparator.comparing(IncidentEvent::at));
        if (latest.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return buildExplanation(id, latest.get());
    }

    @GetMapping("/incidents/{id}/events/{seq}/why")
    public ResponseEntity<IncidentWhyExplanation> getEventExplanation(
            @PathVariable UUID id,
            @PathVariable Long seq
    ) {
        Optional<IncidentEvent> event = incidentEventRepository.findByIncident(id).stream()
                .filter(e -> seq.equals(e.seq()))
                .findFirst();
        if (event.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return buildExplanation(id, event.get());
    }

    /**
     * Builds the explainability record directly from the actual persisted {@link IncidentEvent}:
     * its rule id, timestamp, and the description/evidence this rule itself recorded when it fired
     * (see {@code TriggerEngine#formatPayload}). Nothing here is templated per rule — whatever the
     * rule put in the event is what is shown.
     */
    private ResponseEntity<IncidentWhyExplanation> buildExplanation(UUID incidentId, IncidentEvent event) {
        String description = event.ruleId() != null ? "Rule " + event.ruleId() + " fired" : "Automated rule evaluation";
        Map<String, Object> evidence = Map.of();
        try {
            Map<?, ?> parsed = OBJECT_MAPPER.readValue(event.payloadJson(), Map.class);
            if (parsed.get("description") != null) {
                description = String.valueOf(parsed.get("description"));
            }
            if (parsed.get("evidence") instanceof Map<?, ?> evidenceMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) evidenceMap;
                evidence = cast;
            }
        } catch (Exception ignored) {
            // Older/other event types may not carry a JSON payload shaped this way; fall back
            // to the bare rule-fired description above rather than fabricating evidence.
        }

        int configVersion = incidentRepository.findById(incidentId).map(Incident::configVersion).orElse(0);

        IncidentWhyExplanation explanation = new IncidentWhyExplanation(
                incidentId,
                event.seq() != null ? event.seq() : 0L,
                event.at(),
                NPT_TIME.format(event.at()) + " NPT",
                event.ruleId(),
                event.type().name(),
                description,
                evidence,
                event.tag() != null ? event.tag() : AlertTag.D,
                configVersion
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
