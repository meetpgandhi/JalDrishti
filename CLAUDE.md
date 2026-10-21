# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

JalDrishti v3 is a flash-flood warning-to-action system for the Bhote Koshi–Trishuli–Narayani corridor in Nepal (Google Cloud AI Builder Cup 2026 entry). The key demo claim: on the 26 Aug 2026 flood record, JalDrishti fires a WARNING at 08:53 — 22 minutes before the actual 09:15 SMS — using only the silence of two gauges in river order (Rule R5).

**Read before writing any code:**
- `docs/PROGRESS.md` — sprint status, known issues (read first)
- `docs/HANDOFF.md` — current codebase state, package map
- `AGENTS.md` — full architectural rules (this file summarizes the hard parts)
- `.specify/constitution.md` — engineering constitution
- `JalDrishti v3 — Flash-Flood Warning-to-Action System Specification & Simulation Plan.md` — canonical spec. Every number carries a provenance tag `[P]` (published), `[D]` (derived), or `[A]` (assumption). Never change a `[P]` value without a source; always label `[A]` values in UI/logs.
- `docs/DECISIONS.md` for any architectural change.

Update `docs/PROGRESS.md` and `docs/SPRINT_LOG.md` at the end of a working session.

## Commands

### Backend (Maven multi-module, Java 21)

```bash
./mvnw validate                       # sanity check — BUILD SUCCESS
./mvnw compile -q                     # compile all modules
./mvnw test -pl jd-domain -am -q      # domain unit tests only (fast, no Docker)
./mvnw test -q                        # all tests (needs Docker for Testcontainers)
./mvnw test -pl <module> -am          # test one module, e.g. jd-adapter-persistence
./mvnw spring-boot:run -pl jd-boot    # run the app (sim profile by default)
./mvnw package -q -pl jd-boot --am    # build runnable jar
java -jar jd-boot/target/jd-boot-*.jar
```

Run a single test class/method with the usual Maven surefire filter, e.g.:
```bash
./mvnw test -pl jd-domain -Dtest=R5CascadeRuleTest
./mvnw test -pl jd-domain -Dtest=R5CascadeRuleTest#someMethod
```

Local infra: `docker compose up -d` starts Postgres 16 (`localhost:5432`, db `jaldrishti_dev`) and a Pub/Sub emulator (`localhost:8085`). The app runs fine in `sim` profile without Docker/GCP; Testcontainers-based adapter tests need Docker.

### Console (Vue 3 + TypeScript, in `console/`)

```bash
cd console
npm install
npm run dev          # hot-reload dev server on :3000, proxies /api to backend
npm run build         # vue-tsc + vite build, outputs to jd-boot/src/main/resources/static/
npm run type-check    # vue-tsc --noEmit
```

The dev server's `vite.config.ts` also runs a small middleware (`devSimulationBridgePlugin`) that proxies to a deployed Cloud Run backend by default (`VITE_BACKEND_URL` overrides it) and locally persists a manual sim-speed override against the backend's ticker — useful context if sim speed/clock behavior looks odd in dev vs. prod.

### Scenarios / simulation API

```bash
curl -X POST http://localhost:8080/api/v1/sim/runs -H "Content-Type: application/json" \
  -d '{"scenario": "aug26-as-happened", "variants": {"graceMinutes": 3}}'   # -> {"runId": "..."}
curl -X POST http://localhost:8080/api/v1/sim/runs/<runId>/play
curl -X POST http://localhost:8080/api/v1/sim/runs/<runId>/speed -d '{"speed": 10}'
curl -N "http://localhost:8080/api/v1/stream?runId=<runId>"      # SSE event stream
```

Scenario YAML lives in `scenarios/` (S1 `aug26-as-happened.yaml` is the primary demo scenario; S1–S6 cover silence, outage, no-rain, monsoon rise, second wave, etc.). A validator rejects scenario files that contradict `[P]` spec values.

### CI

Three GitHub Actions jobs gate every PR: `domain-tests` (`./mvnw test -pl jd-domain`), `build-and-test` (full build + integration tests with Postgres/Pub-Sub emulator services), `validate-pom` (`./mvnw validate` + a grep for forbidden imports in `jd-domain`).

## Architecture — hexagonal (ports & adapters), Maven multi-module

```
jd-domain              Pure Java 21. Rules R1–R11. ZERO framework deps (no Spring/Jackson/DB imports — CI fails the PR otherwise).
jd-application         Use-case orchestration: port.in (use cases), port.out (repository/publisher ports), service (TriggerEngine, ActionDispatcherService, FieldReportProcessor, ReplayReportService).
jd-adapter-pubsub      Google Cloud Pub/Sub push endpoints & publishers.
jd-adapter-rest        Spring MVC controllers + SSE stream (/api/v1/stream).
jd-adapter-persistence jOOQ repositories + Flyway migrations (PostgreSQL). No JPA/Hibernate.
jd-adapter-gemini      Vertex AI / Gemini: field-report parsing, message localisation, numeric guard.
jd-adapter-channels    Web phone simulator, Telegram webhook, ack links, message templates.
jd-adapter-cap         OASIS CAP 1.2 XML builder + national-gateway client.
jd-sim                 SimClock, scenario loader/validator/scheduler, live-injection API.
jd-boot                Spring Boot wiring (JalDrishtiWiringConfig). Profiles: sim (default, no cloud) | live. Only module that wires every adapter.
```

