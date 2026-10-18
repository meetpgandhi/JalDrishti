package org.jaldrishti.application.service;

import org.jaldrishti.application.port.in.AckResult;
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
import org.jaldrishti.domain.ActorType;
import org.jaldrishti.domain.ArrivalTimeCalculator;
import org.jaldrishti.domain.DispatchedAction;
import org.jaldrishti.domain.DutyHolder;
import org.jaldrishti.domain.DutyMatrixVersion;
import org.jaldrishti.domain.DutyRule;
import org.jaldrishti.domain.Incident;
import org.jaldrishti.domain.IncidentLevel;
import org.jaldrishti.domain.Language;
import org.jaldrishti.domain.MatrixStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionDispatcherServiceTest {

    private final Instant baseTime = Instant.parse("2026-08-26T08:53:00Z");
    private final Clock clock = Clock.fixed(baseTime, ZoneOffset.UTC);

    private IncidentRepository incidentRepository;
    private IncidentEventRepository incidentEventRepository;
    private IncidentEventPublisher incidentEventPublisher;
    private DutyMatrixRepository dutyMatrixRepository;
    private DutyHolderRepository dutyHolderRepository;
    private DispatchedActionRepository dispatchedActionRepository;
    private DispatchedMessageRepository dispatchedMessageRepository;
    private GeminiMessageLocaliserPort localiserPort;
    private ChannelDispatchPort channelDispatchPort;
    private CapGatewayPort capGatewayPort;
    private CorridorConfigRepository corridorConfigRepository;

    private ActionDispatcherService dispatcherService;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        incidentEventRepository = mock(IncidentEventRepository.class);
        incidentEventPublisher = mock(IncidentEventPublisher.class);
        dutyMatrixRepository = mock(DutyMatrixRepository.class);
        dutyHolderRepository = mock(DutyHolderRepository.class);
        dispatchedActionRepository = mock(DispatchedActionRepository.class);
        dispatchedMessageRepository = mock(DispatchedMessageRepository.class);
        localiserPort = mock(GeminiMessageLocaliserPort.class);
        channelDispatchPort = mock(ChannelDispatchPort.class);
        capGatewayPort = mock(CapGatewayPort.class);
        corridorConfigRepository = mock(CorridorConfigRepository.class);

        dispatcherService = new ActionDispatcherService(
                clock,
                incidentRepository,
                incidentEventRepository,
                incidentEventPublisher,
                dutyMatrixRepository,
                dutyHolderRepository,
                dispatchedActionRepository,
                dispatchedMessageRepository,
                localiserPort,
                channelDispatchPort,
                capGatewayPort,
                ArrivalTimeCalculator.defaultCorridorCurve(),
                corridorConfigRepository
        );
    }

    @Test
    void dispatchesWarningActionsAndBroadcastsCapXml() {
        UUID incidentId = UUID.randomUUID();
        Incident incident = Incident.createWarning(
                incidentId,
                "s1-run",
                "bhote-trishuli-narayani",
                baseTime,
                "RSW",
                1,
                1,
                true
        );
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));

        DutyMatrixVersion matrixVersion = new DutyMatrixVersion(1, MatrixStatus.APPROVED, "lead", "cdo", baseTime);
        when(dutyMatrixRepository.findLatestApprovedVersion()).thenReturn(Optional.of(matrixVersion));

        DutyRule rule = new DutyRule(
                "WRN-HYD-01",
                1,
                IncidentLevel.WARNING,
                ActorType.HYDRO_CONTROL,
                "ALL",
                "CLOSE_INTAKE",
                "Close headworks intake gates",
                "min(etaEarliest - 15m, now + 10m)",
                "Gates closed",
                true
        );
        when(dutyMatrixRepository.findRulesByVersionAndLevel(1, IncidentLevel.WARNING)).thenReturn(List.of(rule));

        UUID holderId = UUID.randomUUID();
        DutyHolder holder = new DutyHolder(
                holderId,
                ActorType.HYDRO_CONTROL,
                "SYB",
                "Chilime Hydro Control",
                new byte[]{1, 2, 3},
                null,
                Language.ne,
                null
        );
        when(dutyHolderRepository.findAll()).thenReturn(List.of(holder));
        when(dispatchedActionRepository.existsByIncidentAndHolderAndCode(any(), any(), any())).thenReturn(false);
        when(localiserPort.localise(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn("जलदृष्टि चेतावनी: ढोका बन्द गर्नुहोस्");

        List<DispatchedAction> dispatched = dispatcherService.dispatchActionsForIncident("s1-run", incidentId);

        assertThat(dispatched).hasSize(1);
        DispatchedAction action = dispatched.get(0);
        assertThat(action.actionCode()).isEqualTo("CLOSE_INTAKE");
        assertThat(action.dutyHolderId()).isEqualTo(holderId);
        assertThat(action.state()).isEqualTo(ActionState.SENT);

        // Verify channel send was called
        verify(channelDispatchPort, times(1)).send(eq(action), eq(holder), anyString(), anyString());

        // Verify CAP alert broadcast with isExercise = true (since runId != "live")
        verify(capGatewayPort, times(1)).broadcast(eq(incident), any(), eq("Alert"), eq(true));
    }

    @Test
    void acknowledgesActionSuccessfully() {
        UUID actionId = UUID.randomUUID();
        DispatchedAction action = new DispatchedAction(
                actionId,
                "s1-run",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "CLOSE_INTAKE",
                1,
                baseTime.plus(Duration.ofMinutes(10)),
                ActionState.SENT,
                baseTime,
                null,
                null,
                0
        );
        when(dispatchedActionRepository.findById(actionId)).thenReturn(Optional.of(action));

        AckResult result = dispatcherService.acknowledge(actionId, "{\"gatesClosed\":true}", baseTime.plus(Duration.ofMinutes(3)));

        assertThat(result.success()).isTrue();
        assertThat(result.newState()).isEqualTo(ActionState.ACKED);

        ArgumentCaptor<DispatchedAction> captor = ArgumentCaptor.forClass(DispatchedAction.class);
        verify(dispatchedActionRepository).save(captor.capture());
        assertThat(captor.getValue().state()).isEqualTo(ActionState.ACKED);
        assertThat(captor.getValue().ackedAt()).isEqualTo(baseTime.plus(Duration.ofMinutes(3)));
    }

    @Test
    void escalatesUnacknowledgedActionsAlongLadder() {
        UUID actionId = UUID.randomUUID();
        Instant sentTime = baseTime.minus(Duration.ofMinutes(16)); // 16 min elapsed!
        DispatchedAction action = new DispatchedAction(
                actionId,
                "s1-run",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "EVACUATE_CREWS",
                1,
                baseTime.plus(Duration.ofMinutes(20)),
                ActionState.SENT,
                sentTime,
                null,
                null,
                0
        );

        when(dispatchedActionRepository.findPendingEscalation("s1-run", baseTime)).thenReturn(List.of(action));

        List<DispatchedAction> escalated = dispatcherService.checkAndEscalateActions("s1-run", baseTime);

        assertThat(escalated).hasSize(1);
        DispatchedAction updated = escalated.get(0);
        // Step interval is 5m -> 16m elapsed corresponds to step 3 (UNRESOLVED)
        assertThat(updated.escalationStep()).isEqualTo(3);
        assertThat(updated.state()).isEqualTo(ActionState.UNRESOLVED);
    }
}
