-- V1__baseline_schema.sql
-- JalDrishti baseline database schema
-- All tables include run_id. Live data uses run_id = 'live'.
-- Append-only event sourcing: incident_event is NEVER updated or deleted.

-- ============================================================
-- Reference data
-- ============================================================

CREATE TABLE corridor_config (
    version          INTEGER      NOT NULL,
    corridor_id      TEXT         NOT NULL,
    params           JSONB        NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (corridor_id, version)
);

CREATE TABLE station (
    id               TEXT         NOT NULL PRIMARY KEY,
    corridor_id      TEXT         NOT NULL,
    name_en          TEXT         NOT NULL,
    name_ne          TEXT,
    river            TEXT,
    river_km         NUMERIC(6,2),
    lat              NUMERIC(9,6),
    lon              NUMERIC(9,6),
    warning_m        NUMERIC(5,2),
    danger_m         NUMERIC(5,2),
    cadence_min      INTEGER      NOT NULL DEFAULT 10,
    telemetry_group  TEXT,
    aliases          TEXT[]
);

CREATE TABLE site (
    id               TEXT         NOT NULL PRIMARY KEY,
    corridor_id      TEXT         NOT NULL,
    name_en          TEXT         NOT NULL,
    name_ne          TEXT,
    aliases          TEXT[],
    type             TEXT         NOT NULL, -- SETTLEMENT, HYDROPOWER, HIGHWAY_JUNCTION
    river_km         NUMERIC(6,2),
    lat              NUMERIC(9,6),
    lon              NUMERIC(9,6)
);

-- ============================================================
-- Telemetry
-- ============================================================

CREATE TABLE reading (
    run_id           TEXT         NOT NULL,
    station_id       TEXT         NOT NULL REFERENCES station(id),
    observed_at      TIMESTAMPTZ  NOT NULL,
    level_m          NUMERIC(6,3),
    rain_mm          NUMERIC(6,2),
    received_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    source           TEXT         NOT NULL, -- DHM, SIM, INJECT
    tag              TEXT         NOT NULL, -- P, D, A
    PRIMARY KEY (run_id, station_id, observed_at)
    -- Idempotent: INSERT ... ON CONFLICT (run_id, station_id, observed_at) DO NOTHING
);

CREATE INDEX reading_station_time_idx ON reading (run_id, station_id, observed_at DESC);

CREATE TABLE station_state (
    run_id           TEXT         NOT NULL,
    station_id       TEXT         NOT NULL REFERENCES station(id),
    band             TEXT         NOT NULL DEFAULT 'OK', -- OK, AMBER, RED
    rate_flag        BOOLEAN      NOT NULL DEFAULT FALSE,
    silent_since     TIMESTAMPTZ,
    outage_ticket    TEXT,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (run_id, station_id)
);

-- ============================================================
-- Incidents (event-sourced)
-- ============================================================

CREATE TABLE incident (
    id               UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id           TEXT         NOT NULL,
    corridor_id      TEXT         NOT NULL,
    level            TEXT         NOT NULL, -- WATCH, WARNING, STAND_DOWN
    opened_at        TIMESTAMPTZ  NOT NULL,
    closed_at        TIMESTAMPTZ,
    anchor_station   TEXT         REFERENCES station(id),
    config_version   INTEGER      NOT NULL,
    matrix_version   INTEGER
);

CREATE INDEX incident_run_idx ON incident (run_id, corridor_id);

CREATE TABLE incident_event (
    seq              BIGSERIAL    NOT NULL PRIMARY KEY,
    incident_id      UUID         NOT NULL REFERENCES incident(id),
    at               TIMESTAMPTZ  NOT NULL,
    type             TEXT         NOT NULL, -- OPENED, LEVEL_CHANGED, EVIDENCE_ADDED, BANDS_UPDATED, STOOD_DOWN
    rule_id          TEXT,                  -- R1..R11
    payload          JSONB        NOT NULL
    -- NEVER UPDATE OR DELETE from this table
);

CREATE INDEX incident_event_incident_idx ON incident_event (incident_id, seq);

-- ============================================================
-- Field reports
-- ============================================================

CREATE TABLE field_report (
    id               UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id           TEXT         NOT NULL,
    received_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sender_hash      TEXT         NOT NULL, -- salted hash of phone number; never store raw number
    trust            TEXT         NOT NULL, -- OFFICIAL, OBSERVER, PUBLIC
    channel          TEXT         NOT NULL, -- TELEGRAM, SMS, WEB, VOICE, SIM
    audio_uri        TEXT,
    raw_text         TEXT,
    parsed           JSONB,                 -- Section 7 schema
    status           TEXT         NOT NULL DEFAULT 'PENDING', -- PARSED, UNPARSED, REJECTED
    model_id         TEXT
);

