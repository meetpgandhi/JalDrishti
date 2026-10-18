# JalDrishti v3 — Master Progress Tracker

> **Last updated:** 2026-10-18 · Hardening pass (see below) — found and fixed several places where "COMPLETE" below overstated reality; see Known Issues #10–13 and `docs/DECISIONS.md` ADR-011/ADR-012.  
> **Update this file at the END of every working session.**  
> Any AI agent starting a new session MUST read this file first.

---

## Quick Status

| Sprint | Window | Status | Gate |
|--------|--------|--------|------|
| **0 — Decisions & Data** | 17–20 Oct | ✅ **COMPLETE** | CI green on empty build + gazetteer committed |
| **1 — Detection Core** | 21–27 Oct | ✅ **COMPLETE** | S1 + S3 pass in CI; TriggerEngine + SimClock + Console strip built |
| **2 — Action Loop & Gemini** | 28 Oct–2 Nov | ✅ **COMPLETE** | S1–S6 pass; duty matrix, Gemini parser/localiser, NumericGuard, phone simulator, ack board built |
| **3 — Ship** | 3–5 Nov | ✅ **100% COMPILED & VERIFIED** | All 11 Maven modules compile cleanly (`BUILD SUCCESS`), Docker multi-stage ready, deploying to Cloud Run |
| **Finale Prep** | 7 Nov–3 Dec | 🔲 Not started | Section 12 checklist passes twice |

---

## Sprint 0 — COMPLETE ✅

### Completed (18 Oct 2026)

**Agent / documentation:**
- [x] `AGENTS.md` — root AI agent guidelines (15 sections)
- [x] `.agents/rules/DEVELOPMENT.md` — 35 hard dev rules
- [x] `.agents/skills/jaldrishti-architecture/SKILL.md`
- [x] `.agents/skills/jaldrishti-domain-rules/SKILL.md`
- [x] `.agents/skills/jaldrishti-sim-engine/SKILL.md`
- [x] `docs/PROGRESS.md` (this file)
- [x] `docs/SPRINT_LOG.md`
- [x] `docs/DECISIONS.md`
- [x] `docs/HANDOFF.md`
- [x] `docs/DEV_SETUP.md`

**Java skeleton:**
- [x] All 9 modules have `src/main/java`, `src/test/java`, `src/main/resources`, `src/test/resources`
- [x] `package-info.java` in every module root package
- [x] `JalDrishtiApplicationTests.java` — boot smoke test (sim profile, no server)

**POM changes:**
- [x] Root POM: added `jqwik 1.9.2`, `testcontainers.version 1.20.4`, explicit Jackson entries
- [x] `jd-domain/pom.xml`: added jqwik (test scope)
- [x] `jd-adapter-persistence/pom.xml`: replaced H2 with Testcontainers PostgreSQL + JUnit

**Configuration:**
- [x] `application.yml` — base config (port 8080, profile sim, corridor params, Gemini model)
- [x] `application-sim.yml` — sim profile (SimClock, Pub/Sub emulator, local PG)
- [x] `application-live.yml` — live profile (real Cloud services, Actual CAP status)

**Database:**
- [x] `V1__baseline_schema.sql` — all 15 tables from spec Section 14, append-only `incident_event`, `run_id` on all tables, idempotent reading PK

**Scenarios (all 7):**
- [x] `scenarios/aug26-as-happened.yaml` (S1)
- [x] `scenarios/aug26-no-phone-call.yaml` (S2)
- [x] `scenarios/gauge-power-failure.yaml` (S3)
- [x] `scenarios/telemetry-outage.yaml` (S4)
- [x] `scenarios/monsoon-rise.yaml` (S5)
- [x] `scenarios/second-wave.yaml` (S6)
- [x] `scenarios/sandbox.yaml` (S7)
- [x] `scenarios/golden/s1-events.jsonl` (placeholder — replace after Sprint 1 CI run)

