package org.jaldrishti.application.service;

import org.jaldrishti.application.port.in.DispatchIncidentActionsUseCase;
import org.jaldrishti.application.port.in.FieldReportCommand;
import org.jaldrishti.application.port.in.ForecasterActionUseCase;
import org.jaldrishti.application.port.in.IngestFieldReportUseCase;
import org.jaldrishti.application.port.in.IngestReadingUseCase;
import org.jaldrishti.application.port.in.ReadingCommand;
import org.jaldrishti.application.port.in.RunSilenceSweepUseCase;
import org.jaldrishti.application.port.in.SweepResult;
import org.jaldrishti.application.port.out.CorridorConfigRepository;
import org.jaldrishti.application.port.out.IncidentEventPublisher;
import org.jaldrishti.application.port.out.IncidentEventRepository;
import org.jaldrishti.application.port.out.IncidentRepository;
import org.jaldrishti.application.port.out.LiveTelemetryPublisher;
import org.jaldrishti.application.port.out.ReadingRepository;
import org.jaldrishti.application.port.out.StationStateRepository;
import org.jaldrishti.domain.AlertTag;
import org.jaldrishti.domain.ArrivalBand;
import org.jaldrishti.domain.ArrivalTimeCalculator;
import org.jaldrishti.domain.CorridorConfig;
import org.jaldrishti.domain.EventType;
import org.jaldrishti.domain.FieldReportObservation;
import org.jaldrishti.domain.Incident;
import org.jaldrishti.domain.IncidentEvent;
import org.jaldrishti.domain.IncidentLevel;
import org.jaldrishti.domain.R10HeightenedWatchRule;
import org.jaldrishti.domain.R11AutoCancelRule;
import org.jaldrishti.domain.R1HeightRule;
import org.jaldrishti.domain.R2RateRule;
import org.jaldrishti.domain.R3SilenceRule;
import org.jaldrishti.domain.R4OutageRule;
import org.jaldrishti.domain.R5CascadeRule;
import org.jaldrishti.domain.R6NoRainRule;
import org.jaldrishti.domain.R7TrustedReportRule;
import org.jaldrishti.domain.R8ObserverRule;
import org.jaldrishti.domain.Reading;
import org.jaldrishti.domain.RuleResult;
import org.jaldrishti.domain.Site;
import org.jaldrishti.domain.Station;
import org.jaldrishti.domain.StationObservationState;
import org.jaldrishti.domain.StationState;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * TriggerEngine — Core application orchestration service.
 *
 * <p>Implements inbound use cases:
 * <ul>
 *   <li>{@link IngestReadingUseCase} — evaluates R1, R2, R6, R11 on each sensor reading</li>
 *   <li>{@link RunSilenceSweepUseCase} — evaluates R4, R3, R5, R10 on periodic clock ticks</li>
 *   <li>{@link IngestFieldReportUseCase} — evaluates R7, R8 on parsed Gemini reports</li>
 *   <li>{@link ForecasterActionUseCase} — manual forecaster escalations and stand-downs</li>
 * </ul>
 *
 * <p><strong>CRITICAL ARCHITECTURE RULES:</strong>
 * <ul>
 *   <li>Zero AI in decision engine — purely deterministic Java rules over versioned config.</li>
 *   <li>All time logic accesses injected {@link Clock}.</li>
 *   <li>Immutable events appended to {@link IncidentEventRepository}.</li>
 * </ul>
 */