-- ============================================================
-- Duty matrix
-- ============================================================

CREATE TABLE duty_holder (
    id               UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_type       TEXT         NOT NULL,
    site_id          TEXT         REFERENCES site(id),
    display_name     TEXT         NOT NULL,
    phone_enc        BYTEA,       -- encrypted at rest; NEVER store plaintext
    telegram_chat_id TEXT,
    language         TEXT         NOT NULL DEFAULT 'ne',
    deputy_id        UUID         REFERENCES duty_holder(id)
);

CREATE TABLE duty_matrix_version (
    version          INTEGER      NOT NULL PRIMARY KEY,
    status           TEXT         NOT NULL DEFAULT 'DRAFT', -- DRAFT, APPROVED, RETIRED
    created_by       TEXT         NOT NULL,
    approved_by      TEXT,
    approved_at      TIMESTAMPTZ
);

CREATE TABLE duty_rule (
    rule_id          TEXT         NOT NULL,
    version          INTEGER      NOT NULL REFERENCES duty_matrix_version(version),
    level            TEXT         NOT NULL,
    actor_type       TEXT         NOT NULL,
    site_scope       JSONB        NOT NULL, -- ALL, [siteIds], {type: HYDROPOWER}
    action_code      TEXT         NOT NULL,
    action_text_en   TEXT         NOT NULL,
    deadline_expr    TEXT         NOT NULL, -- e.g. "min(etaEarliest - 15m, now + 10m)"
    ack_means        TEXT         NOT NULL,
    requires_ack     BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (rule_id, version)
);

-- ============================================================
-- Dispatch
-- ============================================================

CREATE TABLE dispatched_action (
    id               UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id           TEXT         NOT NULL,
    incident_id      UUID         NOT NULL REFERENCES incident(id),
    duty_holder_id   UUID         NOT NULL REFERENCES duty_holder(id),
    action_code      TEXT         NOT NULL,
    matrix_version   INTEGER      NOT NULL,
    deadline_at      TIMESTAMPTZ,
    state            TEXT         NOT NULL DEFAULT 'SENT', -- SENT, DELIVERED, ACKED, DECLINED, ESCALATED_1, ESCALATED_2, ESCALATED_3, UNRESOLVED
    sent_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    acked_at         TIMESTAMPTZ,
    ack_data         JSONB,
    escalation_step  INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX dispatched_action_run_idx ON dispatched_action (run_id, incident_id);
CREATE INDEX dispatched_action_state_idx ON dispatched_action (state, deadline_at) WHERE state NOT IN ('ACKED', 'STOOD_DOWN');

CREATE TABLE message (
    id               UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    dispatched_action_id UUID     NOT NULL REFERENCES dispatched_action(id),
    channel          TEXT         NOT NULL,
    language         TEXT         NOT NULL,
    text             TEXT         NOT NULL,
    guard_result     TEXT         NOT NULL DEFAULT 'PASS', -- PASS, FAILED, SKIPPED
    fallback_used    BOOLEAN      NOT NULL DEFAULT FALSE,
    model_id         TEXT,
    sent_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ============================================================
-- CAP messages
-- ============================================================

CREATE TABLE cap_message (
    identifier       TEXT         NOT NULL PRIMARY KEY,
    incident_id      UUID         NOT NULL REFERENCES incident(id),
    msg_type         TEXT         NOT NULL, -- Alert, Update, Cancel
    status           TEXT         NOT NULL, -- Exercise (sim), Actual (live)
    xml              TEXT         NOT NULL,
    sent_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    gateway_response JSONB
);

-- ============================================================
-- Simulation runs
-- ============================================================

CREATE TABLE sim_run (
    run_id           TEXT         NOT NULL PRIMARY KEY,
    scenario         TEXT         NOT NULL,
    variants         JSONB        NOT NULL DEFAULT '{}',
    seed             BIGINT,
    state            TEXT         NOT NULL DEFAULT 'STOPPED', -- STOPPED, RUNNING, PAUSED
    anchor_sim       TIMESTAMPTZ,
    anchor_wall      TIMESTAMPTZ,
    speed            INTEGER      NOT NULL DEFAULT 1,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
