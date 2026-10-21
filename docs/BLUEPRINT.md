# JalDrishti v3 — End-to-End Development Blueprint

> Audience: engineers joining the project who need to understand, run, extend and test the whole system.
> Companion to `AGENTS.md`, `docs/HANDOFF.md`, `docs/DECISIONS.md` and the canonical spec
> (`JalDrishti v3 — Flash-Flood Warning-to-Action System Specification & Simulation Plan.md`).
> Where this file and the spec disagree, the spec wins for domain numbers; the code wins for current behaviour.
>
> Provenance tags used throughout: **[P]** published fact, **[D]** derived, **[A]** assumption.
> Nothing in this file changes a `[P]` value.

---

## 1. What the system is

A flash-flood **warning-to-action** system for the Bhote Koshi → Trishuli → Narayani corridor, Nepal
(Google Cloud AI Builder Cup 2026 entry).

| Question | Answer |
|---|---|
| Problem | Gauges on a debris-flood path get destroyed or go dark. The existing system relied on a phone call and an SMS blast. |
| Core idea | **Silence is evidence.** If an upstream gauge goes silent and a downstream gauge goes silent or spikes, in river order and at a physically possible speed, that is a flood. |
| Headline claim | On the 26 Aug 2026 record, WARNING at **08:53**, **22 minutes before** the actual 09:15 SMS, from two silent gauges only (rule R5). |
| Output | Not just an alert: duty-specific actions to named roles (hydropower, ward chair, police, CDO...) with deadlines, acknowledgements and an escalation ladder. |
| AI role | **Language only** (parse field reports, localise messages, answer operator questions). AI never decides an alert level. |

### 1.1 Non-goals
- No learned/ML flood forecasting. Detection and arrival times are deterministic code.
- No real SMS/voice gateway in the demo (web phone simulator + Telegram webhook stand in).
- No real national CAP gateway in sim; CAP is always `status: Exercise` in sim.
- Single running simulation per instance (one `SimClock`).

---

## 2. Hard rules (never violate)

| # | Rule | Enforcement |
|---|---|---|
| H1 | `jd-domain` has zero Spring/Jackson/DB imports | CI grep job |
| H2 | Adapters depend only on `jd-application`, never on each other | Maven structure, review |
| H3 | Maven only, never Gradle | Project rule |
| H4 | Gemini never sets an alert level | Architecture: rules R1-R11 decide |
| H5 | CAP in sim is `Exercise`, never `Actual` | `application-sim.yml` `cap.status-override` |
| H6 | `incident_event` is append-only (no UPDATE/DELETE) | Convention + review |
| H7 | Gemini calls run at `temperature: 0`, structured JSON | `application.yml` |
| H8 | `NumericGuard` runs after every localisation | `jd-adapter-gemini` |
| H9 | Phones encrypted at rest (`duty_holder.phone_enc`), hashed in logs | Privacy NFR-07 |
| H10 | No names/phones in Gemini prompts, only role + trust level | Privacy NFR-07 |
| H11 | Never edit a Flyway migration already on `main`; add `V{n+1}` | Flyway checksum |
| H12 | Never change a `[P]` value without a source; label `[A]` values in UI/logs | Spec |
| H13 | Do not upgrade Spring Boot past 3.4.3 without checking Spring Cloud GCP compatibility | Known gotcha |

---

## 3. Architecture

### 3.1 Style: hexagonal (ports and adapters), Maven multi-module, Java 21

```
                    +------------------ jd-boot ------------------+
                    |   wiring only (profiles: sim | live)        |
                    +-----------------------------------------------+
   inbound adapters                                      outbound adapters
 +----------------+                                     +----------------------+
 | jd-adapter-rest|--+                               +--| jd-adapter-persistence|
 | jd-adapter-    |  |      +------------------+     |  | jd-adapter-gemini     |
 |   pubsub       |--+----->|  jd-application  |-----+--| jd-adapter-channels   |
 | jd-adapter-    |  | port | use cases + svc  | port|  | jd-adapter-cap        |
 |   channels(in) |--+  .in +--------+---------+ .out|  +----------------------+
 | jd-sim (engine)|--+               |
 +----------------+                  v
                           +------------------+
                           |    jd-domain     |  pure Java: rules R1-R11, models
                           +------------------+
```

### 3.2 Module map

