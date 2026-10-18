---
name: jaldrishti-architecture
description: >
  Complete guide to JalDrishti's hexagonal Maven module architecture,
  Spring Boot wiring, Pub/Sub topics, data model, REST/SSE API surface,
  and Google Cloud deployment. Use this skill when writing or reviewing
  any adapter, boot wiring, persistence, or API code.
---

# JalDrishti Architecture Skill

## Module Map

```
jaldrishti-parent (root pom.xml)
├── jd-domain              ← Pure Java 21, ZERO framework deps
├── jd-application         ← Use-case orchestration, ports
├── jd-adapter-pubsub      ← Pub/Sub push + publishers
├── jd-adapter-rest        ← Spring MVC + SSE
├── jd-adapter-persistence ← jOOQ + Flyway + PostgreSQL
├── jd-adapter-gemini      ← Vertex AI Gemini calls + numeric guard
├── jd-adapter-channels    ← Web sim, Telegram, SMS, voice
├── jd-adapter-cap         ← CAP 1.2 builder + gateway
├── jd-sim                 ← SimClock, scenario engine, injection API
└── jd-boot                ← Spring Boot wiring (profiles: live | sim)
```

## Dependency Rules (Maven)

| Module | May depend on |
|--------|--------------|
| `jd-domain` | nothing (pure Java) |
| `jd-application` | `jd-domain` |
| `jd-adapter-*` | `jd-application` only |
| `jd-sim` | `jd-application` |
| `jd-boot` | all adapters + `jd-sim` |

## Spring Boot Wiring

`JalDrishtiApplication` uses `scanBasePackages = "org.jaldrishti"`.

Profile `sim` (active by default in `application.yml`):
- Activates `SimClock` bean instead of `java.time.Clock.systemDefaultZone()`
- Activates `SimScheduler` instead of Cloud Scheduler calls

Profile `live`:
- Uses real system clock
- Relies on Cloud Scheduler for `/internal/sweep` and `/internal/escalate`

## Google Cloud Services

| Service | Usage |
|---------|-------|
| Cloud Run | `jaldrishti-core` (min=1 for demo), `jaldrishti-sim`, `dhm-adapter` |
| Cloud Pub/Sub | 8 topics (see below) |
| Cloud SQL PostgreSQL 16 | append-only event store + projections |
| Cloud Storage | Voice notes, scenario files, replay exports |
| Vertex AI / Gemini | `gemini-3.6-flash`, `asia-south1` |
| Cloud Scheduler | Calls `/internal/sweep` and `/internal/escalate` every minute (live) |
| Secret Manager | Telegram token, SMS/voice provider keys |
| Cloud Logging / Monitoring / Trace | OpenTelemetry end-to-end traces |
| Cloud Build + Artifact Registry | CI/CD |

## Pub/Sub Topics (all have dead-letter topics)

| Topic | Ordering Key | Key Payload Fields |
|-------|-------------|-------------------|
| `gauge-readings` | stationId | runId, stationId, observedAt, levelM, rainMm, source, tag |
| `field-reports` | none | runId, reportId, receivedAt, channel, senderHash, trust, audioUri/text |
| `seismic-events` | none | runId, eventId, originTime, publishedAt, magnitude, lat, lon |
| `observations` | none | runId, reportId, parsed (Section 7 schema), modelId |
| `incident-events` | incidentId | runId, incidentId, seq, at, type, ruleId, payload |
| `dispatch-requests` | dutyHolderId | runId, dispatchId, incidentId, dutyHolderId, actions[], language, channel |
| `acks` | dispatchId | runId, dispatchId, actionCode, at, via, data |
| `clock-control` | runId | runId, state, anchorSim, anchorWall, speed |

All messages carry attributes: `eventTime`, `runId`, `schemaVersion`.

## Database Tables

```sql
-- Config & reference
corridor_config(version, corridor_id, params jsonb, created_at)
station(id, corridor_id, name, river, river_km, lat, lon, warning_m, danger_m, cadence_min, telemetry_group)
site(id, corridor_id, name_en, name_ne, aliases text[], type, river_km, lat, lon)

-- Telemetry
reading(run_id, station_id, observed_at, level_m, rain_mm, received_at, source, tag)  -- PK: (run_id, station_id, observed_at)
station_state(run_id, station_id, band, rate_flag, silent_since, outage_ticket)  -- projection

-- Incidents (event-sourced)
incident(id, run_id, corridor_id, level, opened_at, closed_at, anchor_station, config_version, matrix_version)
incident_event(seq bigserial, incident_id, at, type, rule_id, payload jsonb)  -- append-only

-- Field reports
field_report(id, run_id, received_at, sender_hash, trust, channel, audio_uri, raw_text, parsed jsonb, status, model_id)

-- Duty matrix
duty_holder(id, actor_type, site_id, display_name, phone_enc, telegram_chat_id, language, deputy_id)
duty_matrix_version(version, status, created_by, approved_by, approved_at)
duty_rule(rule_id, version, level, actor_type, site_scope jsonb, action_code, action_text_en, deadline_expr, ack_means, requires_ack)

-- Dispatch
dispatched_action(id, run_id, incident_id, duty_holder_id, action_code, matrix_version, deadline_at, state, sent_at, acked_at, ack_data jsonb, escalation_step)
message(id, dispatched_action_id, channel, language, text, guard_result, fallback_used, model_id)

-- CAP
cap_message(identifier, incident_id, msg_type, status, xml, sent_at, gateway_response)

-- Simulation
sim_run(run_id, scenario, variants jsonb, seed, state, anchor_sim, anchor_wall, speed)
```

