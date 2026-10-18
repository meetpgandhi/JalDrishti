# JalDrishti — AI Agent Handoff Document

> **This document is the single most important file for AI agents joining mid-project.**  
> Read this BEFORE touching any code. Update the "Current state" section at the end of every session.

---

## 1. What This Project Is

**JalDrishti v3** is a flash-flood warning-to-action system for the Bhote Koshi–Trishuli–Narayani corridor in Nepal.  
Competition: Google Cloud AI Builder Cup 2026, Sustainability & Social Impact.

**The one-sentence mission:** Issue a corridor WARNING from silent gauges alone (no phone call), and turn every alert level into acknowledged, duty-specific actions.

**The key demo claim:** On the 26 August 2026 flood record, JalDrishti fires a WARNING at 08:53 — 22 minutes before the actual 09:15 SMS — using only the silence of two gauges in river order (Rule R5).

---

## 2. Files You Must Read Before Writing Code

In priority order:

1. **`docs/PROGRESS.md`** — what's done, what's next, known issues
2. **`AGENTS.md`** — architectural hard rules (read section 2 especially)
3. **`.agents/skills/jaldrishti-domain-rules/SKILL.md`** — the 11 trigger rules in full
4. **`.agents/skills/jaldrishti-architecture/SKILL.md`** — module map, API surface, DB schema
5. **`.agents/skills/jaldrishti-sim-engine/SKILL.md`** — SimClock, scenario YAML, S1–S7
6. **`docs/DECISIONS.md`** — every architectural decision and why it was made

The full spec is in:  
`JalDrishti v3 — Flash-Flood Warning-to-Action System Specification & Simulation Plan.md`  
(1095 lines — read Section 6 for rules, Section 13 for architecture, Section 14 for data model)

---

## 3. Hard Constraints — Never Violate These

| # | Rule | Why |
|---|------|-----|
| H1 | `jd-domain` has **zero** Spring/Jackson/database imports | Domain purity; CI checks this on every commit |
| H2 | Adapters depend only on `jd-application`, never on each other | Hexagonal boundary |
| H3 | **Maven only** — never introduce Gradle | Project rule |
| H4 | **Gemini never decides alert levels** | Gemini parses and localises only; R1–R11 decide |
| H5 | **CAP in sim = `status: Exercise`** always | A demo message must never look like a real alert |
| H6 | `incident_event` is **append-only** — never UPDATE or DELETE | Event sourcing |
| H7 | All Gemini calls use **`temperature: 0`** | Determinism |
| H8 | **Numeric guard always runs** after localisation | Safety net |
| H9 | Phone numbers stored **encrypted** in `duty_holder.phone_enc` | Privacy |
| H10 | **No PII in Gemini prompts** — send role + trust level only | Privacy |

---

## 4. Current Codebase State (as of 2026-10-18 · Sprints 0, 1, 2, and 3 Complete)

### What exists