| Module | Responsibility | Key classes |
|---|---|---|
| `jd-domain` | Pure rules and value types. No frameworks. | `R1HeightRule`...`R11AutoCancelRule`, `ArrivalTimeCalculator`, `DeadlineCalculator`, `CorridorConfig`, `Station`, `StationObservationState`, `Incident`, `IncidentEvent`, `DutyRule`, `DispatchedAction`, `ReplayReport`, `IncidentWhyExplanation` |
| `jd-application` | Use cases, inbound/outbound ports | `TriggerEngine`, `ActionDispatcherService`, `FieldReportProcessor`, `ReplayReportService`; ports under `port.in` / `port.out` (incl. `MessageLogQuery`) |
| `jd-adapter-persistence` | PostgreSQL via jOOQ + Flyway. No JPA. | `Jooq*Repository`, `JooqMessageLogQuery`, migrations V1-V4 |
| `jd-adapter-rest` | Spring MVC controllers and SSE | `CorridorStateController`, `SseStreamController`, `SimRunController`, `IncidentActionController`, `IncidentAssistantController`, `InternalSweepController`, `MessageLogController` |
| `jd-adapter-pubsub` | Pub/Sub push endpoints and publishers | `GaugeReadingConsumer`, `GaugeReadingPushPayload` |
| `jd-adapter-gemini` | Vertex AI Gemini: parse, localise, guard | `GeminiFieldReportParser`, `GeminiMessageLocaliser`, `NumericGuard`, `ReviewedTemplateEngine` |
| `jd-adapter-channels` | Delivery: web phone sim, Telegram, ack links | `ChannelDispatcher`, `PhoneSimulatorController`, `AckController`, `TelegramWebhookController` |
| `jd-adapter-cap` | OASIS CAP 1.2 XML and gateway client | `Cap12Builder`, `CapGatewayAdapter` |
| `jd-sim` | Clock, scenario loader/validator/generators, run engine | `SimClock`, `ScenarioRunEngine`, `SimScheduler`, `ScenarioLoader`, `FlatReadingGenerator`, `HydrographReadingGenerator` |
| `jd-boot` | Spring Boot app and wiring | `JalDrishtiApplication`, `JalDrishtiWiringConfig`, `SimTickScheduler` |
| `console/` | Vue 3 operator console and phone simulator | see section 11 |

Dependency rule: domain depends on nothing; application on domain; adapters on application only; `jd-boot` on everything.

### 3.3 Event sourcing

All state changes are immutable events in `incident_event`. Every event carries `runId`, `ruleId`, `configVersion`, `matrixVersion` and a provenance `tag`. `runId = "live"` in production; each simulation run has its own UUID.
**Determinism NFR-05:** same scenario + seed + config must reproduce an identical event sequence (message text excluded). Verified by golden-replay tests against `scenarios/golden/*.jsonl` (currently only `s1-events.jsonl`).

---

## 4. Domain model and rules

### 4.1 Station states
`OK` -> `AMBER` (>= warning) -> `RED` (>= danger); overlay `RATE_ALERT`; `SILENT` (reading overdue past grace); `TELEMETRY_OUTAGE` (silent but explained by shared carrier failure).

### 4.2 Incident levels and lifecycle
`NORMAL -> WATCH -> WARNING -> STAND_DOWN`.
- WATCH: internal (forecaster + nearby duty-holders).
- WARNING: all duty-holders + CAP. A WARNING never cancels itself.
- STAND_DOWN: forecaster only, with a written reason.

### 4.3 Rules R1-R11

| Rule | Fires when | Result |
|---|---|---|
| R1 Height | reading >= warning_m (>= danger_m) | WATCH (WARNING at danger) |
| R2 Rate of rise | >= 0.5 m/10 min, 0.8 m/30 min or 1.0 m/60 min **[A]** | WATCH + RATE_ALERT |
| R3 Silence | `now > lastGood + cadence + grace` (cadence 10, grace 3 min **[A]**), evaluated by sweep | WATCH, station SILENT |
| R4 Telemetry outage | more than 50% of a telemetry group silent in the same slot | Outage ticket, **suppresses R3** for the group, no WARNING |
| **R5 Cascade** | upstream i SILENT and downstream j SILENT/RATE_ALERT with evidence inside the window below | **WARNING** |
| R6 Rate without rain | R2 fires and catchment rain < 5 mm/3 h | WARNING tagged `NON_RAIN` |
| R7 Trusted report | OFFICIAL field report, Gemini confidence >= 0.7 | WARNING |
| R8 Observer reports | 2 independent OBSERVERs within 30 min and 20 km, or 1 + any R1/R2/R3 evidence | WARNING |
| R9 Seismic hint | magnitude >= 3.5 in headwater polygon (not enabled by default; feed latency unmeasured **[A]**) | WATCH, WARNING if R3 follows within 30 min |
| R10 Heightened watch | forecaster declares hazard | R2 thresholds halved; top-station R3 -> WARNING immediately |
| R11 Auto-cancel | silent station returns normal, observer reports normal, or window passes | closes WATCH only, never a WARNING |