## REST & SSE API

### Core endpoints
```
GET  /api/v1/corridors/{id}                       → config, stations, sites
GET  /api/v1/corridors/{id}/state?runId=          → station tiles + open incident
GET  /api/v1/incidents/{id}                       → incident with evidence and bands
GET  /api/v1/incidents/{id}/events/{seq}/why      → rule, inputs, tags, config version
POST /api/v1/incidents/{id}/escalate              → forecaster: body {reason}
POST /api/v1/incidents/{id}/cancel-watch          → forecaster: body {reason}
POST /api/v1/incidents/{id}/stand-down            → forecaster: body {reason}
GET  /api/v1/incidents/{id}/actions               → acknowledgement board
POST /api/v1/reports                              → web field report (multipart: audio|text)
POST /api/v1/acks/{dispatchId}                    → ack from phone simulator (signed link)
POST /api/v1/webhooks/telegram                    → inbound Telegram webhook
POST /api/v1/webhooks/sms                         → inbound SMS webhook
POST /api/v1/assistant/ask                        → FR-18 incident assistant
GET  /api/v1/stream?runId=                        → SSE (reading, station-state, incident, action, report, clock)
POST /internal/sweep                              → silence sweep (Cloud Scheduler / SimScheduler; OIDC)
POST /internal/escalate                           → escalation ladder tick (OIDC)
```

### Simulation endpoints
```
POST /api/v1/sim/runs                             → start run {scenario, variants, seed} → runId
POST /api/v1/sim/runs/{runId}/play                → start clock
POST /api/v1/sim/runs/{runId}/pause               → pause clock
POST /api/v1/sim/runs/{runId}/step                → step to next event
POST /api/v1/sim/runs/{runId}/jump                → jump to bookmark
POST /api/v1/sim/runs/{runId}/speed               → set speed (1,2,10,30,60,120,240)
POST /api/v1/sim/runs/{runId}/inject              → live injection {type: DESTROY_GAUGE|RESTORE_GAUGE|SET_LEVEL|RAIN|REPORT|TELEMETRY_OUTAGE|HEIGHTENED_WATCH, ...}
GET  /api/v1/sim/runs/{runId}/report              → replay report (JSON or ?format=pdf)
```

## SSE Event Types

The `/api/v1/stream` endpoint emits events of type:
- `reading` — new gauge reading (with provenance tag)
- `station-state` — station band/rate/silent update
- `incident` — incident level change
- `action` — dispatched action state update
- `report` — new field report parsed card
- `clock` — simulation clock state

Each SSE event carries a monotonic `id` (sequence). Browser uses `Last-Event-ID` to reconnect without missing events.

## Flyway Migration Conventions

- Location: `jd-adapter-persistence/src/main/resources/db/migration/`
- Format: `V{n}__{description}.sql`
- All tables must include `run_id` column
- `reading` uses idempotent insert: `INSERT ... ON CONFLICT (run_id, station_id, observed_at) DO NOTHING`

## jOOQ Code Generation

Generate jOOQ classes from the Flyway-migrated schema using the jOOQ Maven plugin. Generated classes go in `jd-adapter-persistence/src/main/generated/`.

## Package Naming

```
org.jaldrishti.domain.*          ← jd-domain
org.jaldrishti.application.*     ← jd-application
org.jaldrishti.adapter.pubsub.*  ← jd-adapter-pubsub
org.jaldrishti.adapter.rest.*    ← jd-adapter-rest
org.jaldrishti.adapter.persistence.* ← jd-adapter-persistence
org.jaldrishti.adapter.gemini.*  ← jd-adapter-gemini
org.jaldrishti.adapter.channels.* ← jd-adapter-channels
org.jaldrishti.adapter.cap.*     ← jd-adapter-cap
org.jaldrishti.sim.*             ← jd-sim
org.jaldrishti.boot.*            ← jd-boot
```
