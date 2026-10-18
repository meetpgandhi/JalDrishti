package org.jaldrishti.adapter.channels;

import org.jaldrishti.application.port.in.AckResult;
import org.jaldrishti.application.port.in.AcknowledgeActionUseCase;
import org.jaldrishti.application.port.in.FieldReportCommand;
import org.jaldrishti.application.port.in.ProcessFieldReportUseCase;
import org.jaldrishti.domain.FieldReportRecord;
import org.jaldrishti.domain.SenderTrust;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound webhook receiver for Telegram Bot updates (text messages, voice notes, inline button callbacks).
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final ProcessFieldReportUseCase processFieldReportUseCase;
    private final AcknowledgeActionUseCase acknowledgeUseCase;

    public TelegramWebhookController(
            ProcessFieldReportUseCase processFieldReportUseCase,
            AcknowledgeActionUseCase acknowledgeUseCase
    ) {
        this.processFieldReportUseCase = processFieldReportUseCase;
        this.acknowledgeUseCase = acknowledgeUseCase;
    }

    @PostMapping("/telegram")
    public ResponseEntity<Map<String, Object>> handleTelegramUpdate(
            @RequestBody Map<String, Object> update,
            @RequestParam(defaultValue = "sim") String runId
    ) {
        log.info("Received Telegram webhook update");

        // Handle inline button callback for action ack: callback_query.data = "ack:{actionId}"
        if (update.containsKey("callback_query")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cb = (Map<String, Object>) update.get("callback_query");
            String data = (String) cb.get("data");
            if (data != null && data.startsWith("ack:")) {
                String actionIdStr = data.substring(4);
                try {
                    UUID actionId = UUID.fromString(actionIdStr);
                    AckResult result = acknowledgeUseCase.acknowledge(actionId, "{\"via\":\"TELEGRAM_BUTTON\"}", Instant.now());
                    return ResponseEntity.ok(Map.of("status", "ACKNOWLEDGED", "actionId", actionId, "result", result));
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid action UUID"));
                }
            }
        }

        // Handle message (voice note or text report)
        if (update.containsKey("message")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) update.get("message");
            String text = (String) msg.get("text");
            @SuppressWarnings("unchecked")
            Map<String, Object> voice = (Map<String, Object>) msg.get("voice");

            String audioUri = null;
            if (voice != null) {
                audioUri = (String) voice.get("file_id");
            }

            FieldReportCommand cmd = new FieldReportCommand(
                    runId,
                    Instant.now(),
                    "telegram-chat-hash",
                    SenderTrust.OBSERVER,
                    "telegram",
                    audioUri,
                    text
            );

            FieldReportRecord report = processFieldReportUseCase.processFieldReport(cmd);
            return ResponseEntity.ok(Map.of("status", "REPORT_PROCESSED", "reportId", report.id()));
        }

        return ResponseEntity.ok(Map.of("status", "IGNORED"));
    }
}