### 4.4 R5 worked example (26 Aug)
```
t_earliest_j = t_lastGood_i   + D_ij / v_max   (v_max = 35 m/s)
t_latest_j   = t_firstMissed_i + D_ij / v_min  (v_min =  3 m/s)

RSW lastGood 08:40, firstMissed 08:50, D(RSW->SYP) = 15 km
earliest = 08:40 + 7.1 min = 08:47:07
latest   = 08:50 + 83.3 min = 10:13:20
SYP loss interval (08:40, 08:50] overlaps -> R5 fires at the 08:53 sweep
```
The 08:53 sweep time comes from R3: RSW lastGood 08:40 + 10 min cadence + 3 min grace = 08:53.

### 4.5 Arrival-time model (`ArrivalTimeCalculator`)
Linear interpolation across calibration points from 26 Aug **[D]**. Travel time from the RSW front: BTW (km 38) 36-46 min, MLK (km 100) 146-179 min, Mugling (~km 132) 241-271 min, DVG (km 168) 286-346 min **[A]**.
`earliest = anchorLastGood + earliestTravel`, `latest = anchorFirstMissed + latestTravel`; the anchor is the most downstream station with evidence.
Caveat: calibrated on the same event it is tested on. Label it "calibrated on 26 August". Bands are **not persisted** (ADR-011); a reload after a WARNING shows an empty table until the next `BANDS_UPDATED`.

### 4.6 Stations (seeded in V3)

| Code | Station | River | km | Warning m | Danger m | 26 Aug |
|---|---|---|---|---|---|---|
| RSW | Rasuwagadhi | Bhote Koshi | 0 | 6.0 | - | lost at 1.62 m, 08:40 **[P]** |
| SYP | Syaphrubesi | Bhote Koshi | 15 | 5.5 | - | lost at 3.80 m, 08:40 or 08:50 **[P]** (sources disagree) |
| BTW | Betrawati | Trishuli | 38 | 4.1 | - | last 3.55 m 09:20, lost 09:30 **[P]** |
| GLC | Galchhi | Trishuli | 88 (km **[A]**) | - | - | above 14 m **[P]**, timing unknown |
| MLK | Malekhu (Furke) | Trishuli | 100 | - | - | ~4 m rise 11:20-11:43 **[P]** |
| DVG | Devghat | Narayani | 168 | 7.3 | 9.0 | peak 6.57 m at 16:00 **[P]**, never crosses warning |

All six share telemetry group `dhm-corridor-1`, cadence 10 min.

---

## 5. Data flow (end to end)

```
Scenario YAML --> ScenarioRunEngine (schedule) --\
Live DHM / Pub/Sub gauge-readings ---------------+--> IngestReadingUseCase (TriggerEngine)
Field report (text/voice/phone sim/Telegram) ----+--> ProcessFieldReportUseCase
                                                          |
   Gemini parse (temp 0, JSON) -> R7/R8 <-----------------+
                                                          v
   reading -> station_state -> R1/R2/R6          SimScheduler.tick (every sim minute)
                                                          |-> RunSilenceSweep: R4, R3, R5, R10, R11
                                                          |-> EscalateActions ladder
                                                          v
                     incident + incident_event (append-only)  --> SSE stream --> console
                                                          v
                     ActionDispatcherService: duty matrix -> dispatched_action
                       -> Gemini localise + NumericGuard (or template) -> message
                       -> ChannelDispatcher (web phone / Telegram) + CAP (Exercise)
                                                          v
                     Duty-holder ack (signed HMAC link, 24 h) -> ACKED
                       not acked: +5 min voice, +10 min deputy, +15 min CDO flag
```

Key point: **missing data cannot trigger an event-driven consumer**, so R3/R4/R5 run on a scheduled sweep (every simulated minute in sim; Cloud Scheduler hitting `/internal/sweep` in live).

---

## 6. Simulation engine

### 6.1 Clock
`SimClock`: `sim = anchorSim + (wall - anchorWall) * speed`. States STOPPED / RUNNING / PAUSED. Pause, resume and speed changes re-anchor both times. One clock per instance.