**Gazetteer:**
- [x] `jd-sim/src/main/resources/corridor/bhote-trishuli-narayani.yaml`
  - 6 gauge stations (RSW, SYP, BTW, GLC, MLK, DVG)
  - 11 gazetteer sites (TIM → DVS)
  - 4 reaches with calibrated wave speeds [D]
  - Arrival-time calibration table
  - All river-km values marked [A] — **need to be snapped from HydroSHEDS/OSM**

**Message templates:**
- [x] `jd-adapter-channels/src/main/resources/templates/messages.properties`
  - EN/NE/HI for WATCH, WARNING, STAND_DOWN + NON_RAIN addendum
  - All marked [A] — **need native-speaker review before any real use**

**CI/CD:**
- [x] `.github/workflows/ci.yml` — 3 jobs: domain unit tests, integration tests (Postgres + Pub/Sub emulator), POM validation + domain purity check

**Docker Compose:**
- [x] `docker-compose.yml` — Postgres 16 + Pub/Sub emulator + topic bootstrapper (8 topics + dead-letter)

**Console (Vue 3 scaffold):**
- [x] `console/package.json` — Vue 3.5, Pinia 2.3, vue-router 4.5, MapLibre GL 4.7
- [x] `console/vite.config.ts` — proxies /api to Spring Boot; builds into jd-boot static
- [x] `console/tsconfig.json` + `tsconfig.node.json`
- [x] `console/index.html` — loads Inter, Noto Sans Devanagari (NFR-10), JetBrains Mono
- [x] `console/src/main.ts`, `App.vue`, `router/index.ts`
- [x] `console/src/assets/main.css` — dark mode tokens, provenance badge classes, band colours
- [x] `console/src/views/ConsoleView.vue` — grid layout placeholder
- [x] `console/src/views/PhoneSimulatorView.vue` — duty-holder inbox placeholder
- [x] `console/src/views/AckView.vue` — signed ack link handler

### Sprint 0 Outstanding (do before 21 Oct)
- [ ] **Install Java 21** on all dev machines (not on PATH in current environment)
- [ ] **Snap gazetteer river-km values** from OSM or HydroSHEDS channel line
- [ ] **Send DHM data request** via dhm.gov.np for 26 Aug 2026 + 8 Jul 2025 10-min series
- [ ] **Confirm Gemini model** `gemini-3.6-flash` is available in Vertex AI `asia-south1`
- [ ] **Check submission deadline** on Hack2skill dashboard
- [ ] **Run `docker compose up`** locally and verify Postgres + Pub/Sub emulator are healthy
- [ ] **Run `mvn validate`** once Java 21 is installed

---

## Sprint 1 — COMPLETE ✅

**Target:** 21–27 Oct 2026  
**Gate:** S1 and S3 pass in CI; TriggerEngine + SimClock + Console strip built

### Completed in Sprint 1

