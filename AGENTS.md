# JalDrishti — Agent Guidelines

> **Project:** JalDrishti v3 — Flash-Flood Warning-to-Action System  
> **Competition:** Google Cloud AI Builder Cup 2026, Sustainability & Social Impact  
> **Pilot corridor:** Lende Khola → Bhote Koshi → Trishuli → Narayani (Rasuwagadi to Devghat), Nepal

---

## 🔴 START HERE — Living Documents

Every AI agent MUST read these before writing any code:

| File | Purpose | Read when |
|------|---------|-----------|
| [`docs/PROGRESS.md`](docs/PROGRESS.md) | Sprint status, done/todo checklists, known issues | **Always — first** |
| [`docs/HANDOFF.md`](docs/HANDOFF.md) | Current codebase state, package map, key interfaces | **Always — second** |
| [`docs/SPRINT_LOG.md`](docs/SPRINT_LOG.md) | Session-by-session log of what was built | When you need full context |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | Architecture Decision Records (10 ADRs) | Before any architectural change |
| [`docs/DEV_SETUP.md`](docs/DEV_SETUP.md) | Local setup, env vars, CI pipeline | When setting up or troubleshooting |
| [`docs/DEPLOY.md`](docs/DEPLOY.md) | Exact Cloud Run deploy/redeploy commands for this project's real GCP setup | Before any deploy or production config change |

**Update `docs/PROGRESS.md` and `docs/SPRINT_LOG.md` at the end of every session.**

---


The canonical specification is:
```
JalDrishti v3 — Flash-Flood Warning-to-Action System Specification & Simulation Plan.md
```
Read it before writing any code. Every number carries a provenance tag `[P]` (published), `[D]` (derived) or `[A]` (assumption). Never change a `[P]` value without a source, and always label `[A]` values in UI and logs.

The engineering constitution is at `.specify/constitution.md`. Follow it.

---

## 2. Architecture — Hexagonal (Ports & Adapters)

The project is a **Maven multi-module** project. Stick to this strictly.

```
jd-domain           ← Pure Java 21. Rules R1–R11. ZERO framework deps.
jd-application      ← Use-case orchestration. Inbound + outbound ports.
jd-adapter-pubsub   ← Google Cloud Pub/Sub push endpoints & publishers.
jd-adapter-rest     ← Spring MVC controllers + SSE stream.
jd-adapter-persistence ← jOOQ repositories + Flyway migrations (PostgreSQL).
jd-adapter-gemini   ← Vertex AI / Gemini: parse, localise, guard, assistant.
jd-adapter-channels ← Web phone simulator, Telegram, SMS, voice.
jd-adapter-cap      ← CAP 1.2 XML builder + national-gateway client.
jd-sim              ← SimClock, scenario loader/validator, live-injection API.
jd-boot             ← Spring Boot wiring. Profiles: live | sim.
```

### Hard rules

- `jd-domain` has **no Spring, no Jackson, no database** imports. Fail the PR if any appear.
- Adapters depend only on `jd-application`, never on each other.
- `jd-boot` depends on all adapters; it is the only wiring point.
- Build tool is **Maven only**. Never introduce Gradle.
- All inter-module dependency versions are managed in the **root `pom.xml`** `<dependencyManagement>` section.

---

## 3. Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Java | Java 21 LTS | 21 |
| Spring Boot | Spring Boot | 3.4.3 (currently; spec targets 4.1.x — update before finale) |
| Persistence | PostgreSQL 16 + jOOQ + Flyway | As in root pom.xml |
| Messaging | Google Cloud Pub/Sub (plain `google-cloud-pubsub`) | 1.134.0 |
| AI | Google GenAI SDK (`com.google.genai:google-genai`) on Vertex AI | 0.1.1 |
| Gemini model | `gemini-3.6-flash`, `temperature: 0`, `asia-south1` | Pinned in config |
| Testing | JUnit 5 + AssertJ + Mockito + Testcontainers | As in root pom.xml |
| Frontend | Vue 3 + TypeScript + Pinia + MapLibre GL | `console/` directory |

> ⚠️ **Spring Boot version note:** The spec recommends Spring Boot 4.1.x. The POM currently has 3.4.3. Do not upgrade mid-sprint without full regression. Check Spring Cloud GCP compatibility before upgrading.

---

## 4. Domain Rules (R1–R11)

These live exclusively in `jd-domain`. Gemini **never** sets an alert level.

| Rule | Condition | Output |
|------|-----------|--------|
| R1 | Reading ≥ warning level (≥ danger) | WATCH (WARNING) |
| R2 | Rise ≥ threshold over 10/30/60 min | WATCH |
| R3 | Reading overdue > grace period | WATCH (internal only) |
| R4 | >50% of a telemetry group silent in one slot | TELEMETRY_OUTAGE (suppresses R3) |
| R5 | Silent station + downstream SILENT/RATE_ALERT, order-consistent (3–35 m/s) | WARNING |
| R6 | R2 fires with catchment rain < 5 mm/3h | WARNING (NON_RAIN) |
| R7 | OFFICIAL field report, confidence ≥ 0.7 | WARNING |
| R8 | Two independent OBSERVER reports in 30 min/20 km, or one + any R1–R3 | WARNING |
| R9 | Seismic M ≥ 3.5 in watched headwater polygon | WATCH; WARNING if R3 within 30 min |
| R10 | Forecaster declares heightened watch | R2 thresholds × 0.5; R3 at top station → WARNING |
| R11 | Silent station returns normal or window passes | Closes WATCH only |

**R5 order-consistency window (exact formula):**
```
t_earliest_j = t_lastGood_i + D_ij / v_max   (v_max = 35 m/s)
t_latest_j   = t_firstMissed_i + D_ij / v_min (v_min = 3 m/s)
```
R5 fires only when station j's evidence (silence interval or rate-alert time) overlaps `[t_earliest_j, t_latest_j]`.

