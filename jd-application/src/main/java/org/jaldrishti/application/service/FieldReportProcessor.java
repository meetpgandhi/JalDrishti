package org.jaldrishti.application.service;

import org.jaldrishti.application.port.in.FieldReportCommand;
import org.jaldrishti.application.port.in.IngestFieldReportUseCase;
import org.jaldrishti.application.port.in.ProcessFieldReportUseCase;
import org.jaldrishti.application.port.out.CorridorConfigRepository;
import org.jaldrishti.application.port.out.FieldReportRepository;
import org.jaldrishti.application.port.out.GeminiFieldReportParserPort;
import org.jaldrishti.domain.CorridorConfig;
import org.jaldrishti.domain.FieldReportObservation;
import org.jaldrishti.domain.FieldReportRecord;
import org.jaldrishti.domain.ReportStatus;
import org.jaldrishti.domain.Site;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Service orchestrating field-report ingestion: saves raw report, invokes Gemini
 * parser on Vertex AI, validates observation, and passes to trigger engine.
 */
public class FieldReportProcessor implements ProcessFieldReportUseCase {

    private static final Logger log = LoggerFactory.getLogger(FieldReportProcessor.class);

    private final FieldReportRepository fieldReportRepository;
    private final GeminiFieldReportParserPort parserPort;
    private final IngestFieldReportUseCase triggerEngine;
    private final CorridorConfigRepository corridorConfigRepository;
    private final Clock clock;

    public FieldReportProcessor(
            FieldReportRepository fieldReportRepository,
            GeminiFieldReportParserPort parserPort,
            IngestFieldReportUseCase triggerEngine,
            CorridorConfigRepository corridorConfigRepository,
            Clock clock
    ) {
        this.fieldReportRepository = Objects.requireNonNull(fieldReportRepository, "fieldReportRepository must not be null");
        this.parserPort = Objects.requireNonNull(parserPort, "parserPort must not be null");
        this.triggerEngine = Objects.requireNonNull(triggerEngine, "triggerEngine must not be null");
        this.corridorConfigRepository = corridorConfigRepository;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public FieldReportRecord processFieldReport(FieldReportCommand command) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();

        FieldReportRecord rawRecord = new FieldReportRecord(
                id,
                command.runId(),
                command.receivedAt() != null ? command.receivedAt() : now,
                command.senderHash(),
                command.trust(),
                command.channel(),
                command.audioUri(),
                command.rawText(),
                null,
                ReportStatus.PENDING,
                "gemini-3.6-flash"
        );
        fieldReportRepository.save(rawRecord);

        // Fetch corridor gazetteer sites
        List<Site> gazetteer = Collections.emptyList();
        if (corridorConfigRepository != null) {
            gazetteer = corridorConfigRepository.findSites("bhote-trishuli-narayani");
        }

        // Call Gemini parser
        FieldReportObservation observation = parserPort.parse(rawRecord, gazetteer);

        ReportStatus finalStatus;
        if (observation != null && observation.confidence() >= 0.5) {
            finalStatus = ReportStatus.PARSED;
            // Dispatch to deterministic trigger engine (R7 / R8)
            triggerEngine.handleFieldReport(observation);
            log.info("Field report {} PARSED successfully. Location={}, Hazard={}, Conf={}",
                    id, observation.locationId(), observation.hazardType(), observation.confidence());
        } else {
            finalStatus = ReportStatus.UNPARSED;
            log.warn("Field report {} marked UNPARSED (low confidence or location unresolved)", id);
        }

        String parsedJson = observation != null
                ? String.format("{\"locationId\":\"%s\",\"hazardType\":\"%s\",\"confidence\":%.2f}",
                observation.locationId(), observation.hazardType(), observation.confidence())
                : "{}";

        FieldReportRecord processed = rawRecord.withParsed(parsedJson, finalStatus, "gemini-3.6-flash");
        fieldReportRepository.save(processed);

        return processed;
    }
}