### 6.2 `ScenarioRunEngine`
- `start(runId, scenario, variants)`: loads YAML (classpath `scenarios/` then `./scenarios/`, `../scenarios/`), builds a full schedule, sets clock to scenario start.
- `tick()` (called every 500 ms by `SimTickScheduler`): applies speed, ingests due items through the real ports, runs `SimScheduler.tick`, publishes a clock update, completes at the scenario end.
- Speed priority: manual speed (set via `/speed`) beats the scenario `speedProfile`.
- Per-item errors are caught so one bad item cannot stall the run (the cursor still advances).
- Only one active run. Starting a new run replaces the old one.

### 6.3 Scenario YAML (in `scenarios/`)

| ID | File | Purpose |
|---|---|---|
| S1 | `aug26-as-happened.yaml` | **Primary demo.** 26 Aug flood, phone call at 09:00 |
| S2 | `aug26-no-phone-call.yaml` | Counterfactual: no phone call |
| S3 | `gauge-power-failure.yaml` | One gauge dies, no flood (false-alarm check) |
| S4 | `telemetry-outage.yaml` | Shared carrier failure; R4 must suppress |
| S5 | `monsoon-rise.yaml` | Rain-driven rise |
| S6 | `second-wave.yaml` | Second pulse after first |
| S7 | `sandbox.yaml` | Free play / live injection |

Sections: `variants` (e.g. `graceMinutes`, `syaphrubesiLastReading` 08:40|08:50, `phoneCallAt0900`, `malekhuCrossing`), `speedProfile`, `bookmarks`, `stations` (generator `flat`|`hydrograph`, `pinned` `[P]` readings, `outage`), `weather`, `events` (FIELD_REPORT wired; SEISMIC not yet wired), `asHappened`, `groundTruthArrivals`. A validator rejects files contradicting `[P]` values.
S1 default speed profile: 60x until 08:36, **2x from 08:36 to 09:05** (the critical half-hour, about 14 real minutes), 120x to 11:05, 10x to 11:45, 240x after. Set manual speed to skip the slow window.

### 6.4 Sim-specific guarantees
- Simulator uses the same code paths as live; only clock and data source differ.
- Live-injected readings are stored with `source = LIVE_INJECTION`.
- CAP in sim: `Exercise`.

---

## 7. Persistence

PostgreSQL 16, jOOQ (no JPA), Flyway migrations in `jd-adapter-persistence/src/main/resources/db/migration/`.

| Migration | Content |
|---|---|
| V1 | Baseline schema, 16 tables (see below) |
| V1_1 | Gazetteer sites |
| V2 | Approved duty matrix v1 and 6 duty holders |
| V3 | Stations |
| V4 | `station_state` observation fields: `last_good_reading_at`, `first_missed_slot_at`, `rate_alert_at`, `last_reported_level_m`, `last_reported_rain_mm` |

Tables: `corridor_config`, `station`, `site`, `reading` (PK run, station, observed_at), `station_state`, `incident`, `incident_event`, `field_report`, `duty_holder`, `duty_matrix_version`, `duty_rule`, `dispatched_action`, `message`, `cap_message`, `sim_run`, `flyway_schema_history`.

Notes:
- `message` has no recipient column. The recipient is derived: the action's duty holder, or its deputy for `[DEPUTY ESCALATION]` messages (see `JooqMessageLogQuery`).
- `cap_message.gateway_response` is JSONB (non-JSON text is quoted as a JSON string on save).
- jOOQ classes are generated into `src/main/generated/` (gitignored) and regenerate on build.
- Repository mapping must round-trip every field of the domain record. A missed field silently disables rules (see section 15).

### Duty matrix v1 (seed)

| Level | Role | Action | Deadline |
|---|---|---|---|
| WATCH | Hydro control | STANDBY_CREW | now + 15 min |
| WATCH | Ward chair | ALERT_VOLUNTEERS | now + 15 min |
| WATCH | Gauge reader | INSPECT_RIVER | now + 10 min |
| WARNING | NDRRMA | BROADCAST_CAP | now + 2 min |
| WARNING | Hydro control | CLOSE_INTAKE | min(etaEarliest - 15 m, now + 10 m) |
| WARNING | Hydro safety | EVACUATE_CREWS | earliest arrival - 20 min |
| WARNING | Traffic police | HOLD_TRAFFIC | now + 10 min |
| WARNING | Ward chair | EVACUATE_RIVERSIDE | earliest arrival - 15 min |
| WARNING | School principal | MOVE_STUDENTS | earliest arrival - 20 min |
| WARNING | CDO | CONVENE_COMMITTEE | now + 15 min |
| STAND_DOWN | Hydro control | STAND_DOWN | none (no ack required) |

