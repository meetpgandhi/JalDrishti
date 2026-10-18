package org.jaldrishti.boot;

import org.jaldrishti.adapter.cap.CapGatewayAdapter;
import org.jaldrishti.adapter.channels.ChannelDispatcher;
import org.jaldrishti.adapter.gemini.GeminiFieldReportParser;
import org.jaldrishti.adapter.gemini.GeminiMessageLocaliser;
import org.jaldrishti.adapter.gemini.ReviewedTemplateEngine;
import org.jaldrishti.application.port.out.CapMessageRepository;
import org.jaldrishti.application.port.out.CorridorConfigRepository;
import org.jaldrishti.application.port.out.DispatchedActionRepository;
import org.jaldrishti.application.port.out.DispatchedMessageRepository;
import org.jaldrishti.application.port.out.DutyHolderRepository;
import org.jaldrishti.application.port.out.DutyMatrixRepository;
import org.jaldrishti.application.port.out.FieldReportRepository;
import org.jaldrishti.application.port.out.IncidentEventPublisher;
import org.jaldrishti.application.port.out.IncidentEventRepository;
import org.jaldrishti.application.port.out.IncidentRepository;
import org.jaldrishti.application.port.out.ReadingRepository;
import org.jaldrishti.application.port.out.StationStateRepository;
import org.jaldrishti.application.service.ActionDispatcherService;
import org.jaldrishti.application.service.FieldReportProcessor;
import org.jaldrishti.application.service.TriggerEngine;
import org.jaldrishti.domain.ArrivalTimeCalculator;
import org.jaldrishti.sim.SimClock;
import org.jaldrishti.sim.SimScheduler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.Instant;

/**
 * Spring Boot wiring configuration for hexagonal ports, adapters, and domain engines.
 */
@Configuration
public class JalDrishtiWiringConfig {

    @Value("${jaldrishti.gemini.model:gemini-3.6-flash}")
    private String geminiModel;

    @Value("${jaldrishti.gemini.location:asia-south1}")
    private String geminiLocation;

    @Value("${jaldrishti.gemini.vertex-enabled:false}")
    private boolean vertexEnabled;

    @Bean
    @Profile("sim")
    @Primary
    public SimClock simClock() {
        // Starts at 08:00:00 NPT on 26 August 2026 (02:15:00 UTC)
        return SimClock.create(Instant.parse("2026-08-26T02:15:00Z"));
    }

    @Bean
    @Profile("!sim")
    @Primary
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public ArrivalTimeCalculator arrivalTimeCalculator() {
        return ArrivalTimeCalculator.defaultCorridorCurve();
    }

    @Bean
    public ReviewedTemplateEngine reviewedTemplateEngine() {
        return new ReviewedTemplateEngine();
    }

    @Bean
    public GeminiMessageLocaliser geminiMessageLocaliser(ReviewedTemplateEngine templateEngine) {
        return new GeminiMessageLocaliser(templateEngine, geminiModel, geminiLocation, vertexEnabled);
    }

    @Bean
    public GeminiFieldReportParser geminiFieldReportParser() {
        return new GeminiFieldReportParser(geminiModel, geminiLocation, vertexEnabled);
    }

    @Bean
    public CapGatewayAdapter capGatewayAdapter(CapMessageRepository capMessageRepository) {
        return new CapGatewayAdapter(capMessageRepository, "jaldrishti@dhm.gov.np");
    }

    @Bean
    public ActionDispatcherService actionDispatcherService(
            Clock clock,
            IncidentRepository incidentRepository,
            IncidentEventRepository incidentEventRepository,
            IncidentEventPublisher incidentEventPublisher,
            DutyMatrixRepository dutyMatrixRepository,
            DutyHolderRepository dutyHolderRepository,
            DispatchedActionRepository dispatchedActionRepository,
            DispatchedMessageRepository dispatchedMessageRepository,
            GeminiMessageLocaliser geminiMessageLocaliser,
            ChannelDispatcher channelDispatcher,
            CapGatewayAdapter capGatewayAdapter,
            ArrivalTimeCalculator arrivalTimeCalculator,
            CorridorConfigRepository corridorConfigRepository
    ) {
        return new ActionDispatcherService(
                clock,
                incidentRepository,
                incidentEventRepository,
                incidentEventPublisher,
                dutyMatrixRepository,
                dutyHolderRepository,
                dispatchedActionRepository,
                dispatchedMessageRepository,
                geminiMessageLocaliser,
                channelDispatcher,
                capGatewayAdapter,
                arrivalTimeCalculator,
                corridorConfigRepository
        );
    }

    @Bean
    public TriggerEngine triggerEngine(
            Clock clock,
            ReadingRepository readingRepository,
            StationStateRepository stationStateRepository,
            IncidentRepository incidentRepository,
            IncidentEventRepository incidentEventRepository,
            CorridorConfigRepository corridorConfigRepository,
            IncidentEventPublisher incidentEventPublisher,
            ArrivalTimeCalculator arrivalTimeCalculator,
            ActionDispatcherService actionDispatcherService
    ) {
        return new TriggerEngine(
                clock,
                readingRepository,
                stationStateRepository,
                incidentRepository,
                incidentEventRepository,
                corridorConfigRepository,
                incidentEventPublisher,
                arrivalTimeCalculator,
                actionDispatcherService
        );
    }

    @Bean
    public FieldReportProcessor fieldReportProcessor(
            FieldReportRepository fieldReportRepository,
            GeminiFieldReportParser geminiFieldReportParser,
            TriggerEngine triggerEngine,
            CorridorConfigRepository corridorConfigRepository,
            Clock clock
    ) {
        return new FieldReportProcessor(
                fieldReportRepository,
                geminiFieldReportParser,
                triggerEngine,
                corridorConfigRepository,
                clock
        );
    }

    @Bean
    public org.jaldrishti.application.service.ReplayReportService replayReportService(
            IncidentEventRepository incidentEventRepository,
            IncidentRepository incidentRepository,
            DispatchedActionRepository dispatchedActionRepository,
            Clock clock
    ) {
        return new org.jaldrishti.application.service.ReplayReportService(
                incidentEventRepository,
                incidentRepository,
                dispatchedActionRepository,
                clock
        );
    }

    @Bean
    @Profile("sim")
    public SimScheduler simScheduler(
            SimClock simClock,
            TriggerEngine triggerEngine,
            ActionDispatcherService actionDispatcherService
    ) {
        return new SimScheduler(simClock, triggerEngine, triggerEngine, actionDispatcherService);
    }
}