```
JalDrishti/
├── AGENTS.md                              AI agent rules
├── PROGRESS.md  → docs/PROGRESS.md       Master tracker
├── .agents/
│   ├── rules/DEVELOPMENT.md              35 dev rules
│   └── skills/
│       ├── jaldrishti-architecture/SKILL.md
│       ├── jaldrishti-domain-rules/SKILL.md
│       └── jaldrishti-sim-engine/SKILL.md
├── .github/workflows/ci.yml              GitHub Actions CI (3 jobs)
├── .specify/constitution.md              Engineering constitution
├── docker-compose.yml                    Postgres 16 + Pub/Sub emulator
├── Dockerfile                            3-stage minimal container (Node 20 + Maven JDK 21 + JRE 21)
├── cloudbuild.yaml                       Google Cloud Build & Cloud Run (asia-south1)
├── pom.xml                               Parent BOM (Spring Boot 3.4.3)
│
├── jd-domain/                            ← COMPLETE (Pure Java 21, ZERO framework deps)
│   ├── StationState, IncidentLevel, AlertTag, SenderTrust, HazardType, etc.
│   ├── Station, Reading, CorridorConfig, StationObservationState, Site, etc.
│   ├── Rules R1–R11 (R1HeightRule, R2RateRule, R3SilenceRule, R4OutageRule, R5CascadeRule,
│   │   R6NoRainRule, R7TrustedReportRule, R8ObserverRule, R9SeismicRule, R10HeightenedWatchRule, R11AutoCancelRule)
│   ├── ArrivalTimeCalculator (linear interpolation across 26 Aug calibration points)
│   ├── DutyMatrixVersion, DutyRule, DutyHolder, ActionState, DispatchedAction, DeadlineCalculator
│   ├── ReportStatus, GuardResult, FieldReportRecord, DispatchedMessageRecord
│   ├── ReplayReport (FR-17) & IncidentWhyExplanation (FR-15)
│   └── src/test: Unit tests for all rules, jqwik property tests, and DeadlineCalculatorTest
│
├── jd-application/                       ← COMPLETE (Use-cases, ports, Action Loop)
│   ├── port.in: IngestReadingUseCase, RunSilenceSweepUseCase, IngestFieldReportUseCase, ForecasterActionUseCase,
│   │   DispatchIncidentActionsUseCase, AcknowledgeActionUseCase, EscalateActionsUseCase, ProcessFieldReportUseCase,
│   │   GenerateReplayReportUseCase (FR-17)
│   ├── port.out: ReadingRepository, StationStateRepository, IncidentRepository, IncidentEventRepository,
│   │   CorridorConfigRepository, IncidentEventPublisher, DutyMatrixRepository, DutyHolderRepository,
│   │   DispatchedActionRepository, DispatchedMessageRepository, FieldReportRepository, CapMessageRepository,
│   │   GeminiFieldReportParserPort, GeminiMessageLocaliserPort, ChannelDispatchPort, CapGatewayPort
│   ├── service: TriggerEngine, ActionDispatcherService, FieldReportProcessor, ReplayReportService
│   └── src/test: TriggerEngineTest, ActionDispatcherServiceTest
│
├── jd-adapter-pubsub/                    ← COMPLETE
│   └── GaugeReadingPushPayload, GaugeReadingConsumer
│
├── jd-adapter-rest/                      ← COMPLETE
│   ├── CorridorStateController, SseStreamController (/api/v1/stream), InternalSweepController,
│   ├── SimRunController (/api/v1/sim/runs, /inject [FR-16], /report [FR-17])
│   ├── IncidentActionController (/api/v1/incidents/{id}/actions, /escalate, /cancel-watch, /stand-down, /why [FR-15])
│   └── IncidentAssistantController (/api/v1/assistant/ask [FR-18] with Gemini 3.6 Flash & telemetry grounding)
│
├── jd-adapter-persistence/               ← COMPLETE
│   ├── JooqReadingRepository, JooqStationStateRepository, JooqIncidentRepository, JooqIncidentEventRepository,
│   │   JooqDutyMatrixRepository, JooqDutyHolderRepository, JooqDispatchedActionRepository,
│   │   JooqDispatchedMessageRepository, JooqFieldReportRepository, JooqCapMessageRepository, JooqCorridorConfigRepository
│   ├── PersistenceUtils
│   └── src/main/resources/db/migration/
│       ├── V1__baseline_schema.sql (all 15 tables)
│       └── V2__seed_duty_matrix_and_holders.sql (version 1 approved matrix & 6 corridor duty holders)
│
├── jd-adapter-gemini/                    ← COMPLETE (Vertex AI / Gemini)
│   ├── NumericGuard (Devanagari digit conversion ०..९ to 0..9 and strict payload token assertion)
│   ├── ReviewedTemplateEngine (messages.properties safety net for EN, NE, HI with NON_RAIN addendum)
│   ├── GeminiMessageLocaliser (temperature 0, guarded by NumericGuard, automatic template fallback)
│   ├── GeminiFieldReportParser (temperature 0, structured JSON schema, offline gazetteer heuristics)
│   └── src/test: NumericGuardTest, GeminiFieldReportParserTest
│
├── jd-adapter-channels/                  ← COMPLETE (Phone sim, Telegram, templates)
│   ├── ChannelDispatcher (ChannelDispatchPort implementation, in-memory inboxes)
│   ├── PhoneSimulatorController (/api/v1/phone/duty-holders, /inbox, /ack, /decline, /report)
│   ├── AckController (/api/v1/acks/{actionId} for signed 24h HMAC links)
│   └── TelegramWebhookController (/api/v1/webhooks/telegram)
│
├── jd-adapter-cap/                       ← COMPLETE (OASIS CAP 1.2 XML)
│   ├── Cap12Builder (Exercise status in sim, Actual in live)
│   ├── CapGatewayAdapter (CapGatewayPort implementation)
│   └── src/test: Cap12BuilderTest
│
├── jd-sim/                               ← COMPLETE
│   ├── SimClock (injectable Clock with pause, resume, speed multiplier)
│   ├── ScenarioLoader, ScenarioValidator, ReadingGenerators (flat, hydrograph)
│   ├── SimScheduler (silence sweep + escalation sweeps every simulated minute)
│   └── src/main/resources/corridor/bhote-trishuli-narayani.yaml
│
├── jd-boot/                              ← COMPLETE
│   ├── JalDrishtiApplication.java
│   ├── JalDrishtiWiringConfig.java (wires sim/live profiles, SimClock, TriggerEngine, ActionDispatcherService, ReplayReportService, Gemini)
│   ├── application.yml, application-sim.yml, application-live.yml
│   └── src/test: JalDrishtiApplicationTests.java (smoke test with mocked db)
│
├── scenarios/                            ← ALL 7 SCENARIOS
│   ├── aug26-as-happened.yaml           S1 ← PRIMARY demo scenario
│   ├── aug26-no-phone-call.yaml         S2
│   ├── gauge-power-failure.yaml         S3
│   ├── telemetry-outage.yaml            S4
│   ├── monsoon-rise.yaml                S5
│   ├── second-wave.yaml                 S6
│   ├── sandbox.yaml                     S7
│   └── golden/s1-events.jsonl
│
└── console/                             ← COMPLETE (Vue 3 + TypeScript + Pinia)
    ├── package.json, vite.config.ts, tsconfig.json, env.d.ts
    ├── src/composables/useSSE.ts (SSE client with Last-Event-ID)
    ├── src/stores/stationStore.ts (reactive telemetry & incident store)
    ├── src/components/StationTile.vue, StationStrip.vue
    ├── src/components/AckBoard.vue (live duty acknowledgement board with filters & countdowns)
    ├── src/components/ReportFeed.vue (field reports with Gemini parse cards & live test buttons)
    ├── src/components/ComparisonTimeline.vue (22-minute saved timeline proof point)
    ├── src/components/WhyModal.vue (physical order-consistency derivation audit)
    ├── src/components/ReplayReportModal.vue (deterministic audit report & JSON download)
    ├── src/components/LiveInjectionModal.vue (real-time telemetry & hazard stress-testing)
    ├── src/components/CopilotChat.vue (interactive Gemini 3.6 Flash conversational copilot)
    ├── src/views/PhoneSimulatorView.vue (interactive mobile duty-holder app with 1-click ack)
    ├── src/views/ConsoleView.vue (integrated operator dashboard with all modals & copilot)
    └── Verified: npm run build outputs cleanly to jd-boot/src/main/resources/static/
```