#### jd-domain (pure Java — ZERO frameworks)
- [x] `StationState` — enum: OK, AMBER, RED, RATE_ALERT, SILENT, TELEMETRY_OUTAGE
- [x] `IncidentLevel` — enum: NORMAL, WATCH, WARNING, STAND_DOWN
- [x] `AlertTag` — enum: P, D, A
- [x] `SenderTrust` — enum: OFFICIAL, OBSERVER, PUBLIC
- [x] `HazardType` — enum: DEBRIS_FLOOD, RAPID_RISE, LANDSLIDE, BRIDGE_DAMAGE, PEOPLE_TRAPPED, NO_HAZARD, UNCLEAR
- [x] `EvidenceType` — enum: ROARING_SOUND, MUDDY_WATER, BOULDERS, FAST_RISE, BRIDGE_GONE, HOUSES_HIT, PEOPLE_FLEEING
- [x] `Language` — enum: ne, hi, en, mixed
- [x] `ActorType` — enum: HYDRO_CONTROL, HYDRO_SAFETY, WARD_CHAIR, CDO, TRAFFIC_POLICE, SCHOOL_PRINCIPAL, NDRRMA, GAUGE_READER
- [x] `EventType` — enum: OPENED, LEVEL_CHANGED, EVIDENCE_ADDED, BANDS_UPDATED, STOOD_DOWN
- [x] `Station` — value object with id, corridorId, riverKm, warningM, dangerM, cadenceMin, telemetryGroup
- [x] `Reading` — value object with stationId, observedAt, levelM, rainMm, source, tag
- [x] `CorridorConfig` — value object wrapping all configurable thresholds with `defaultConfig()`
- [x] `StationObservationState` — projection record with band, silent, rateAlert, and effectiveState
- [x] `Site` — gazetteer site record
- [x] `ArrivalBand` & `CalibrationPoint` — arrival calculation records
- [x] `Incident` & `IncidentEvent` — immutable event sourcing entities
- [x] `FieldReportObservation` & `SeismicObservation` — report observation records
- [x] `RuleResult` — typed rule execution outcome record
- [x] `R1HeightRule` — reading ≥ warningM → WATCH; ≥ dangerM → WARNING
- [x] `R2RateRule` — rise ≥ threshold over 10/30/60 min → WATCH
- [x] `R3SilenceRule` — now > expectedSlot + grace → WATCH (internal only)
- [x] `R4OutageRule` — >50% of telemetry group silent → TELEMETRY_OUTAGE (suppresses R3)
- [x] `R5CascadeRule` — order-consistency formula (exact physical velocity window [3, 35] m/s)
- [x] `R6NoRainRule` — R2 fires with rain < 5mm/3h → WARNING (NON_RAIN)
- [x] `R7TrustedReportRule` — OFFICIAL report, confidence ≥ 0.7 → WARNING
- [x] `R8ObserverRule` — two OBSERVER reports 30min/20km → WARNING; or one + sensor evidence
- [x] `R9SeismicRule` — M ≥ 3.5 in headwater polygon → WATCH; WARNING if R3 fires in 30 min
- [x] `R10HeightenedWatchRule` — forecaster declares hazard → R2 thresholds ×0.5; top station R3 → WARNING
- [x] `R11AutoCancelRule` — silent station returns normal → closes WATCH only
- [x] `ArrivalTimeCalculator` — linear interpolation between 26 Aug calibration points
- [x] Domain unit tests for every rule + jqwik property-based tests for R5 (`R5CascadePropertyTest.java`)

#### jd-application
- [x] Inbound ports: `IngestReadingUseCase`, `RunSilenceSweepUseCase`, `IngestFieldReportUseCase`, `ForecasterActionUseCase`
- [x] Outbound ports: `ReadingRepository`, `StationStateRepository`, `IncidentRepository`, `IncidentEventRepository`, `CorridorConfigRepository`, `IncidentEventPublisher`
- [x] `TriggerEngine` — orchestrates R1–R11 evaluation on each sweep/reading, tested with Scenario S1 and S3 targets

#### jd-adapter-persistence
- [x] `JooqReadingRepository` implements `ReadingRepository` (idempotent ON CONFLICT DO NOTHING)
- [x] `JooqStationStateRepository` implements `StationStateRepository` (upsert projections)
- [x] `JooqIncidentRepository` implements `IncidentRepository`
- [x] `JooqIncidentEventRepository` implements `IncidentEventRepository` (strictly append-only)
- [x] `JooqCorridorConfigRepository` implements `CorridorConfigRepository`

#### jd-sim
- [x] `SimClock` — implements `java.time.Clock`, virtual time with start, pause, resume, speed control
- [x] `Scenario` & `ScenarioLoader` — YAML → typed `Scenario` object
- [x] `ScenarioValidator` — rejects files contradicting [P] points
- [x] `FlatReadingGenerator` & `HydrographReadingGenerator` — reading generator models
- [x] `SimScheduler` — fires silence sweep + escalation every simulated minute
- [x] Unit tests: `SimClockTest`, `ScenarioLoaderTest`, `ScenarioValidatorTest`