---

## 5. Gemini Usage Rules

- **Parse** field reports (Section 7 of spec): `temperature: 0`, structured JSON output, gazetteer injected in system prompt.
- **Localise** warning messages (Section 8): `temperature: 0`, numeric guard runs after every call.
- **Numeric guard**: Convert Devanagari digits to 0–9. Every number/time/action-code in Gemini output must match the payload exactly. Any mismatch → use reviewed template, log mismatch.
- **Never** let Gemini decide alert levels, rule evaluation, or arrival times.
- Fallback on any Gemini failure: send reviewed templates (`jd-adapter-channels/resources/templates/`).
- Timeout: 10 s, one retry, then UNPARSED/fallback.

---

## 6. Event Sourcing & Immutability

- All state changes emit **immutable events** stored in `incident_event` (append-only).
- Every event carries: `runId`, `ruleId`, `configVersion`, `matrixVersion`, `tag` ([P]/[D]/[A]).
- The `SimClock` in sim profile; real `java.time.Clock` in live profile.
- `runId = live` for production; each simulation run gets a unique UUID `runId`.
- Replays must produce identical `incident_event` sequences (Gemini text excluded) — NFR-05.

---

## 7. Simulation Rules

- Simulator publishes to the **same Pub/Sub topics** as the live DHM adapter.
- Only the clock and data source change. Production code is never special-cased.
- CAP messages in sim always use `status: Exercise` — never `Actual`.
- Scenario YAML files live in `scenarios/`. A validator rejects files that contradict `[P]` points.
- Speed profile: scenarios define per-time-band simulation speeds (1× to 240×).
- Every live injection is stored with `source = LIVE_INJECTION`.

---

## 8. Database Rules

- Use **jOOQ** for all SQL. No JPA/Hibernate.
- Use **Flyway** for all schema migrations. Files in `jd-adapter-persistence/src/main/resources/db/migration/`.
- All tables include `run_id` column. Live data uses `run_id = 'live'`.
- The `reading` table has an idempotent PK on `(run_id, station_id, observed_at)`.
- Phone numbers are **encrypted at rest**. Hashed in all logs and events (salted hash).
- Audio files deleted after 30 days unless attached to an incident.

---

## 9. API Conventions

- Base path: `/api/v1/`
- Internal endpoints: `/internal/` — OIDC-authenticated only (Cloud Scheduler / SimScheduler).
- SSE stream: `GET /api/v1/stream?runId=` — supports `Last-Event-ID` for reconnect.
- All responses: `application/json`.
- CAP XML: `application/xml` from `/api/v1/cap/...`.
- Ack links: signed per recipient, expire 24h.

---

## 10. Testing Requirements

Every PR must pass:

1. **Domain unit tests** — every rule, including edge cases (same slot, reversed order, speed > 35 m/s).
2. **Scenario tests** — S1–S6 from the spec. S1 must fire WARNING ≤ 08:53 (3-min grace, Syaphrubesi lost by 08:50).
3. **Golden replay** — S1's `incident_event` sequence diffed against `scenarios/golden/s1-events.jsonl`.
4. **Contract tests** — Pub/Sub payloads against JSON Schema; CAP XML against OASIS CAP 1.2 XSD.
5. **Numeric guard** — guard catches Devanagari digit mismatches.

---

## 11. Privacy & Security

- **No names, phone numbers, or personal identifiers** in prompts, logs, or events.
- Senders passed to Gemini as: role + trust level only.
- Phone numbers: encrypted at rest in `duty_holder.phone_enc`.
- `/internal/*`: OIDC tokens only.
- Secrets: Secret Manager (never in code or config files).
- Signed ack links with 24h expiry.

---

## 12. Corridor Configuration

All corridor parameters live in versioned `corridor_config` (jsonb). A new version is created on every change; alerts always log the version used.

Key defaults (all `[A]` unless noted):
- Cadence: 10 min `[P]`
- Grace: 3 min `[A]`
- Sweep interval: 60 s `[A]`
- Cascade band: 3–35 m/s `[A]`
- Rate thresholds: 0.5 / 0.8 / 1.0 m (10/30/60 min) `[A]`
- No-rain threshold: < 5 mm/3h `[A]`
- Parse confidence floor: 0.7 `[A]`

---

## 13. Provenance Tags on Screen

Every reading and event displayed in the operator console must show its provenance badge:
- `[P]` — Published source (green badge)
- `[D]` — Derived calculation (blue badge)
- `[A]` — Assumption/design default (amber badge)

The narration and demo must only quote `[P]` and `[D]` values; `[A]` values must be labelled.

---

## 14. Sprint Plan (reference)

| Sprint | Dates | Key deliverables |
|--------|-------|-----------------|
| 0 — Decisions & Data | 17–20 Oct | CI green; module skeleton; gazetteer; DHM data request |
| 1 — Detection Core | 21–27 Oct | R1–R11; SimClock; Pub/Sub+Postgres adapters; console station strip |
| 2 — Action Loop & Gemini | 28 Oct–2 Nov | Duty matrix; dispatcher; phone sim; ack/escalation; report parsing; CAP; Telegram |
| 3 — Ship | 3–5 Nov | Replay report; Cloud Run deploy; 3-min video; submission |
| Finale Prep | 7 Nov–3 Dec | Gemini eval set; DHM data; load tests; hydrologist review |

---

## 15. What to Never Cut

- Silence + cascade rules (R3, R5)
- Simulator with provenance badges
- Acknowledgement board
- Numeric guard
- Scenarios S1 and S3

Read Section 17 of the spec for the full cut list.