### Next Up: Finale Prep & Demonstration
- 3-minute video recording rehearsal (S1 scenario run with silent gauge warning + phone sim ack)
- Submission deployment to Google Cloud Run via `gcloud builds submit --config=cloudbuild.yaml`
- Final pre-demo checklist verification (Section 12)

---

## 5. Packages to Use

```
org.jaldrishti.domain.*           jd-domain
org.jaldrishti.application.*      jd-application
  .application.port.in.*          inbound ports (use-case interfaces)
  .application.port.out.*         outbound ports (repository interfaces)
  .application.service.*          use-case implementations
org.jaldrishti.adapter.pubsub.*   jd-adapter-pubsub
org.jaldrishti.adapter.rest.*     jd-adapter-rest
org.jaldrishti.adapter.persistence.* jd-adapter-persistence
org.jaldrishti.adapter.gemini.*   jd-adapter-gemini
org.jaldrishti.adapter.channels.* jd-adapter-channels
org.jaldrishti.adapter.cap.*      jd-adapter-cap
org.jaldrishti.sim.*              jd-sim
org.jaldrishti.boot.*             jd-boot
```

---

## 6. The Most Critical Rule to Implement First: R5

R5 is the heart of the demo. Get this exactly right.

```
Station i goes SILENT between t_lastGood_i and t_firstMissed_i.
Station j is downstream of i by distance D_ij.

Order-consistency window for j:
  t_earliest_j = t_lastGood_i  + D_ij / 35   (v_max = 35 m/s)
  t_latest_j   = t_firstMissed_i + D_ij / 3  (v_min = 3 m/s)

R5 fires → WARNING when:
  j's evidence interval overlaps [t_earliest_j, t_latest_j]
  
  For SILENT j:   evidence = j's silence interval
  For RATE_ALERT: evidence = the reading timestamp

26 Aug worked example (must pass in unit test):
  RSW: lastGood=08:40, firstMissed=08:50, D=15km to SYP
  t_earliest_SYP = 08:40 + 15000/35s = 08:47:08
  t_latest_SYP   = 08:50 + 15000/3s  = 10:13:20
  SYP loss interval = (08:40, 08:50] → overlaps [08:47, 10:13] → FIRES
  At the 08:53 sweep → WARNING [D]
```

