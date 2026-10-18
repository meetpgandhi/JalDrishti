package org.jaldrishti.adapter.channels;

import org.jaldrishti.application.port.in.AckResult;
import org.jaldrishti.application.port.in.AcknowledgeActionUseCase;
import org.jaldrishti.application.port.in.FieldReportCommand;
import org.jaldrishti.application.port.in.ProcessFieldReportUseCase;
import org.jaldrishti.application.port.out.DutyHolderRepository;
import org.jaldrishti.domain.DutyHolder;
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
 * Controller serving the web phone simulator for judges and operators.
 * Allows role selection, inbox polling, and one-click duty action acknowledgements.
 */
@RestController
@RequestMapping("/api/v1/phone")
public class PhoneSimulatorController {

    private final DutyHolderRepository dutyHolderRepository;
    private final ChannelDispatcher channelDispatcher;
    private final AcknowledgeActionUseCase acknowledgeUseCase;
    private final ProcessFieldReportUseCase processFieldReportUseCase;

    public PhoneSimulatorController(
            DutyHolderRepository dutyHolderRepository,
            ChannelDispatcher channelDispatcher,
            AcknowledgeActionUseCase acknowledgeUseCase,
            ProcessFieldReportUseCase processFieldReportUseCase
    ) {
        this.dutyHolderRepository = dutyHolderRepository;
        this.channelDispatcher = channelDispatcher;
        this.acknowledgeUseCase = acknowledgeUseCase;
        this.processFieldReportUseCase = processFieldReportUseCase;
    }

    @GetMapping("/duty-holders")
    public ResponseEntity<List<DutyHolder>> listDutyHolders() {
        return ResponseEntity.ok(dutyHolderRepository.findAll());
    }

    @GetMapping("/inbox/{dutyHolderId}")
    public ResponseEntity<List<ChannelDispatcher.PhoneMessage>> getInbox(@PathVariable UUID dutyHolderId) {
        return ResponseEntity.ok(channelDispatcher.getInbox(dutyHolderId));
    }

    @PostMapping("/ack/{actionId}")
    public ResponseEntity<AckResult> ackAction(
            @PathVariable UUID actionId,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        String ackData = payload != null ? payload.toString() : "{\"status\":\"CONFIRMED\"}";
        AckResult result = acknowledgeUseCase.acknowledge(actionId, ackData, Instant.now());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/decline/{actionId}")
    public ResponseEntity<AckResult> declineAction(
            @PathVariable UUID actionId,
            @RequestBody(required = false) Map<String, String> payload
    ) {
        String reason = payload != null && payload.containsKey("reason")
                ? payload.get("reason")
                : "Cannot fulfill duty directive";
        AckResult result = acknowledgeUseCase.decline(actionId, reason, Instant.now());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/report")
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
                "sim-phone-hash",
                trust,
                "phone_sim",
                null,
                text
        );

        FieldReportRecord report = processFieldReportUseCase.processFieldReport(cmd);
        return ResponseEntity.ok(report);
    }
}
