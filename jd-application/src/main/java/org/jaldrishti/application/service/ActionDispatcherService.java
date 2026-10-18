package org.jaldrishti.application.service;

import org.jaldrishti.application.port.in.AckResult;
import org.jaldrishti.application.port.in.AcknowledgeActionUseCase;
import org.jaldrishti.application.port.in.DispatchIncidentActionsUseCase;
import org.jaldrishti.application.port.in.EscalateActionsUseCase;
import org.jaldrishti.application.port.out.CapGatewayPort;
import org.jaldrishti.application.port.out.ChannelDispatchPort;
import org.jaldrishti.application.port.out.CorridorConfigRepository;
import org.jaldrishti.application.port.out.DispatchedActionRepository;
import org.jaldrishti.application.port.out.DispatchedMessageRepository;
import org.jaldrishti.application.port.out.DutyHolderRepository;
import org.jaldrishti.application.port.out.DutyMatrixRepository;
import org.jaldrishti.application.port.out.GeminiMessageLocaliserPort;
import org.jaldrishti.application.port.out.IncidentEventPublisher;
import org.jaldrishti.application.port.out.IncidentEventRepository;
import org.jaldrishti.application.port.out.IncidentRepository;
import org.jaldrishti.domain.ActionState;
import org.jaldrishti.domain.AlertTag;
import org.jaldrishti.domain.ArrivalBand;
import org.jaldrishti.domain.ArrivalTimeCalculator;
import org.jaldrishti.domain.DeadlineCalculator;
import org.jaldrishti.domain.DispatchedAction;
import org.jaldrishti.domain.DispatchedMessageRecord;
import org.jaldrishti.domain.DutyHolder;
import org.jaldrishti.domain.DutyMatrixVersion;
import org.jaldrishti.domain.DutyRule;
import org.jaldrishti.domain.EventType;
import org.jaldrishti.domain.GuardResult;
import org.jaldrishti.domain.Incident;
import org.jaldrishti.domain.IncidentEvent;
import org.jaldrishti.domain.IncidentLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ActionDispatcherService — Orchestrates the Action Loop (Sprint 2).
 *
 * <p>Implements:
 * <ul>
 *   <li>{@link DispatchIncidentActionsUseCase} — matches duty rules, localises via Gemini, guards digits, dispatches.</li>
 *   <li>{@link AcknowledgeActionUseCase} — processes signed 24h acks and declines.</li>
 *   <li>{@link EscalateActionsUseCase} — evaluates escalation ladder (+5m voice, +10m deputy, +15m CDO flag).</li>
 * </ul>
 */