---

## 7. Key Ports and Interfaces to Define (Sprint 1)

### Inbound ports (in jd-application)
```java
// Called by jd-adapter-pubsub when a gauge reading arrives
interface IngestReadingUseCase {
    void ingest(ReadingCommand command);
}

// Called by Cloud Scheduler / SimScheduler every minute
interface RunSilenceSweepUseCase {
    SweepResult sweep(String runId, Instant now);
}

// Called by jd-adapter-pubsub when a field report arrives
interface IngestFieldReportUseCase {
    void ingest(FieldReportCommand command);
}
```

### Outbound ports (in jd-application)
```java
interface ReadingRepository {
    void save(Reading reading);           // ON CONFLICT DO NOTHING
    List<Reading> findRecent(String runId, String stationId, Instant since);
}

interface StationStateRepository {
    Optional<StationState> find(String runId, String stationId);
    void save(StationState state);
}

interface IncidentEventRepository {
    void append(IncidentEvent event);     // NEVER UPDATE
    List<IncidentEvent> findByIncident(UUID incidentId);
}
```

---

## 8. Sim Profile Behaviour

When `spring.profiles.active=sim`:
- A `SimClock` bean (implements `java.time.Clock`) is primary
- All domain code calls `clock.instant()` — never `Instant.now()` directly
- `SimScheduler` calls `/internal/sweep` once per simulated minute
- Pub/Sub emulator is at `localhost:8085` (set via `PUBSUB_EMULATOR_HOST`)
- CAP messages get `status: Exercise` (enforced in `jd-adapter-cap`)
- `runId` is a UUID; live mode uses the literal string `"live"`

---

## 9. Test Strategy Summary

| Test type | Tool | Location | Must pass |
|-----------|------|----------|-----------|
| Domain unit | JUnit 5 + AssertJ | `jd-domain/src/test` | Every commit |
| R5 property-based | jqwik | `jd-domain/src/test` | Every commit |
| Scenario S1 | Scenario runner | `jd-sim/src/test` | Every commit |
| Scenario S3 | Scenario runner | `jd-sim/src/test` | Every commit |
| Golden replay diff | Diff against `scenarios/golden/s1-events.jsonl` | CI | Every commit |
| Integration (Postgres) | Testcontainers | `jd-adapter-persistence/src/test` | Every merge |
| Boot smoke | `@SpringBootTest` + `@ActiveProfiles("sim")` | `jd-boot/src/test` | Every commit |

---

## 10. Current Blockers

1. **Java 21 not installed** in the agent shell — `mvn` commands will fail with "Unable to locate a Java Runtime"
   - Fix: `brew install --cask temurin@21` or use SDKMAN
   
2. **`JalDrishtiApplicationTests` will fail** until Spring beans exist
   - Expected for Sprint 0; add `@MockBean` or `@Conditional` stubs in Sprint 1 as beans are added

3. **jOOQ code generation not configured** — `jd-adapter-persistence` has the schema but no Maven plugin setup yet
   - Add `jooq-codegen-maven` plugin to `jd-adapter-persistence/pom.xml` in Sprint 1

---

## 11. How to Start a New Session

1. Read `docs/PROGRESS.md` — find current sprint and first unchecked item
2. Read `docs/HANDOFF.md` (this file) — understand current codebase state  
3. Check `docs/DECISIONS.md` for any relevant architectural decisions
4. Look at `docs/SPRINT_LOG.md` for what happened in the last session
5. Start implementing the first unchecked item from the sprint checklist
6. At session end: update `docs/PROGRESS.md` and `docs/SPRINT_LOG.md`
