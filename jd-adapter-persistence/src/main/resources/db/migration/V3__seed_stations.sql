-- ============================================================
-- JalDrishti v3 — Seed the six corridor gauge stations
-- ============================================================
-- V1 creates the `station` table but nothing ever populated it, and
-- no runtime code loads the corridor gazetteer YAML into the DB
-- either. Without these rows the trigger engine (R1-R11) has no
-- station data to evaluate, so the headline S1 demo claim (RSW and
-- SYP going silent -> R5 WARNING at 08:53) cannot run against a
-- deployed instance.
--
-- Values and tags follow Section 4 of the canonical spec:
-- warning/danger levels and river-km are [P]/[A] as documented
-- there; "obtain" values (not published in any opened source) are
-- left NULL rather than guessed.

INSERT INTO station (id, corridor_id, name_en, name_ne, river, river_km, warning_m, danger_m, cadence_min, telemetry_group)
VALUES
  ('RSW', 'bhote-trishuli-narayani', 'Rasuwagadhi', NULL, 'Bhote Koshi', 0,   6.0, NULL, 10, 'dhm-corridor-1'),
  ('SYP', 'bhote-trishuli-narayani', 'Syaphrubesi',  NULL, 'Bhote Koshi', 15,  5.5, NULL, 10, 'dhm-corridor-1'),
  ('BTW', 'bhote-trishuli-narayani', 'Betrawati',    NULL, 'Trishuli',    38,  4.1, NULL, 10, 'dhm-corridor-1'),
  ('GLC', 'bhote-trishuli-narayani', 'Galchhi',      NULL, 'Trishuli',    88,  NULL, NULL, 10, 'dhm-corridor-1'),
  ('MLK', 'bhote-trishuli-narayani', 'Malekhu (Furke)', NULL, 'Trishuli', 100, NULL, NULL, 10, 'dhm-corridor-1'),
  ('DVG', 'bhote-trishuli-narayani', 'Devghat',      NULL, 'Narayani',    168, 7.3, 9.0,  10, 'dhm-corridor-1')
ON CONFLICT (id) DO NOTHING;