Hard boundaries (CI-enforced where noted):
- Adapters depend only on `jd-application`, never on each other.
- `jd-boot` is the only module allowed to depend on all adapters.
- Maven only — never introduce Gradle.
- Inter-module dependency versions are centralized in the root `pom.xml` `<dependencyManagement>`.

### Domain rules R1–R11 (in `jd-domain`)

The rule engine is the core IP: silence + cascade detection fires warnings without a phone call. Gemini **never** sets an alert level — it only parses/localises text; R1–R11 decide. Rules of particular note:
- R3 (overdue reading) and R4 (telemetry outage) interact: R4 suppresses R3 when >50% of a telemetry group goes silent in one slot.
- R5 (cascade) is the signature rule — a silent upstream station plus an order-consistent downstream SILENT/RATE_ALERT (velocity window 3–35 m/s) fires WARNING using the formula:
  ```
  t_earliest_j = t_lastGood_i + D_ij / v_max   (v_max = 35 m/s)
  t_latest_j   = t_firstMissed_i + D_ij / v_min (v_min = 3 m/s)
  ```
  R5 fires only when station j's evidence overlaps `[t_earliest_j, t_latest_j]`.
- R6 reclassifies an R2 rate-rise WATCH as WARNING(NON_RAIN) when catchment rain is < 5 mm/3h — i.e. a rise with no rain explanation is treated as more dangerous, not less.
- R10 (forecaster-declared heightened watch) halves R2's thresholds and makes a top-station R3 immediately WARNING — a human-in-the-loop override of the automatic rules.
- Full rule table and the `.agents/skills/jaldrishti-domain-rules/SKILL.md` skill carry the complete conditions/outputs if you need the other rules (R1, R2, R7, R8, R9, R11).

### Event sourcing

All state changes emit immutable events into the append-only `incident_event` table — never UPDATE/DELETE it. Every event carries `runId`, `ruleId`, `configVersion`, `matrixVersion`, and a provenance `tag`. `runId = "live"` in production; each simulation run gets its own UUID `runId`. Replays must reproduce identical `incident_event` sequences (excluding Gemini free text) — this is an explicit NFR, checked by golden-replay tests diffing against `scenarios/golden/*.jsonl`.

### Simulation

The simulator publishes to the *same* Pub/Sub topics as the live DHM adapter — only the clock (`SimClock`, injectable/pausable/speed-multiplied) and data source differ; production code paths are never special-cased for sim. CAP messages generated in sim always use `status: Exercise`, never `Actual`. Every live-injected reading is stored with `source = LIVE_INJECTION`.

### Gemini usage (`jd-adapter-gemini`)

- Field-report parsing and message localisation both run at `temperature: 0` with structured JSON output.
- `NumericGuard` runs after every localisation call: converts Devanagari digits (०–९) to 0–9 and asserts every number/time/action-code in Gemini's output matches the source payload exactly; any mismatch falls back to a reviewed template (`jd-adapter-channels/resources/templates/`) rather than failing closed on the whole message.
- Timeout 10s, one retry, then fallback to reviewed templates.
- No PII goes into prompts: senders are passed as role + trust level only, never name/phone.

### Privacy/security conventions

- Phone numbers encrypted at rest (`duty_holder.phone_enc`) and hashed in logs/events.
- `/internal/*` endpoints are OIDC-authenticated only (Cloud Scheduler / SimScheduler use them).
- Ack links are signed per recipient (HMAC) and expire after 24h.
- Secrets come from Secret Manager, never from code/config files.

### API conventions

Base path `/api/v1/`. `/internal/` is OIDC-only. SSE stream at `GET /api/v1/stream?runId=` supports `Last-Event-ID` for reconnect. CAP responses are `application/xml`; everything else is `application/json`.

### Provenance tags in the UI

Every reading/event shown in the operator console carries a provenance badge: `[P]` published (green), `[D]` derived (blue), `[A]` assumption/default (amber). Narration/demo copy may only quote `[P]`/`[D]` values; `[A]` values must stay visibly labelled.

## Known gotchas (see `docs/PROGRESS.md` "Known Issues" for the live list)

- Spring Boot is pinned at 3.4.3 even though the spec recommends 4.1.x — do not upgrade without checking Spring Cloud GCP compatibility first.
- Arrival bands (`ArrivalTimeCalculator`) are not persisted (ADR-011): a page reload after a WARNING fires shows an empty bands table until the next `BANDS_UPDATED` event.
- jOOQ classes are generated into `src/main/generated/` via Testcontainers-backed codegen in `jd-adapter-persistence/pom.xml`; they're gitignored and regenerate on each build.
- Flyway migrations: `jd-adapter-persistence/src/main/resources/db/migration/`, named `V{n}__{description}.sql`. Never modify a migration already committed to `main` — add a new `V{n+1}` instead.
