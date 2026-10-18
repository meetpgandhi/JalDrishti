package org.jaldrishti.application.service;

import org.jaldrishti.application.port.in.ReadingCommand;
import org.jaldrishti.application.port.in.SweepResult;
import org.jaldrishti.application.port.out.CorridorConfigRepository;
import org.jaldrishti.application.port.out.IncidentEventPublisher;
import org.jaldrishti.application.port.out.IncidentEventRepository;
import org.jaldrishti.application.port.out.IncidentRepository;
import org.jaldrishti.application.port.out.ReadingRepository;
import org.jaldrishti.application.port.out.StationStateRepository;
import org.jaldrishti.domain.AlertTag;
import org.jaldrishti.domain.ArrivalTimeCalculator;
import org.jaldrishti.domain.CorridorConfig;
import org.jaldrishti.domain.Incident;
import org.jaldrishti.domain.IncidentEvent;
import org.jaldrishti.domain.IncidentLevel;
import org.jaldrishti.domain.Reading;
import org.jaldrishti.domain.Site;
import org.jaldrishti.domain.Station;
import org.jaldrishti.domain.StationObservationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TriggerEngineTest {

    private ReadingRepository readingRepo;
    private StationStateRepository stationStateRepo;
    private IncidentRepository incidentRepo;
    private IncidentEventRepository incidentEventRepo;
    private CorridorConfigRepository configRepo;
    private IncidentEventPublisher eventPublisher;
    private TriggerEngine engine;

    private final CorridorConfig config = CorridorConfig.defaultConfig();
    private final String runId = "test-s1-run";
    private final String corridorId = "bhote-trishuli-narayani";

    private final Station rsw = new Station(
            "RSW", corridorId, "Rasuwagadhi", "Bhote Koshi", 0.0, 28.2887, 85.5282, 6.0, null, 10, "vsat-rasuwa"
    );
    private final Station syp = new Station(
            "SYP", corridorId, "Syaphrubesi", "Bhote Koshi", 15.0, 28.168, 85.371, 5.5, null, 10, "vsat-rasuwa"
    );
    private final Station btw = new Station(
            "BTW", corridorId, "Betrawati", "Trishuli", 38.0, 27.974, 85.181, 4.1, null, 10, "gsm-nuwakot"
    );

    private final Map<String, StationObservationState> stateMap = new HashMap<>();
    private final List<Incident> savedIncidents = new ArrayList<>();
    private final List<IncidentEvent> appendedEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        readingRepo = mock(ReadingRepository.class);
        stationStateRepo = mock(StationStateRepository.class);
        incidentRepo = mock(IncidentRepository.class);
        incidentEventRepo = mock(IncidentEventRepository.class);
        configRepo = mock(CorridorConfigRepository.class);
        eventPublisher = mock(IncidentEventPublisher.class);

        stateMap.clear();
        savedIncidents.clear();
        appendedEvents.clear();

        when(stationStateRepo.find(eq(runId), any())).thenAnswer(inv -> {
            String stId = inv.getArgument(1);
            return Optional.ofNullable(stateMap.get(stId));
        });

        org.mockito.Mockito.doAnswer(inv -> {
            StationObservationState st = inv.getArgument(0);
            stateMap.put(st.stationId(), st);
            return null;
        }).when(stationStateRepo).save(any());

        when(incidentRepo.findOpenIncident(eq(runId), eq(corridorId))).thenAnswer(inv -> {
            return savedIncidents.stream().filter(Incident::isOpen).findFirst();
        });

        org.mockito.Mockito.doAnswer(inv -> {
            Incident inc = inv.getArgument(0);
            savedIncidents.removeIf(i -> i.id().equals(inc.id()));
            savedIncidents.add(inc);
            return null;
        }).when(incidentRepo).save(any());

        org.mockito.Mockito.doAnswer(inv -> {
            IncidentEvent ev = inv.getArgument(0);
            appendedEvents.add(ev);
            return null;
        }).when(incidentEventRepo).append(any());

        when(configRepo.findCurrent(corridorId)).thenReturn(Optional.of(config));
        when(configRepo.findStations(corridorId)).thenReturn(List.of(rsw, syp, btw));
        when(configRepo.findStationById("RSW")).thenReturn(Optional.of(rsw));
        when(configRepo.findStationById("SYP")).thenReturn(Optional.of(syp));
        when(configRepo.findStationById("BTW")).thenReturn(Optional.of(btw));

        Site siteBTW = new Site("BTW-S", corridorId, "Betrawati", "बेत्रावती", List.of(), "SETTLEMENT", 38.0, 27.974, 85.181, List.of());
        when(configRepo.findSites(corridorId)).thenReturn(List.of(siteBTW));

        Clock clock = Clock.fixed(Instant.parse("2026-08-26T03:08:00Z"), ZoneId.of("UTC")); // 08:53 NPT
        engine = new TriggerEngine(
                clock, readingRepo, stationStateRepo, incidentRepo, incidentEventRepo,
                configRepo, eventPublisher, ArrivalTimeCalculator.defaultCorridorCurve()
        );
    }

    @Test
    @DisplayName("Scenario S1 Target: 08:40 readings -> 08:50 lost -> 08:53 sweep fires R5 and WARNING")
    void s1ScenarioProducesWarningAt0853() {
        // 08:40:00 NPT = 02:55:00 UTC
        Instant t0840 = Instant.parse("2026-08-26T02:55:00Z");

        // 1. Ingest 08:40 readings for RSW and SYP
        engine.ingest(new ReadingCommand(runId, "RSW", t0840, 1.62, 0.0, t0840, "DHM", AlertTag.P));
        engine.ingest(new ReadingCommand(runId, "SYP", t0840, 3.80, 0.0, t0840, "DHM", AlertTag.P));

        assertThat(stateMap.get("RSW").lastGoodReadingAt()).isEqualTo(t0840);
        assertThat(stateMap.get("SYP").lastGoodReadingAt()).isEqualTo(t0840);
        assertThat(savedIncidents).isEmpty(); // Both readings are below warning thresholds

        // 2. 08:53 sweep tick: 3-min grace expires for 08:50 slot
        Instant t0853 = Instant.parse("2026-08-26T03:08:01Z"); // 08:53:01 NPT
        SweepResult sweepResult = engine.sweep(runId, corridorId, t0853);

        // Verify: Both RSW and SYP identified as silent
        assertThat(sweepResult.silentStationIds()).contains("RSW", "SYP");

        // Verify: Rule R5 cascade fired and elevated to WARNING
        assertThat(sweepResult.activeIncidentLevel()).isEqualTo(IncidentLevel.WARNING);
        assertThat(sweepResult.firedRules()).anyMatch(r -> r.ruleId().equals("R5") && r.resultingLevel() == IncidentLevel.WARNING);

        // Verify: Incident created and bands computed
        assertThat(savedIncidents).hasSize(1);
        Incident incident = savedIncidents.get(0);
        assertThat(incident.level()).isEqualTo(IncidentLevel.WARNING);

        // Events appended: OPENED (R3), LEVEL_CHANGED (R5), BANDS_UPDATED
        assertThat(appendedEvents).anyMatch(e -> e.ruleId().equals("R5"));
        assertThat(appendedEvents).anyMatch(e -> e.ruleId().equals("ARRIVAL_MODEL"));
    }

    @Test
    @DisplayName("Scenario S3: Single gauge (SYP) loses power -> internal WATCH only, 0 public WARNINGs")
    void s3SingleGaugeFailureDoesNotTriggerWarning() {
        Instant t1415 = Instant.parse("2026-08-26T08:30:00Z");

        // SYP had reading at 14:15, RSW is healthy at 14:25
        engine.ingest(new ReadingCommand(runId, "SYP", t1415, 3.2, 0.0, t1415, "DHM", AlertTag.A));
        engine.ingest(new ReadingCommand(runId, "RSW", t1415.plusSeconds(600), 1.6, 0.0, t1415.plusSeconds(600), "DHM", AlertTag.A));

        // Sweep at 14:28:01 (SYP is silent, RSW is NOT silent)
        Instant sweepTime = t1415.plusSeconds(13 * 60 + 1);
        SweepResult result = engine.sweep(runId, corridorId, sweepTime);

        assertThat(result.silentStationIds()).containsExactly("SYP");
        assertThat(result.activeIncidentLevel()).isEqualTo(IncidentLevel.WATCH); // Internal only
        assertThat(result.firedRules()).noneMatch(r -> r.resultingLevel() == IncidentLevel.WARNING);

        // Verify no WARNING was produced
        assertThat(savedIncidents.get(0).level()).isEqualTo(IncidentLevel.WATCH);
    }
}