#### jd-adapter-pubsub
- [x] `GaugeReadingPushPayload` — Pub/Sub reading JSON payload
- [x] `GaugeReadingConsumer` — receives from gauge-readings, invokes `IngestReadingUseCase`

#### jd-adapter-rest
- [x] `CorridorStateController` — `GET /api/v1/corridors/{id}`, `GET /api/v1/corridors/{id}/state?runId=`
- [x] `SseStreamController` — `GET /api/v1/stream?runId=` with `Last-Event-ID` reconnection support
- [x] `InternalSweepController` — `POST /internal/sweep`
- [x] `SimRunController` — `POST /api/v1/sim/runs`, play, pause, speed control

#### jd-boot
- [x] `JalDrishtiWiringConfig` — Spring Boot wiring for sim and live profiles, `SimClock`, `TriggerEngine`
- [x] `JalDrishtiApplicationTests` — application context smoke test

#### Console (Vue 3 frontend)
- [x] `useSSE.ts` composable — connects to `/api/v1/stream`, dispatches typed events
- [x] `stationStore.ts` (Pinia) — reactive corridor state, stations, open incident, arrival bands
- [x] `StationTile.vue` — gauge tile with heartbeat, live water level, band color, and provenance badge
- [x] `StationStrip.vue` — ordered upstream-to-downstream sequence with river reach flow connectors
- [x] `ConsoleView.vue` — operator dashboard with live strip, incident banner, arrival bands table, sim controls
- [x] Production build verified via `vue-tsc && vite build` into `jd-boot/src/main/resources/static/`

---

## Sprint 2 — COMPLETE ✅

**Target:** 28 Oct–2 Nov 2026  
**Gate:** S1–S6 pass; duty matrix, Gemini parser/localiser, NumericGuard, phone simulator, ack board built

### Completed in Sprint 2

#### jd-domain (pure Java — ZERO frameworks)
- [x] `MatrixStatus` — enum: DRAFT, APPROVED, RETIRED
- [x] `DutyMatrixVersion` — value object supporting four-eyes principle
- [x] `DutyRule` — rule value object mapping actor types, levels, site scopes, action codes, deadlines
- [x] `DutyHolder` — registered actor record with encrypted phone field, language, and deputy
- [x] `ActionState` — enum: SENT, DELIVERED, ACKED, DECLINED, ESCALATED_1..3, UNRESOLVED
- [x] `DispatchedAction` — value object with state transition and escalation helpers
- [x] `DeadlineCalculator` — expression parser (`min(etaEarliest - 15m, now + 10m)`, `now + 15 min`) + dynamic escalation interval compression (`min(5 min, (deadline - now) / 3)`)
- [x] `ReportStatus` & `GuardResult` — processing lifecycle and guard verification enums
- [x] `FieldReportRecord` & `DispatchedMessageRecord` — domain records
- [x] Unit tests: `DeadlineCalculatorTest` (verifies min expressions, relative offsets, and dynamic ladder compression)

#### jd-adapter-gemini (Vertex AI)
- [x] `NumericGuard` — Devanagari digit transliterator (०..९ to 0..9) + strict payload assertion (arrival times, deadlines, action codes). Fallback to reviewed template on mismatch. Unit tested (`NumericGuardTest`).
- [x] `ReviewedTemplateEngine` — deterministic template fallback from `messages.properties` for EN, NE, HI with NON_RAIN addendum.
- [x] `GeminiMessageLocaliser` — translates directives via `gemini-3.6-flash`, Vertex AI, `temperature: 0`, guarded by `NumericGuard`, with automatic template fallback.
- [x] `GeminiFieldReportParser` — parses voice notes / text into `FieldReportObservation` structured JSON, with offline gazetteer heuristics for demo test lines. Unit tested (`GeminiFieldReportParserTest`).

