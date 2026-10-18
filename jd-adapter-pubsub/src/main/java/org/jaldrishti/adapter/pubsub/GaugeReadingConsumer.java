package org.jaldrishti.adapter.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jaldrishti.application.port.in.IngestReadingUseCase;
import org.jaldrishti.application.port.in.ReadingCommand;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;

/**
 * Message receiver for the gauge-readings Pub/Sub subscription.
 */
public class GaugeReadingConsumer {

    private final IngestReadingUseCase ingestReadingUseCase;
    private final ObjectMapper objectMapper;

    public GaugeReadingConsumer(IngestReadingUseCase ingestReadingUseCase, ObjectMapper objectMapper) {
        this.ingestReadingUseCase = Objects.requireNonNull(ingestReadingUseCase, "ingestReadingUseCase must not be null");
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public void processJson(String messageJson) throws IOException {
        GaugeReadingPushPayload payload = objectMapper.readValue(messageJson, GaugeReadingPushPayload.class);
        ReadingCommand command = new ReadingCommand(
                payload.runId() != null ? payload.runId() : "live",
                payload.stationId(),
                payload.parseObservedAt(),
                payload.levelM(),
                payload.rainMm(),
                Instant.now(),
                payload.source() != null ? payload.source() : "SIM",
                payload.parseTag()
        );
        ingestReadingUseCase.ingest(command);
    }
}