public class TriggerEngine implements
        IngestReadingUseCase,
        RunSilenceSweepUseCase,
        IngestFieldReportUseCase,
        ForecasterActionUseCase {

    private final Clock clock;
    private final ReadingRepository readingRepository;
    private final StationStateRepository stationStateRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final CorridorConfigRepository corridorConfigRepository;
    private final IncidentEventPublisher incidentEventPublisher;
    private final ArrivalTimeCalculator arrivalTimeCalculator;
    private final DispatchIncidentActionsUseCase actionDispatcher;
    private final LiveTelemetryPublisher liveTelemetryPublisher;

    public TriggerEngine(
            Clock clock,
            ReadingRepository readingRepository,
            StationStateRepository stationStateRepository,
            IncidentRepository incidentRepository,
            IncidentEventRepository incidentEventRepository,
            CorridorConfigRepository corridorConfigRepository,
            IncidentEventPublisher incidentEventPublisher,
            ArrivalTimeCalculator arrivalTimeCalculator
    ) {
        this(clock, readingRepository, stationStateRepository, incidentRepository, incidentEventRepository,
                corridorConfigRepository, incidentEventPublisher, arrivalTimeCalculator, null, null);
    }

    public TriggerEngine(
            Clock clock,
            ReadingRepository readingRepository,
            StationStateRepository stationStateRepository,
            IncidentRepository incidentRepository,
            IncidentEventRepository incidentEventRepository,
            CorridorConfigRepository corridorConfigRepository,
            IncidentEventPublisher incidentEventPublisher,
            ArrivalTimeCalculator arrivalTimeCalculator,
            DispatchIncidentActionsUseCase actionDispatcher
    ) {
        this(clock, readingRepository, stationStateRepository, incidentRepository, incidentEventRepository,
                corridorConfigRepository, incidentEventPublisher, arrivalTimeCalculator, actionDispatcher, null);
    }

    public TriggerEngine(
            Clock clock,
            ReadingRepository readingRepository,
            StationStateRepository stationStateRepository,
            IncidentRepository incidentRepository,
            IncidentEventRepository incidentEventRepository,
            CorridorConfigRepository corridorConfigRepository,
            IncidentEventPublisher incidentEventPublisher,
            ArrivalTimeCalculator arrivalTimeCalculator,
            DispatchIncidentActionsUseCase actionDispatcher,
            LiveTelemetryPublisher liveTelemetryPublisher
    ) {
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.readingRepository = Objects.requireNonNull(readingRepository, "readingRepository must not be null");
        this.stationStateRepository = Objects.requireNonNull(stationStateRepository, "stationStateRepository must not be null");
        this.incidentRepository = Objects.requireNonNull(incidentRepository, "incidentRepository must not be null");
        this.incidentEventRepository = Objects.requireNonNull(incidentEventRepository, "incidentEventRepository must not be null");
        this.corridorConfigRepository = Objects.requireNonNull(corridorConfigRepository, "corridorConfigRepository must not be null");
        this.incidentEventPublisher = Objects.requireNonNull(incidentEventPublisher, "incidentEventPublisher must not be null");
        this.arrivalTimeCalculator = arrivalTimeCalculator != null ? arrivalTimeCalculator : ArrivalTimeCalculator.defaultCorridorCurve();
        this.actionDispatcher = actionDispatcher;
        this.liveTelemetryPublisher = liveTelemetryPublisher;
    }

    private void publishStationStateUpdate(StationObservationState state) {
        if (liveTelemetryPublisher == null) {
            return;
        }
        liveTelemetryPublisher.publishStationState(state.runId(), new LiveTelemetryPublisher.StationStateUpdate(
                state.stationId(),
                state.effectiveState().name(),
                state.silent(),
                state.rateAlert(),
                state.lastReportedLevel(),
                state.lastGoodReadingAt()
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // IngestReadingUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void ingest(ReadingCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Reading reading = new Reading(
                command.runId(),
                command.stationId(),
                command.observedAt(),
                command.levelM(),
                command.rainMm(),
                command.receivedAt(),
                command.source(),
                command.tag()
        );

        readingRepository.save(reading);

        Optional<Station> stationOpt = corridorConfigRepository.findStationById(command.stationId());
        if (stationOpt.isEmpty()) {
            return;
        }
        Station station = stationOpt.get();
        String corridorId = station.corridorId();
        CorridorConfig config = corridorConfigRepository.findCurrent(corridorId)
                .orElse(CorridorConfig.defaultConfig());

        StationObservationState currentState = stationStateRepository.find(command.runId(), command.stationId())
                .orElse(StationObservationState.initial(command.runId(), command.stationId()));

        // Rule R1: Height Band
        RuleResult r1Result = R1HeightRule.evaluate(reading, station);
        StationState newBand = R1HeightRule.determineBand(reading, station);

        // Rule R2: Rate of Rise
        Instant oneHourAgo = command.observedAt().minus(Duration.ofMinutes(60));
        List<Reading> recentReadings = readingRepository.findRecent(command.runId(), command.stationId(), oneHourAgo);
        boolean heightened = corridorConfigRepository.isHeightenedWatchActive(command.runId(), corridorId);
        RuleResult r2Result = R2RateRule.evaluate(reading, recentReadings, config, heightened);

        // Rule R6: Rate without Rain
        RuleResult r6Result = null;
        if (r2Result.fired()) {
            double rain3h = corridorConfigRepository.getCatchmentRainMm3h(command.runId(), corridorId);
            r6Result = R6NoRainRule.evaluate(r2Result, rain3h, config);
        }

        // Rule R11: Auto-cancel check if station was silent and returned
        Optional<Incident> openIncidentOpt = incidentRepository.findOpenIncident(command.runId(), corridorId);
        if (currentState.silent() && openIncidentOpt.isPresent()) {
            RuleResult r11Result = R11AutoCancelRule.evaluate(openIncidentOpt.get(), station, reading, null, false);
            if (r11Result.fired()) {
                standDownIncident(openIncidentOpt.get(), r11Result.description(), command.observedAt());
            }
        }

        // Update station state projection
        boolean isRateAlert = r2Result.fired();
        Instant rateAlertAt = isRateAlert ? command.observedAt() : (currentState.rateAlert() ? currentState.rateAlertAt() : null);

        StationObservationState newState = new StationObservationState(
                command.runId(),
                command.stationId(),
                newBand,
                isRateAlert,
                rateAlertAt,
                command.observedAt(),
                null,
                false,
                null,
                false,
                null,
                command.levelM(),
                command.rainMm()
        );
        stationStateRepository.save(newState);
        publishStationStateUpdate(newState);
        if (liveTelemetryPublisher != null) {
            liveTelemetryPublisher.publishReading(command.runId(), new LiveTelemetryPublisher.ReadingUpdate(
                    command.stationId(), command.observedAt(), command.levelM(), command.rainMm(), command.tag().name()
            ));
        }

        // Elevate/update incident if rules fired
        if (r6Result != null && r6Result.fired()) {
            raiseOrUpdateIncident(command.runId(), corridorId, IncidentLevel.WARNING, "R6",
                    r6Result.description(), r6Result.evidence(), station.id(), config.version(), true, command.observedAt());
        } else if (r1Result.fired()) {
            raiseOrUpdateIncident(command.runId(), corridorId, r1Result.resultingLevel(), "R1",
                    r1Result.description(), r1Result.evidence(), station.id(), config.version(), false, command.observedAt());
        } else if (r2Result.fired()) {
            raiseOrUpdateIncident(command.runId(), corridorId, r2Result.resultingLevel(), "R2",
                    r2Result.description(), r2Result.evidence(), station.id(), config.version(), false, command.observedAt());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RunSilenceSweepUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public SweepResult sweep(String runId, String corridorId, Instant now) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(corridorId, "corridorId must not be null");
        Instant sweepTime = now != null ? now : clock.instant();

        CorridorConfig config = corridorConfigRepository.findCurrent(corridorId)
                .orElse(CorridorConfig.defaultConfig());
        List<Station> stations = corridorConfigRepository.findStations(corridorId);

        List<String> silentStationIds = new ArrayList<>();
        List<RuleResult> firedRules = new ArrayList<>();

        // Group stations by telemetry group
        Map<String, List<Station>> stationsByGroup = new HashMap<>();
        for (Station s : stations) {
            stationsByGroup.computeIfAbsent(s.telemetryGroup(), k -> new ArrayList<>()).add(s);
        }

        // 1. Evaluate Rule R4 (telemetry carrier failure) per group
        Map<String, Boolean> groupOutageMap = new HashMap<>();
        for (Map.Entry<String, List<Station>> entry : stationsByGroup.entrySet()) {
            String groupName = entry.getKey();
            List<StationObservationState> groupStates = new ArrayList<>();
            for (Station s : entry.getValue()) {
                StationObservationState st = stationStateRepository.find(runId, s.id())
                        .orElse(StationObservationState.initial(runId, s.id()));
                groupStates.add(st);
            }
            RuleResult r4Result = R4OutageRule.evaluate(groupName, groupStates, config, sweepTime);
            if (r4Result.fired() && r4Result.suppressesSilence()) {
                groupOutageMap.put(groupName, true);
                firedRules.add(r4Result);
                for (Station s : entry.getValue()) {
                    StationObservationState current = stationStateRepository.find(runId, s.id())
                            .orElse(StationObservationState.initial(runId, s.id()));
                    if (current.silent() || current.telemetryOutage()) {
                        StationObservationState outageState = new StationObservationState(
                                runId, s.id(), current.band(), current.rateAlert(), current.rateAlertAt(),
                                current.lastGoodReadingAt(), current.firstMissedSlotAt(), false, null,
                                true, (String) r4Result.evidence().get("outageTicket"),
                                current.lastReportedLevel(), current.lastReportedRain()
                        );
                        stationStateRepository.save(outageState);
                        publishStationStateUpdate(outageState);
                    }
                }
            } else {
                groupOutageMap.put(groupName, false);
            }
        }

        // 2. Evaluate Rule R3 (silence) for stations not in a group outage
        for (Station s : stations) {
            boolean inGroupOutage = groupOutageMap.getOrDefault(s.telemetryGroup(), false);
            if (inGroupOutage) {
                continue;
            }

            StationObservationState current = stationStateRepository.find(runId, s.id())
                    .orElse(StationObservationState.initial(runId, s.id()));

            RuleResult r3Result = R3SilenceRule.evaluate(s, current.lastGoodReadingAt(), sweepTime, config);
            if (r3Result.fired()) {
                silentStationIds.add(s.id());
                firedRules.add(r3Result);

                Instant firstMissed = current.lastGoodReadingAt() != null
                        ? current.lastGoodReadingAt().plus(Duration.ofMinutes(s.cadenceMin()))
                        : sweepTime;

                StationObservationState silentState = new StationObservationState(
                        runId, s.id(), current.band(), current.rateAlert(), current.rateAlertAt(),
                        current.lastGoodReadingAt(), firstMissed, true,
                        current.silent() ? current.silentSince() : sweepTime,
                        false, null, current.lastReportedLevel(), current.lastReportedRain()
                );
                stationStateRepository.save(silentState);
                publishStationStateUpdate(silentState);

                // Check Rule R10: Heightened watch at top station elevates silence to WARNING directly
                boolean heightened = corridorConfigRepository.isHeightenedWatchActive(runId, corridorId);
                boolean isTopStation = (s.riverKm() == 0.0);
                RuleResult r10Result = R10HeightenedWatchRule.evaluate(heightened, s, isTopStation, true);

                if (r10Result.fired()) {
                    firedRules.add(r10Result);
                    raiseOrUpdateIncident(runId, corridorId, IncidentLevel.WARNING, "R10",
                            r10Result.description(), r10Result.evidence(), s.id(), config.version(), false, sweepTime);
                } else {
                    raiseOrUpdateIncident(runId, corridorId, IncidentLevel.WATCH, "R3",
                            r3Result.description(), r3Result.evidence(), s.id(), config.version(), false, sweepTime);
                }
            }
        }

        // 3. Evaluate Rule R5 (Cascade Corroboration) across all station pairs
        List<Station> sortedStations = new ArrayList<>(stations);
        sortedStations.sort(Comparator.comparingDouble(Station::riverKm));

        for (int i = 0; i < sortedStations.size(); i++) {
            Station stI = sortedStations.get(i);
            StationObservationState stateI = stationStateRepository.find(runId, stI.id()).orElse(null);
            if (stateI == null || !stateI.silent() || stateI.lastGoodReadingAt() == null || stateI.firstMissedSlotAt() == null) {
                continue;
            }

            for (int j = i + 1; j < sortedStations.size(); j++) {
                Station stJ = sortedStations.get(j);
                StationObservationState stateJ = stationStateRepository.find(runId, stJ.id()).orElse(null);
                if (stateJ == null) {
                    continue;
                }

                RuleResult r5Result = R5CascadeRule.evaluate(
                        stI, stateI.lastGoodReadingAt(), stateI.firstMissedSlotAt(),
                        stJ, stateJ, config
                );

                if (r5Result.fired()) {
                    firedRules.add(r5Result);
                    raiseOrUpdateIncident(runId, corridorId, IncidentLevel.WARNING, "R5",
                            r5Result.description(), r5Result.evidence(), stJ.id(), config.version(), false, sweepTime);
                }
            }
        }

        IncidentLevel activeLevel = incidentRepository.findOpenIncident(runId, corridorId)
                .map(Incident::level)
                .orElse(IncidentLevel.NORMAL);

        return new SweepResult(runId, corridorId, sweepTime, stations.size(), silentStationIds, firedRules, activeLevel);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // IngestFieldReportUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void ingest(FieldReportCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        FieldReportObservation report = new FieldReportObservation(
                command.reportId(),
                command.runId(),
                command.receivedAt(),
                command.senderHash(),
                command.trust(),
                command.channel(),
                command.language(),
                command.transcript(),
                command.translationEn(),
                command.locationId(),
                command.locationText(),
                command.hazardType(),
                command.evidence(),
                command.observedAt(),
                command.peopleAtRiskCount(),
                command.confidence(),
                command.needsCallback()
        );

        handleFieldReport(report);
    }

    @Override
    public void handleFieldReport(FieldReportObservation report) {
        Objects.requireNonNull(report, "report must not be null");

        String corridorId = "bhote-trishuli-narayani"; // Default pilot corridor
        CorridorConfig config = corridorConfigRepository.findCurrent(corridorId)
                .orElse(CorridorConfig.defaultConfig());

        // Rule R7: Trusted Report (OFFICIAL)
        RuleResult r7Result = R7TrustedReportRule.evaluate(report, config);
        if (r7Result.fired()) {
            raiseOrUpdateIncident(report.runId(), corridorId, IncidentLevel.WARNING, "R7",
                    r7Result.description(), r7Result.evidence(), report.locationId(), config.version(), false, report.receivedAt());
            return;
        }

        // Rule R8: Observer Reports
        Optional<Incident> openIncidentOpt = incidentRepository.findOpenIncident(report.runId(), corridorId);
        boolean sensorEvidencePresent = openIncidentOpt.isPresent();

        Map<String, Site> sitesById = new HashMap<>();
        for (Site site : corridorConfigRepository.findSites(corridorId)) {
            sitesById.put(site.id(), site);
        }

        RuleResult r8Result = R8ObserverRule.evaluate(report, Collections.emptyList(), sitesById, sensorEvidencePresent, config);
        if (r8Result.fired()) {
            raiseOrUpdateIncident(report.runId(), corridorId, IncidentLevel.WARNING, "R8",
                    r8Result.description(), r8Result.evidence(), report.locationId(), config.version(), false, report.receivedAt());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ForecasterActionUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void escalateToWarning(UUID incidentId, String reason, Instant now) {
        Objects.requireNonNull(incidentId, "incidentId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Instant at = now != null ? now : clock.instant();

        Optional<Incident> opt = incidentRepository.findById(incidentId);
        if (opt.isPresent() && opt.get().isOpen()) {
            Incident inc = opt.get();
            Incident escalated = inc.escalateToWarning(at, inc.anchorStation(), inc.nonRain());
            incidentRepository.save(escalated);

            IncidentEvent event = new IncidentEvent(
                    null, inc.id(), at, EventType.LEVEL_CHANGED, "FORECASTER_MANUAL",
                    String.format("{\"reason\":\"%s\"}", reason), AlertTag.P
            );
            incidentEventRepository.append(event);
            incidentEventPublisher.publish(inc.runId(), event);

            computeAndPublishArrivalBands(escalated, at);

            if (actionDispatcher != null) {
                actionDispatcher.dispatchActionsForIncident(escalated.runId(), escalated.id());
            }
        }
    }

    @Override
    public void cancelWatch(UUID incidentId, String reason, Instant now) {
        standDown(incidentId, reason, now);
    }

    @Override
    public void standDown(UUID incidentId, String reason, Instant now) {
        Objects.requireNonNull(incidentId, "incidentId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Instant at = now != null ? now : clock.instant();

        Optional<Incident> opt = incidentRepository.findById(incidentId);
        if (opt.isPresent() && opt.get().isOpen()) {
            standDownIncident(opt.get(), reason, at);
        }
    }

    @Override
    public void setHeightenedWatch(String runId, String corridorId, boolean active, String reason, Instant now) {
        corridorConfigRepository.setHeightenedWatchActive(runId, corridorId, active);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal Incident Lifecycle Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private synchronized void raiseOrUpdateIncident(
            String runId,
            String corridorId,
            IncidentLevel targetLevel,
            String ruleId,
            String description,
            Map<String, Object> evidence,
            String anchorStation,
            int configVersion,
            boolean nonRain,
            Instant at
    ) {
        Optional<Incident> openOpt = incidentRepository.findOpenIncident(runId, corridorId);
        int matrixVersion = 1; // Default Sprint 1 duty matrix version

        if (openOpt.isEmpty()) {
            UUID id = UUID.randomUUID();
            Incident incident;
            if (targetLevel == IncidentLevel.WARNING) {
                incident = Incident.createWarning(id, runId, corridorId, at, anchorStation, configVersion, matrixVersion, nonRain);
            } else {
                incident = Incident.createWatch(id, runId, corridorId, at, anchorStation, configVersion, matrixVersion);
            }
            incidentRepository.save(incident);

            IncidentEvent openEvent = new IncidentEvent(
                    null, id, at, EventType.OPENED, ruleId,
                    formatPayload(description, evidence), AlertTag.D
            );
            incidentEventRepository.append(openEvent);
            incidentEventPublisher.publish(runId, openEvent);

            if (targetLevel == IncidentLevel.WARNING) {
                computeAndPublishArrivalBands(incident, at);
            }

            if (actionDispatcher != null) {
                actionDispatcher.dispatchActionsForIncident(runId, incident.id());
            }
        } else {
            Incident current = openOpt.get();
            if (current.level() == IncidentLevel.WATCH && targetLevel == IncidentLevel.WARNING) {
                Incident elevated = current.escalateToWarning(at, anchorStation, nonRain);
                incidentRepository.save(elevated);

                IncidentEvent levelEvent = new IncidentEvent(
                        null, current.id(), at, EventType.LEVEL_CHANGED, ruleId,
                        formatPayload(description, evidence), AlertTag.D
                );
                incidentEventRepository.append(levelEvent);
                incidentEventPublisher.publish(runId, levelEvent);

                computeAndPublishArrivalBands(elevated, at);

                if (actionDispatcher != null) {
                    actionDispatcher.dispatchActionsForIncident(runId, elevated.id());
                }
            } else {
                // Record additional corroborating evidence
                IncidentEvent evidenceEvent = new IncidentEvent(
                        null, current.id(), at, EventType.EVIDENCE_ADDED, ruleId,
                        formatPayload(description, evidence), AlertTag.D
                );
                incidentEventRepository.append(evidenceEvent);
                incidentEventPublisher.publish(runId, evidenceEvent);
            }
        }
    }

    private void standDownIncident(Incident incident, String reason, Instant at) {
        Incident stoodDown = incident.standDown(at, reason);
        incidentRepository.save(stoodDown);

        IncidentEvent event = new IncidentEvent(
                null, incident.id(), at, EventType.STOOD_DOWN, "R11",
                String.format("{\"reason\":\"%s\"}", reason), AlertTag.D
        );
        incidentEventRepository.append(event);
        incidentEventPublisher.publish(incident.runId(), event);

        if (actionDispatcher != null) {
            actionDispatcher.dispatchActionsForIncident(incident.runId(), incident.id());
        }
    }

    private void computeAndPublishArrivalBands(Incident incident, Instant at) {
        List<Site> sites = corridorConfigRepository.findSites(incident.corridorId());
        Optional<Station> anchorOpt = corridorConfigRepository.findStationById(incident.anchorStation());
        double anchorKm = anchorOpt.map(Station::riverKm).orElse(0.0);

        StationObservationState anchorState = stationStateRepository.find(incident.runId(), incident.anchorStation())
                .orElse(null);
        Instant lastGood = anchorState != null && anchorState.lastGoodReadingAt() != null
                ? anchorState.lastGoodReadingAt() : at;
        Instant firstMissed = anchorState != null && anchorState.firstMissedSlotAt() != null
                ? anchorState.firstMissedSlotAt() : at.plus(Duration.ofMinutes(10));

        List<ArrivalBand> bands = new ArrayList<>();
        for (Site site : sites) {
            if (site.riverKm() >= anchorKm) {
                bands.add(arrivalTimeCalculator.calculateBand(site, anchorKm, lastGood, firstMissed));
            }
        }

        IncidentEvent bandEvent = new IncidentEvent(
                null, incident.id(), at, EventType.BANDS_UPDATED, "ARRIVAL_MODEL",
                String.format("{\"bandsCount\":%d,\"anchorKm\":%.1f}", bands.size(), anchorKm), AlertTag.D
        );
        incidentEventRepository.append(bandEvent);
        incidentEventPublisher.publish(incident.runId(), bandEvent);

        if (liveTelemetryPublisher != null) {
            liveTelemetryPublisher.publishArrivalBands(incident.runId(), bands);
        }
    }

    private String formatPayload(String description, Map<String, Object> evidence) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"description\":\"").append(description.replace("\"", "\\\"")).append("\"");
        if (evidence != null && !evidence.isEmpty()) {
            sb.append(",\"evidence\":{");
            int i = 0;
            for (Map.Entry<String, Object> e : evidence.entrySet()) {
                if (i++ > 0) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":");
                if (e.getValue() instanceof Number || e.getValue() instanceof Boolean) {
                    sb.append(e.getValue());
                } else {
                    sb.append("\"").append(String.valueOf(e.getValue()).replace("\"", "\\\"")).append("\"");
                }
            }
            sb.append("}");
        }
        sb.append("}");
        return sb.toString();
    }
}