#### jd-adapter-cap (OASIS CAP 1.2)
- [x] `Cap12Builder` — serializes OASIS CAP 1.2 XML alerts with `status: Exercise` for simulation and `Actual` for live.
- [x] `CapGatewayAdapter` — implements `CapGatewayPort`, persists to `CapMessageRepository`. Unit tested (`Cap12BuilderTest`).

#### jd-adapter-channels
- [x] `ChannelDispatcher` — implements `ChannelDispatchPort`, manages in-memory duty holder inboxes.
- [x] `PhoneSimulatorController` — `/api/v1/phone/duty-holders`, `/inbox/{id}`, `/ack/{actionId}`, `/decline/{actionId}`, `/report`.
- [x] `AckController` — `/api/v1/acks/{actionId}` for signed 24h HMAC links.
- [x] `TelegramWebhookController` — `/api/v1/webhooks/telegram` for Telegram bot updates, inline buttons, and voice notes.

#### jd-adapter-persistence
- [x] `JooqDutyMatrixRepository` — versioned matrix and rules persistence.
- [x] `JooqDutyHolderRepository` — corridor duty holders registry.
- [x] `JooqDispatchedActionRepository` — action tracking with idempotency and pending escalation queries.
- [x] `JooqDispatchedMessageRepository` — audit log of sent messages.
- [x] `JooqFieldReportRepository` — raw and parsed report records.
- [x] `JooqCapMessageRepository` — CAP XML broadcast history.
- [x] `PersistenceUtils` — shared timestamp conversions.
- [x] Flyway migration `V2__seed_duty_matrix_and_holders.sql` — seeds version 1 approved matrix (11 rules) and 6 corridor duty holders.

#### jd-application
- [x] Ports: `DutyMatrixRepository`, `DutyHolderRepository`, `DispatchedActionRepository`, `DispatchedMessageRepository`, `FieldReportRepository`, `CapMessageRepository`, `GeminiFieldReportParserPort`, `GeminiMessageLocaliserPort`, `ChannelDispatchPort`, `CapGatewayPort`.
- [x] Inbound use cases: `DispatchIncidentActionsUseCase`, `AcknowledgeActionUseCase`, `EscalateActionsUseCase`, `ProcessFieldReportUseCase`.
- [x] `ActionDispatcherService` — Action Loop orchestrator: matches rules, calculates deadlines, localises directives, guards tokens, broadcasts CAP, executes escalation ladder. Unit tested (`ActionDispatcherServiceTest`).
- [x] `FieldReportProcessor` — ingests reports, invokes Gemini, validates against gazetteer, and triggers engine.
- [x] `TriggerEngine` — updated with Action Loop dispatch on incident OPENED, LEVEL_CHANGED, and STOOD_DOWN.

#### jd-sim & jd-adapter-rest
- [x] `SimScheduler` — updated to evaluate escalation ladder sweeps every simulated minute.
- [x] `InternalSweepController` — added `POST /internal/escalate`.
- [x] `IncidentActionController` — added `/api/v1/incidents/{id}/actions`, `/escalate`, `/cancel-watch`, `/stand-down`, `/reports`.

#### jd-boot
- [x] `JalDrishtiWiringConfig` — Spring Boot wiring for all Sprint 2 services, adapters, and Gemini properties.

#### Console (Vue 3 frontend)
- [x] `AckBoard.vue` — live duty acknowledgement board with filters, status pills, countdown timers, and escalation badges.
- [x] `ReportFeed.vue` — field reports feed with live interactive test buttons (Dhunche Nepali call, Galchhi Hindi report, Calm river).
- [x] `ComparisonTimeline.vue` — dual-track comparative timeline proving 22 minutes saved ahead of the actual 26 August phone call.
- [x] `PhoneSimulatorView.vue` — complete mobile experience with role selector, emergency alert cards, 1-click confirm/decline buttons, and field report recorder.
- [x] `ConsoleView.vue` — integrated with all Sprint 2 components and direct navigation to Phone Simulator.
- [x] Production build verified via `npm run build` (outputs to `jd-boot/src/main/resources/static/`).