Duty holders: Chilime control room (SYB), Mailung HEP safety (MLG), Betrawati ward chair (BTW), Galchhi highway police (GLB, Hindi), Rasuwa CDO (DHC), Syaphrubesi gauge reader (SYB).

---

## 8. API reference

Base `/api/v1`. JSON everywhere except CAP (`application/xml`). `/internal/*` is OIDC-only.

| Method and path | Purpose |
|---|---|
| `GET /corridors/{id}` | Config, stations, sites |
| `GET /corridors/{id}/state?runId=` | Station tiles and open incident |
| `GET /incidents/events?runId=` | **Evidence trail**: all incident events of a run |
| `GET /incidents/{id}/why`, `/events/{seq}/why` | Rule, inputs, tags, config version |
| `GET /incidents/{id}/actions` | Acknowledgement board |
| `POST /incidents/{id}/escalate`, `/cancel-watch`, `/stand-down` | Forecaster actions (body: reason) |
| `POST /reports?runId=` | Submit a field report |
| `GET /messages?runId=` | **Message log**: dispatched duty messages (recipient, language, state, escalation step) |
| `GET /cap-messages?runId=` | CAP alerts with XML and gateway response |
| `POST /assistant/ask` | Incident copilot (Gemini, grounded on telemetry) |
| `POST /sim/runs` | Start `{scenario, variants}` -> `{runId}` |
| `POST /sim/runs/{id}/play`, `/pause`, `/speed` | Clock control (`{speed}`) |
| `GET /sim/runs/{id}/clock`, `/timeline`, `/report` | Snapshot, as-happened marks, replay report |
| `POST /sim/runs/{id}/inject` | Live injection (readings, hazards, heightened watch) |
| `GET /phone/duty-holders`, `/phone/inbox/{id}` | Phone simulator |
| `POST /phone/ack/{actionId}`, `/phone/decline/{actionId}`, `/phone/report` | Phone simulator actions |
| `GET /acks/{actionId}?token=` | Signed 24 h HMAC ack link |
| `POST /webhooks/telegram` | Telegram inbound |
| `GET /stream?runId=` | SSE; supports `Last-Event-ID` |
| `POST /internal/sweep`, `/internal/escalate` | Scheduler endpoints |

SSE events: `connected`, `clock`, `reading`, `station-state`, `incident`, `evidence`, `bands`, plus action and report events.

---

## 9. Gemini integration (`jd-adapter-gemini`)

| Use | Behaviour |
|---|---|
| Field-report parsing | Free text/voice transcript -> structured JSON (hazard type, site, confidence). Offline gazetteer heuristics when the model is unavailable. R7/R8 consume the result. |
| Message localisation | Duty message in ne/hi/en. `NumericGuard` converts Devanagari digits to ASCII and asserts every number, time and action code equals the source payload; any mismatch falls back to the reviewed template (`ReviewedTemplateEngine`, `messages.properties`). |
| Copilot | `/assistant/ask`, answers grounded in current incident and telemetry. |
| Limits | 10 s timeout, one retry, then template. Temperature 0. No PII in prompts. |
| Toggle | `JALDRISHTI_GEMINI_VERTEX_ENABLED` (default **false**). Model `gemini-3.6-flash`, region `asia-south1`. |
| Degraded mode | NFR-04: with Gemini off, detection, dispatch (templates) and acknowledgement all work. |

---

## 10. Channels and CAP

- **Web phone simulator** (`/phone`, `/phone/:recipientId`): in-memory inboxes; one-click ack/decline; submit a field report as that role.
- **Telegram**: webhook at `/webhooks/telegram`; token from Secret Manager.
- **Ack links**: signed per recipient (HMAC), expire after 24 h; page `/ack/:dispatchId`.
- **Escalation ladder** (`checkAndEscalateActions`): +5 min voice, +10 min deputy notified (`[DEPUTY ESCALATION]`), +15 min CDO flag; terminal state `UNRESOLVED`.
- **CAP 1.2**: `Cap12Builder` builds the XML, `CapGatewayAdapter` stores it and calls the gateway (simulated `NDRRMA_SIMULATED` in sim). Identifier format `JD-<CORRIDOR>-<STATION>-<incident8>`.

---

## 11. Operator console (`console/`)

Vue 3 + TypeScript + Pinia + vue-router + MapLibre GL + lucide icons.