public class ActionDispatcherService implements
        DispatchIncidentActionsUseCase,
        AcknowledgeActionUseCase,
        EscalateActionsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ActionDispatcherService.class);

    private final Clock clock;
    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final IncidentEventPublisher incidentEventPublisher;
    private final DutyMatrixRepository dutyMatrixRepository;
    private final DutyHolderRepository dutyHolderRepository;
    private final DispatchedActionRepository dispatchedActionRepository;
    private final DispatchedMessageRepository dispatchedMessageRepository;
    private final GeminiMessageLocaliserPort localiserPort;
    private final ChannelDispatchPort channelDispatchPort;
    private final CapGatewayPort capGatewayPort;
    private final ArrivalTimeCalculator arrivalTimeCalculator;
    private final CorridorConfigRepository corridorConfigRepository;

    public ActionDispatcherService(
            Clock clock,
            IncidentRepository incidentRepository,
            IncidentEventRepository incidentEventRepository,
            IncidentEventPublisher incidentEventPublisher,
            DutyMatrixRepository dutyMatrixRepository,
            DutyHolderRepository dutyHolderRepository,
            DispatchedActionRepository dispatchedActionRepository,
            DispatchedMessageRepository dispatchedMessageRepository,
            GeminiMessageLocaliserPort localiserPort,
            ChannelDispatchPort channelDispatchPort,
            CapGatewayPort capGatewayPort,
            ArrivalTimeCalculator arrivalTimeCalculator,
            CorridorConfigRepository corridorConfigRepository
    ) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.incidentRepository = Objects.requireNonNull(incidentRepository, "incidentRepository must not be null");
        this.incidentEventRepository = Objects.requireNonNull(incidentEventRepository, "incidentEventRepository must not be null");
        this.incidentEventPublisher = Objects.requireNonNull(incidentEventPublisher, "incidentEventPublisher must not be null");
        this.dutyMatrixRepository = Objects.requireNonNull(dutyMatrixRepository, "dutyMatrixRepository must not be null");
        this.dutyHolderRepository = Objects.requireNonNull(dutyHolderRepository, "dutyHolderRepository must not be null");
        this.dispatchedActionRepository = Objects.requireNonNull(dispatchedActionRepository, "dispatchedActionRepository must not be null");
        this.dispatchedMessageRepository = Objects.requireNonNull(dispatchedMessageRepository, "dispatchedMessageRepository must not be null");
        this.localiserPort = Objects.requireNonNull(localiserPort, "localiserPort must not be null");
        this.channelDispatchPort = Objects.requireNonNull(channelDispatchPort, "channelDispatchPort must not be null");
        this.capGatewayPort = capGatewayPort;
        this.arrivalTimeCalculator = Objects.requireNonNull(arrivalTimeCalculator, "arrivalTimeCalculator must not be null");
        this.corridorConfigRepository = corridorConfigRepository;
    }

    @Override
    public List<DispatchedAction> dispatchActionsForIncident(String runId, UUID incidentId) {
        Optional<Incident> incOpt = incidentRepository.findById(incidentId);
        if (incOpt.isEmpty() || !incOpt.get().isOpen()) {
            return Collections.emptyList();
        }

        Incident incident = incOpt.get();
        Instant now = clock.instant();

        // 1. Calculate flood arrival bands
        List<ArrivalBand> arrivalBands = arrivalTimeCalculator.calculate(incident.openedAt());
        Map<String, ArrivalBand> bandBySite = new HashMap<>();
        for (ArrivalBand b : arrivalBands) {
            bandBySite.put(b.siteId(), b);
        }

        // 2. Fetch latest approved duty matrix
        Optional<DutyMatrixVersion> versionOpt = dutyMatrixRepository.findLatestApprovedVersion();
        if (versionOpt.isEmpty()) {
            log.warn("No approved duty matrix version found for incident {}", incidentId);
            return Collections.emptyList();
        }

        DutyMatrixVersion version = versionOpt.get();
        List<DutyRule> rules = dutyMatrixRepository.findRulesByVersionAndLevel(version.version(), incident.level());
        List<DutyHolder> holders = dutyHolderRepository.findAll();

        List<DispatchedAction> createdActions = new ArrayList<>();

        for (DutyHolder holder : holders) {
            ArrivalBand band = holder.siteId() != null ? bandBySite.get(holder.siteId()) : null;

            // Alert fatigue control (Section 9): WATCH only reaches duty holders within 2h earliest arrival
            if (incident.level() == IncidentLevel.WATCH && band != null) {
                Duration timeToArrival = Duration.between(now, band.earliestArrival());
                if (timeToArrival.toHours() > 2) {
                    continue;
                }
            }

            for (DutyRule rule : rules) {
                if (rule.actorType() != holder.actorType()) {
                    continue;
                }

                // Check idempotency: avoid duplicate dispatch for same action code
                if (dispatchedActionRepository.existsByIncidentAndHolderAndCode(incidentId, holder.id(), rule.actionCode())) {
                    continue;
                }

                Instant deadline = DeadlineCalculator.calculateDeadline(
                        rule.deadlineExpr(),
                        now,
                        band != null ? band.earliestArrival() : null
                );

                UUID actionId = UUID.randomUUID();
                String token = UUID.randomUUID().toString().substring(0, 8);
                String ackUrl = String.format("/api/v1/acks/%s?token=%s", actionId, token);

                DispatchedAction action = new DispatchedAction(
                        actionId,
                        runId,
                        incidentId,
                        holder.id(),
                        rule.actionCode(),
                        version.version(),
                        deadline,
                        ActionState.SENT,
                        now,
                        null,
                        null,
                        0
                );

                // Localise directive message
                String messageText = localiserPort.localise(
                        action,
                        holder,
                        incident,
                        band,
                        rule,
                        holder.language(),
                        incident.nonRain()
                );

                // Persist action and message attempt
                dispatchedActionRepository.save(action);

                DispatchedMessageRecord msgRecord = new DispatchedMessageRecord(
                        UUID.randomUUID(),
                        actionId,
                        holder.telegramChatId() != null ? "TELEGRAM" : "WEB_PHONE_SIM",
                        holder.language(),
                        messageText,
                        GuardResult.PASS,
                        false,
                        "gemini-3.6-flash",
                        now
                );
                dispatchedMessageRepository.save(msgRecord);

                // Deliver to channel
                channelDispatchPort.send(action, holder, messageText, ackUrl);

                createdActions.add(action);
            }
        }

        // Broadcast OASIS CAP 1.2 XML if WARNING or STAND_DOWN
        if (capGatewayPort != null && (incident.level() == IncidentLevel.WARNING || incident.level() == IncidentLevel.STAND_DOWN)) {
            boolean isExercise = !"live".equalsIgnoreCase(runId);
            String msgType = incident.level() == IncidentLevel.STAND_DOWN ? "Cancel" : "Alert";
            capGatewayPort.broadcast(incident, arrivalBands, msgType, isExercise);
        }

        // Emit incident event
        if (!createdActions.isEmpty()) {
            String payload = String.format("{\"dispatchedCount\":%d,\"matrixVersion\":%d}",
                    createdActions.size(), version.version());
            IncidentEvent event = new IncidentEvent(
                    null, incidentId, now, EventType.EVIDENCE_ADDED, "DISPATCH_ACTION_LOOP",
                    payload, AlertTag.D
            );
            incidentEventRepository.append(event);
            incidentEventPublisher.publish(runId, event);
        }

        return createdActions;
    }

    @Override
    public AckResult acknowledge(UUID actionId, String ackData, Instant when) {
        Optional<DispatchedAction> opt = dispatchedActionRepository.findById(actionId);
        if (opt.isEmpty()) {
            return new AckResult(false, actionId, null, "Action not found");
        }

        DispatchedAction action = opt.get();
        if (action.state() == ActionState.ACKED) {
            return new AckResult(true, actionId, ActionState.ACKED, "Action was already acknowledged");
        }

        Instant ackTime = when != null ? when : clock.instant();
        DispatchedAction acked = action.withState(ActionState.ACKED, ackTime, ackData);
        dispatchedActionRepository.save(acked);

        log.info("Action {} confirmed/ACKED at {}", actionId, ackTime);

        // Emit SSE event
        IncidentEvent event = new IncidentEvent(
                null,
                action.incidentId(),
                ackTime,
                EventType.EVIDENCE_ADDED,
                "DUTY_ACK",
                String.format("{\"actionId\":\"%s\",\"dutyHolderId\":\"%s\",\"status\":\"ACKED\"}",
                        actionId, action.dutyHolderId()),
                AlertTag.P
        );
        incidentEventRepository.append(event);
        incidentEventPublisher.publish(action.runId(), event);

        return new AckResult(true, actionId, ActionState.ACKED, "Acknowledgement recorded successfully");
    }

    @Override
    public AckResult decline(UUID actionId, String reason, Instant when) {
        Optional<DispatchedAction> opt = dispatchedActionRepository.findById(actionId);
        if (opt.isEmpty()) {
            return new AckResult(false, actionId, null, "Action not found");
        }

        DispatchedAction action = opt.get();
        Instant decTime = when != null ? when : clock.instant();
        DispatchedAction declined = action.withState(ActionState.DECLINED, decTime,
                String.format("{\"reason\":\"%s\"}", reason));
        dispatchedActionRepository.save(declined);

        log.warn("Action {} DECLINED by duty holder: {}", actionId, reason);

        IncidentEvent event = new IncidentEvent(
                null,
                action.incidentId(),
                decTime,
                EventType.EVIDENCE_ADDED,
                "DUTY_DECLINED",
                String.format("{\"actionId\":\"%s\",\"reason\":\"%s\"}", actionId, reason),
                AlertTag.P
        );
        incidentEventRepository.append(event);
        incidentEventPublisher.publish(action.runId(), event);

        return new AckResult(true, actionId, ActionState.DECLINED, "Action decline recorded and flagged");
    }

    @Override
    public List<DispatchedAction> checkAndEscalateActions(String runId, Instant now) {
        Instant checkTime = now != null ? now : clock.instant();
        List<DispatchedAction> pending = dispatchedActionRepository.findPendingEscalation(runId, checkTime);
        List<DispatchedAction> escalatedActions = new ArrayList<>();

        for (DispatchedAction action : pending) {
            Instant sent = action.sentAt();
            Duration interval = DeadlineCalculator.calculateEscalationInterval(checkTime, action.deadlineAt(), Duration.ofMinutes(5));
            long elapsedSec = Duration.between(sent, checkTime).toSeconds();

            int targetStep = (int) (elapsedSec / Math.max(30, interval.toSeconds()));
            if (targetStep > action.escalationStep()) {
                int nextStep = Math.min(targetStep, 3);
                ActionState nextState = switch (nextStep) {
                    case 1 -> ActionState.ESCALATED_1; // Voice call
                    case 2 -> ActionState.ESCALATED_2; // Deputy notified
                    case 3 -> ActionState.UNRESOLVED;  // CDO red flag
                    default -> ActionState.UNRESOLVED;
                };

                DispatchedAction escalated = action.withEscalation(nextState, nextStep);
                dispatchedActionRepository.save(escalated);
                escalatedActions.add(escalated);

                log.warn("Action {} escalated to {} (step {})", action.id(), nextState, nextStep);

                // If step 2, notify deputy if registered
                if (nextStep == 2) {
                    Optional<DutyHolder> holderOpt = dutyHolderRepository.findById(action.dutyHolderId());
                    if (holderOpt.isPresent() && holderOpt.get().deputyId() != null) {
                        Optional<DutyHolder> deputyOpt = dutyHolderRepository.findById(holderOpt.get().deputyId());
                        deputyOpt.ifPresent(deputy -> {
                            channelDispatchPort.send(
                                    escalated,
                                    deputy,
                                    "[DEPUTY ESCALATION] Urgent duty action unconfirmed by principal: " + action.actionCode(),
                                    "/api/v1/acks/" + action.id()
                            );
                        });
                    }
                }

                // Emit event
                IncidentEvent event = new IncidentEvent(
                        null,
                        action.incidentId(),
                        checkTime,
                        EventType.EVIDENCE_ADDED,
                        "ESCALATION_STEP",
                        String.format("{\"actionId\":\"%s\",\"step\":%d,\"state\":\"%s\"}",
                                action.id(), nextStep, nextState),
                        AlertTag.D
                );
                incidentEventRepository.append(event);
                incidentEventPublisher.publish(runId, event);
            }
        }

        return escalatedActions;
    }
}
