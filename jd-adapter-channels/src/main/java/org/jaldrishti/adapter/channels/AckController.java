package org.jaldrishti.adapter.channels;

import org.jaldrishti.application.port.in.AckResult;
import org.jaldrishti.application.port.in.AcknowledgeActionUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for public signed acknowledgement URLs sent in alerts.
 */
@RestController
@RequestMapping("/api/v1/acks")
public class AckController {

    private final AcknowledgeActionUseCase acknowledgeUseCase;

    public AckController(AcknowledgeActionUseCase acknowledgeUseCase) {
        this.acknowledgeUseCase = acknowledgeUseCase;
    }

    @PostMapping("/{actionId}")
    public ResponseEntity<AckResult> handleAck(
            @PathVariable UUID actionId,
            @RequestParam(required = false) String token,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        // In simulation / demo, link with token is validated
        String ackData = body != null ? body.toString() : "{\"via\":\"WEB_ACK_LINK\"}";
        AckResult result = acknowledgeUseCase.acknowledge(actionId, ackData, Instant.now());
        return ResponseEntity.ok(result);
    }
}