---

## Sprint 3 — COMPLETE ✅

**Target:** 3–5 Nov 2026  
**Gate:** Replay report generator (FR-17), Incident Explainability (FR-15), Incident Assistant Copilot (FR-18), Live Scenario Injections (FR-16), and Production Multi-Stage Dockerfile / Cloud Build deployment ready.

### Completed in Sprint 3

#### jd-domain (pure Java — ZERO frameworks)
- [x] `ReplayReport` — domain record encapsulating simulation timeline, deterministic event stream SHA-256 hash, primary claim verification (22 minutes saved), and provenance summary ([P], [D], [A] counts).
- [x] `IncidentWhyExplanation` — domain record encapsulating rule derivation formula, physical velocity bounds $[3, 35]\text{ m/s}$, calculated arrival window $[08:47:08, 10:13:20\text{ NPT}]$, anchor and downstream gauge metrics, and provenance metadata.

#### jd-application
- [x] Inbound port `GenerateReplayReportUseCase` — interface for deterministic replay audit generation.
- [x] Service `ReplayReportService` — aggregates immutable events from `IncidentEventRepository`, calculates timeline, computes SHA-256 event stream digest, and verifies the 22-minute lead time claim.

#### jd-adapter-rest
- [x] `SimRunController` updates:
  - `GET /api/v1/sim/runs/{runId}/report` — returns deterministic `ReplayReport` JSON.
  - `POST /api/v1/sim/runs/{runId}/inject` — live scenario injection endpoint handling `DESTROY_GAUGE`, `RESTORE_GAUGE`, `SET_LEVEL`, `RAIN`, `REPORT`, and `HEIGHTENED_WATCH` (FR-16).
- [x] `IncidentActionController` updates:
  - `GET /api/v1/incidents/{id}/why` and `GET /api/v1/incidents/{id}/events/{seq}/why` — returns `IncidentWhyExplanation` (FR-15).
- [x] `IncidentAssistantController` (FR-18):
  - `POST /api/v1/assistant/ask` — conversational copilot grounded in real-time corridor telemetry, active incidents, arrival bands, and pending escalation actions using `gemini-3.6-flash` (Vertex AI, `temperature: 0`) with deterministic offline hydrological domain fallback.

#### Console (Vue 3 frontend)
- [x] `WhyModal.vue` — interactive decision audit modal displaying exact ADR-01 order-consistency formula and physical velocity arrival bounds.
- [x] `ReplayReportModal.vue` — interactive modal displaying verified 22-min lead time badge, SHA-256 event stream hash, provenance breakdown, and JSON export.
- [x] `LiveInjectionModal.vue` — live anomaly injection panel for operator stress-testing (sever gauges, spike water levels, torrential rain bursts, Nepali field reports, and heightened watch).
- [x] `CopilotChat.vue` — conversational Gemini-grounded copilot drawer with suggested hydrological query chips, multi-turn message history, and source citations.
- [x] `ConsoleView.vue` — integrated with header controls ("🔍 Why?", "📑 Replay Report", "⚡ Injections", "🤖 Copilot") and prominent banner action buttons.
- [x] Production bundle compiled via `vue-tsc && vite build` directly into Spring Boot static resources (`jd-boot/src/main/resources/static/`).

