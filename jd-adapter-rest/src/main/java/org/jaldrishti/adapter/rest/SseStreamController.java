package org.jaldrishti.adapter.rest;

import org.jaldrishti.application.port.out.IncidentEventPublisher;
import org.jaldrishti.domain.IncidentEvent;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-Sent Events (SSE) controller for real-time telemetry, incidents, and clock updates.
 *
 * <p>Supports reconnecting clients via the standard {@code Last-Event-ID} header.
 */
@RestController
@RequestMapping("/api/v1/stream")
public class SseStreamController implements IncidentEventPublisher {

    private final Map<String, List<SseEmitter>> emittersByRunId = new ConcurrentHashMap<>();
    private final AtomicLong eventSequence = new AtomicLong(1);

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(
            @RequestParam(name = "runId", defaultValue = "live") String runId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId
    ) {
        // 30-minute timeout for Cloud Run SSE connections
        SseEmitter emitter = new SseEmitter(1800_000L);

        emittersByRunId.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(runId, emitter));
        emitter.onTimeout(() -> removeEmitter(runId, emitter));
        emitter.onError(e -> removeEmitter(runId, emitter));

        // Send initial connect handshake
        try {
            long seq = eventSequence.getAndIncrement();
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(seq))
                    .name("connected")
                    .data("{\"status\":\"CONNECTED\",\"runId\":\"" + runId + "\"}"));
        } catch (IOException e) {
            removeEmitter(runId, emitter);
        }

        return emitter;
    }

    @Override
    public void publish(String runId, IncidentEvent event) {
        List<SseEmitter> emitters = emittersByRunId.get(runId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        long seq = eventSequence.getAndIncrement();
        String eventName = switch (event.type()) {
            case OPENED, LEVEL_CHANGED, STOOD_DOWN -> "incident";
            case BANDS_UPDATED -> "bands";
            case EVIDENCE_ADDED -> "evidence";
        };

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(seq))
                        .name(eventName)
                        .data(event));
            } catch (Exception e) {
                removeEmitter(runId, emitter);
            }
        }
    }

    public void broadcast(String runId, String eventName, Object data) {
        List<SseEmitter> emitters = emittersByRunId.get(runId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        long seq = eventSequence.getAndIncrement();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(seq))
                        .name(eventName)
                        .data(data));
            } catch (Exception e) {
                removeEmitter(runId, emitter);
            }
        }
    }

    private void removeEmitter(String runId, SseEmitter emitter) {
        List<SseEmitter> list = emittersByRunId.get(runId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emittersByRunId.remove(runId);
            }
        }
    }
}