| Route | View |
|---|---|
| `/` | `ConsoleView` (operator dashboard) |
| `/phone`, `/phone/:recipientId` | `PhoneSimulatorView` |
| `/ack/:dispatchId` | `AckView` |

Structure: `components/console/` (TopBar, SimBar, StatusHeadline, RiverStrip, CorridorMap, GaugeCard, EvidenceTrail, LeadTimeTimeline, ActionBoard, FieldReports, WhySheet, ScenarioSheet, InjectSheet, CopilotSheet, ForecasterActionDialog, ReplayReportModal, DutyPhonePopover), `components/messages/MessagesLog.vue`, `components/ui/` primitives, `stores/console.ts` (single Pinia store: run, stations, events, actions, messages, errors), `composables/useSSE.ts`, `usePresentationMode.ts`, `useLifeSafety.ts`, `lib/` (api endpoints, geo, labels, payload, time), `types/`.

Conventions:
- Every reading/event shows a provenance badge: `[P]` green, `[D]` blue, `[A]` amber. Narration may quote only `[P]`/`[D]`.
- Times shown in NPT (UTC+05:45).
- Build: `npm run build` writes into `jd-boot/src/main/resources/static/`, so a backend build serves the console.
- Dev server (`npm run dev`, port 3000) proxies `/api`. Its `devSimulationBridgePlugin` points at a deployed Cloud Run backend by default; set `VITE_BACKEND_URL=http://localhost:8080` for a local backend.
- Sim speed: store keeps a `manualSpeed`, re-posted on play.

---

## 12. Local development

### 12.1 Prerequisites
JDK 21 (set `JAVA_HOME`), Node 20, PostgreSQL 16 (Docker or the portable `D:\JD\pgsql_tools`). Docker is only needed for Testcontainers adapter tests and the Pub/Sub emulator.

### 12.2 Database
Docker: `docker compose up -d` (Postgres on 5432, db `jaldrishti_dev`, user `jaldrishti`, password `jaldrishti_dev`, plus Pub/Sub emulator on 8085).
Portable (Windows, used on this machine):
```
D:\JD\pgsql_tools\pgsql\bin\pg_ctl.exe start -D D:\JD\pgsql_tools\data -l D:\JD\pgsql_tools\pg.log
D:\JD\pgsql_tools\pgsql\bin\pg_isready.exe
```
`pg_ctl start -w` can hang in a captured shell; start without `-w` and poll `pg_isready`.

### 12.3 Backend
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.10"   # Git Bash on Windows; there is no mvnw.cmd
./mvnw validate
./mvnw test -pl jd-domain -am -q       # fast domain tests, no Docker
./mvnw test -q                          # everything
./mvnw install -DskipTests -q           # REQUIRED before spring-boot:run (see below)
./mvnw spring-boot:run -pl jd-boot      # sim profile, http://localhost:8080
```
**Gotcha:** `spring-boot:run -pl jd-boot` resolves sibling modules from `~/.m2`. After editing any other module, run `install` first or you will run stale code (this hid the manual-speed fix once).

### 12.4 Console
```bash
cd console && npm install && npm run dev   # :3000
npm run type-check
npm run build                              # into jd-boot static/
```

### 12.5 Smoke run via API
```bash
ID=$(curl -s -X POST localhost:8080/api/v1/sim/runs -H "Content-Type: application/json" \
     -d '{"scenario":"aug26-as-happened"}' | sed 's/.*"runId":"\([^"]*\)".*/\1/')