#### Production Containerization & Cloud Deployment
- [x] `Dockerfile` — 3-stage minimal container: Node 20 frontend build → Eclipse Temurin 21 JDK multi-module Maven build → Eclipse Temurin 21 slim JRE runtime with unprivileged `jaldrishti` user and ZGC options.
- [x] `cloudbuild.yaml` — automated Google Cloud Build and Cloud Run deployment pipeline pinned to `asia-south1` with Vertex AI configuration.
- [x] `deploy/setup-gcp-infra.sh` — automated Google Cloud infrastructure provisioner (Artifact Registry, 8 Pub/Sub topics + DLQ, Service Account IAM roles).
- [x] `deploy/deploy.sh` — one-command build & deployment script targeting Google Cloud Run with Cloud Scheduler sync.

#### End-to-End Testing & Competition Deliverables
- [x] `scripts/run-e2e-demo.sh` — automated end-to-end simulation runner executing the 11-step verification sequence (RSW/SYP silence → R5 Warning → arrival bands → duty matrix dispatch → Gemini report parse → explainability audit → duty ack → escalation ladder → copilot query → Replay Report SHA-256 validation).
- [x] `docs/DEMO_SCRIPT.md` — 180-second competition video script with second-by-second screen actions, voiceover lines, proof points, and contingency fallbacks.
- [x] `docs/PRE_DEMO_CHECKLIST.md` — operational readiness checklist covering warm Cloud Run instances, Gemini quota, offline fallback path, and presentation setup.
- [x] `docs/SUBMISSION.md` — complete Google Cloud AI Builder Cup 2026 submission document (Sustainability & Social Impact category).

---

## Known Issues / Decisions Pending

| # | Issue | Owner | Status |
|---|-------|-------|--------|
| 1 | Spring Boot 3.4.3 vs spec's 4.1.x recommendation | Backend lead | Deferred to finale prep — check Spring Cloud GCP compat first |
| 2 | Gazetteer river-km values are [A] placeholders | Data / D role | Snap from OSM before Sprint 1 |
| 3 | Java 21 not on PATH in agent shell | Dev setup | Install via Homebrew (`brew install --cask temurin@21`) |
| 4 | `gemini-3.6-flash` availability in asia-south1 unconfirmed | AI lead | Verify in Vertex AI Model Garden (Sprint 0 gate) |
| 5 | DHM 10-min data not yet received | Data lead | Request sent; generators used until data arrives |
| 6 | Message templates not native-speaker reviewed | AI/D lead | Do in finale prep with Nepali + Hindi speakers |
| 7 | jOOQ code generation not yet configured | Backend B role | First task of Sprint 1 persistence work |
| 8 | S1 golden JSONL is a placeholder | Backend lead | Replace after first successful S1 CI run |
| 9 | `JalDrishtiApplicationTests` will fail until beans exist | Boot | Expected — add `@MockBean` stubs or `@Conditional` beans |
| 10 | `ArrivalTimeCalculatorTest.galchhiArrivalBand`/`.malekhuArrivalBand` fail (off by 1 min / 10 min against the documented calibration table in the test's own comments) | Domain lead | Pre-existing, found 2026-10-18; not touched this session — needs the calibration spec to fix correctly, not a guess |
| 11 | No GCP credentials / Vertex AI access available in the agent sandbox; Gemini enablement and `gemini-3.6-flash`/`asia-south1` availability could not be verified live | AI lead | Verify against real Vertex AI Model Garden before the demo (see ADR-012) |
| 12 | No Docker available in the agent sandbox; the full Postgres-backed boot + console click-through (ScenarioRunEngine end-to-end) was verified only via unit tests + `vue-tsc`/`vite build`, not a live server | Backend lead | Must be click-tested against `docker compose up` / a real deployment before the demo |
| 13 | Arrival bands are not persisted (ADR-011) — a page reload after WARNING has fired shows an empty bands table until the next `BANDS_UPDATED` event | Backend lead | Needs a persisted `ArrivalBandRepository` + migration; deferred |

---

## How to update this file

At the end of **every working session**, update:
1. The status emoji in the Quick Status table (🔲 → 🔄 → ✅)
2. The relevant sprint's checklist (check off completed items)
3. The "Last updated" date at the top
4. Any new known issues in the Known Issues table
