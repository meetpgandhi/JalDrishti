-- Persist the observation timestamps R3/R5 depend on. Without these, every read of
-- station_state returned null lastGoodReadingAt and silence/cascade rules never fired.
ALTER TABLE station_state
    ADD COLUMN IF NOT EXISTS last_good_reading_at   timestamptz,
    ADD COLUMN IF NOT EXISTS first_missed_slot_at   timestamptz,
    ADD COLUMN IF NOT EXISTS rate_alert_at          timestamptz,
    ADD COLUMN IF NOT EXISTS last_reported_level_m  double precision,
    ADD COLUMN IF NOT EXISTS last_reported_rain_mm  double precision;