curl -s -X POST localhost:8080/api/v1/sim/runs/$ID/play
curl -s -X POST localhost:8080/api/v1/sim/runs/$ID/speed -H "Content-Type: application/json" -d '{"speed":150}'
curl -N "localhost:8080/api/v1/stream?runId=$ID"
curl -s "localhost:8080/api/v1/incidents/events?runId=$ID"
curl -s "localhost:8080/api/v1/messages?runId=$ID"
curl -s "localhost:8080/api/v1/cap-messages?runId=$ID"
```
Expected: R3 opens at about 03:08 UTC (08:53 NPT), R5 raises WARNING in the same sweep, messages go to duty holders, CAP appears as `Exercise`.

---

## 13. Testing strategy

| Layer | What | Needs Docker |
|---|---|---|
| Domain unit tests | Each rule R1-R11, `ArrivalTimeCalculator`, `DeadlineCalculator` | No |
| Property tests (jqwik) | R5 never fires when downstream is upstream of anchor; window follows velocity bounds | No |
| Application tests | `TriggerEngineTest`, `ActionDispatcherServiceTest` | No |
| Adapter tests | `NumericGuardTest`, `GeminiFieldReportParserTest`, `Cap12BuilderTest`, persistence (Testcontainers) | Persistence yes |
| Sim tests | `ScenarioRunEngineTest` (incl. manual-speed override) | No |
| Boot smoke | `JalDrishtiApplicationTests` (context loads, sim profile) | No |
| Golden replay | S1 event sequence diff vs `scenarios/golden/s1-events.jsonl` | Yes |

State on 2026-10-20: 76 tests, 24 classes, 0 failures (`./mvnw test -q`).

### 13.1 Gaps (be honest about these)
- No automated end-to-end test of "rule fires at 08:53 through the real repositories". The bugs in section 15 were all invisible to unit tests because they sat in the persistence mapping. **Add an integration test** that runs S1 against Postgres and asserts R3 then R5 WARNING and non-empty `/incidents/events`, `/messages`, `/cap-messages`.
- Only S1 has a golden file.
- No accuracy measurement (see 14).

### 13.2 CI (GitHub Actions)
`domain-tests` (`./mvnw test -pl jd-domain`), `build-and-test` (full build with Postgres and Pub/Sub emulator services), `validate-pom` (`./mvnw validate` + forbidden-import grep in `jd-domain`).

---

## 14. Measuring detection quality (planned)

Currently there is **no measured accuracy**. One real event (26 Aug) shows a 22-minute lead on a scenario partly built from assumptions.

Proposed harness:
1. Run a batch of scenarios headless through the real pipeline.
2. Label each `flood: true|false` with arrival times.
3. Compute true positives (WARNING before arrival, with lead time), false positives (WARNING in a no-flood window), false negatives, and per-rule attribution.
4. Sweep `[A]` parameters (grace 3 vs 10 min, velocity bounds, outage fraction) for the false-alarm vs lead-time trade-off.
5. Add randomized negatives (calm days with single-gauge dropouts and outages) and randomized floods to estimate a false-alarm rate.
6. Allow real DHM history to be dropped in later; only that gives real-world accuracy.

---

## 15. Known issues and lessons learned

Fixed on 2026-10-20 (all in persistence/wiring, none visible to unit tests):

| Symptom | Root cause | Fix |
|---|---|---|
| R3/R5 never fired; no WARNING at 08:53 | `JooqStationStateRepository` did not save or load `lastGoodReadingAt`, `firstMissedSlotAt`, `rateAlertAt`, last level/rain, so every read returned null | Migration V4 + repository round-trip |
| Evidence trail empty, `/incidents/events` returned 500 | `findByRunId` selected `ie.seq` etc. without aliases, then read `seq` | Aliased columns in `JooqIncidentEventRepository` |
| Message log empty (404) | Console called `/messages` and `/cap-messages`, which did not exist | `MessageLogController`, `MessageLogQuery`, `JooqMessageLogQuery` |
| CAP list empty, scheduler errors | `gateway_response` is JSONB but a plain string was inserted | Write via `JSONB` |
| Speed control seemed ignored | `spring-boot:run` used a stale installed `jd-sim` jar | `install` before run |
| Clock "stuck" | S1 speed profile is 2x from 08:36 to 09:05 by design | Set manual speed |

Still open:
- Arrival bands not persisted (ADR-011).
- CAP `sentAt` uses wall-clock time, not sim time.
- Sweep runs once per simulated minute, so the WARNING can land up to about a minute after the theoretical 08:53 (it landed at 08:53:23 in the latest run).
- Deputy messages are identified by text prefix (no recipient column).
- SEISMIC scenario events are not wired to an ingestion port.
- `/api/v1/reports` GET is not implemented (POST only); the console `reports` endpoint should be checked.
- Single-instance clock and SSE (NFR-11 multi-instance is post-hackathon).
- Mockito self-attach and SLF4J no-provider warnings in test output are cosmetic.

---

## 16. Deployment

Google Cloud, region `asia-south1`, project `jaldrishti-512011`.

| Item | Value |
|---|---|
| Runtime | Cloud Run service `jaldrishti-core` |
| Image | Artifact Registry repo `jaldrishti`; 3-stage `Dockerfile` (Node 20 console build, Maven JDK 21 build, JRE 21 runtime) |
| Pipeline | `cloudbuild.yaml` (`gcloud builds submit --config=cloudbuild.yaml`) |
| Database | Neon Postgres (credentials never in repo) |
| Service account | `jaldrishti-sa@...` with aiplatform.user, pubsub.editor, secretmanager.secretAccessor, logging.logWriter, cloudtrace.agent |
| Gemini | Off by default; toggle with env var, see `docs/DEPLOY.md` |
| Scheduling (live) | Cloud Scheduler -> `/internal/sweep`, `/internal/escalate` every minute (OIDC) |
| Secrets | Secret Manager (Telegram token, provider keys) |

After every Cloud Build redeploy, re-apply the DB connection env vars (a redeploy can wipe them); see `docs/DEPLOY.md` section 1.

Pub/Sub topics (each with a dead-letter topic): `gauge-readings`, `field-reports`, `seismic-events`, `observations`, `incident-events`, `dispatch-requests`, `acks`, `clock-control`.

---

## 17. Security and privacy checklist

- Phones encrypted at rest; only hashes in logs and events.
- No PII in prompts (role + trust level only).
- `/internal/*` OIDC only; ack links HMAC-signed, 24 h expiry.
- Secrets from Secret Manager, never in files.
- One least-privilege service account per service.
- Audio deleted after 30 days unless attached to an incident.

---

## 18. Non-functional targets (from spec)

| ID | Target |
|---|---|
| NFR-01 | Station state updated <= 5 s (p95); WARNING committed <= 10 s after the justifying sweep |
| NFR-02 | 95% of WARNING messages sent <= 60 s, template on Gemini timeout |
| NFR-03 | 30 s voice note parsed <= 15 s (p95) |
| NFR-04 | Full operation without Gemini |
| NFR-05 | Deterministic replay |
| NFR-06 | Every level change explainable from stored rule, inputs, tags, config version |
| NFR-07/08 | Privacy and security as above |
| NFR-09 | WCAG 2.2 AA; status by shape, label and colour; console usable at 1366x768, phone page at 360 px |
| NFR-10 | ne / hi / en, Noto Sans Devanagari |
| NFR-11 | 99.9% availability after hackathon (multi-instance) |

---

## 19. How to add things

**A new rule:** implement in `jd-domain` as a pure static `evaluate(...)` returning `RuleResult`; unit + jqwik tests; call it from `TriggerEngine` (ingest path or sweep); record `ruleId` on events; update the rule table here and in the domain-rules skill; add a `docs/DECISIONS.md` entry.

**A new scenario:** add YAML under `scenarios/`, keep `[P]` values intact (the validator will reject contradictions), tag assumptions `[A]`, add `groundTruthArrivals` if a flood occurs, add a golden file once the output is reviewed.

**A new endpoint:** define a port in `jd-application` (`port.in` for use cases, `port.out` for reads), implement in the right adapter, controller in `jd-adapter-rest`, add the path to `console/src/lib/api.ts`, then verify with curl against a live run.

**A new table/column:** new `V{n+1}` migration (never edit committed ones); update the jOOQ repository save **and** map; add a round-trip test.

**A new duty action:** insert into `duty_rule` under a new matrix version (draft -> approved), add reviewed templates for ne/hi/en, check the deadline expression in `DeadlineCalculator`.

---

## 20. End-of-session checklist

1. `./mvnw test -q` passes; `npm run type-check` passes if the console changed.
2. Run S1 end to end: R3 and R5 fire at about 08:53, evidence trail, messages and CAP populated.
3. Update `docs/PROGRESS.md` and `docs/SPRINT_LOG.md`.
4. Add `docs/DECISIONS.md` for any architectural change.
5. Commit with the attribution trailer; never commit secrets or generated jOOQ sources.

---

## 21. Reference index

| Need | Where |
|---|---|
| Sprint status, known issues | `docs/PROGRESS.md` |
| Current code map | `docs/HANDOFF.md` |
| Architectural decisions | `docs/DECISIONS.md` |
| Hard rules | `AGENTS.md`, `.specify/constitution.md` |
| Rules in full | `.agents/skills/jaldrishti-domain-rules/SKILL.md` |
| Architecture and API | `.agents/skills/jaldrishti-architecture/SKILL.md` |
| Sim engine | `.agents/skills/jaldrishti-sim-engine/SKILL.md` |
| Deploy | `docs/DEPLOY.md` |
| Local setup | `docs/DEV_SETUP.md` |
| Demo | `docs/DEMO_SCRIPT.md`, `docs/PRE_DEMO_CHECKLIST.md`, `docs/SUBMISSION.md` |
| Canonical spec | `JalDrishti v3 — ... Specification & Simulation Plan.md` |
