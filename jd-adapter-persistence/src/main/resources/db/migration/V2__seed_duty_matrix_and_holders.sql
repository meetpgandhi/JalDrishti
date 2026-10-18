-- ============================================================
-- JalDrishti v3 — Sprint 2: Seed Duty Matrix & Pilot Corridor Duty Holders
-- ============================================================

-- 1. Initial approved duty matrix version (four-eyes approval simulated)
INSERT INTO duty_matrix_version (version, status, created_by, approved_by, approved_at)
VALUES (1, 'APPROVED', 'dhm_hydrologist', 'cdo_rasuwa', now())
ON CONFLICT (version) DO NOTHING;

-- 2. Approved duty rules for pilot corridor
INSERT INTO duty_rule (rule_id, version, level, actor_type, site_scope, action_code, action_text_en, deadline_expr, ack_means, requires_ack)
VALUES
  ('W-HYD-01', 1, 'WATCH', 'HYDRO_CONTROL', '"ALL"', 'STANDBY_CREW', 'Put intake-closure crew on standby; test siren', 'now + 15 min', 'Crew ready', true),
  ('W-WRD-01', 1, 'WATCH', 'WARD_CHAIR', '"ALL"', 'ALERT_VOLUNTEERS', 'Alert volunteers; check the evacuation route', 'now + 15 min', 'Volunteers alerted', true),
  ('W-GAU-01', 1, 'WATCH', 'GAUGE_READER', '"ALL"', 'INSPECT_RIVER', 'Look at the river and send a report', 'now + 10 min', 'Report sent', true),
  ('WRN-NDR-01', 1, 'WARNING', 'NDRRMA', '"ALL"', 'BROADCAST_CAP', 'Broadcast the CAP message to affected wards', 'now + 2 min', 'Broadcast sent', true),
  ('WRN-HYD-01', 1, 'WARNING', 'HYDRO_CONTROL', '"ALL"', 'CLOSE_INTAKE', 'Close headworks intake gates and stop river-level work', 'min(etaEarliest - 15m, now + 10m)', 'Gates closed', true),
  ('WRN-HYD-02', 1, 'WARNING', 'HYDRO_SAFETY', '"ALL"', 'EVACUATE_CREWS', 'Move all crews at river level and in tunnels near the river to the assembly point; enter headcount', 'earliest arrival - 20 min', 'Headcount entered', true),
  ('WRN-POL-01', 1, 'WARNING', 'TRAFFIC_POLICE', '"ALL"', 'HOLD_TRAFFIC', 'Stop vehicles entering the corridor at the named checkpoint; close the named bridge', 'now + 10 min', 'Traffic held', true),
  ('WRN-WRD-01', 1, 'WARNING', 'WARD_CHAIR', '"ALL"', 'EVACUATE_RIVERSIDE', 'Move riverside households to the named evacuation point', 'earliest arrival - 15 min', 'Evacuation started', true),
  ('WRN-SCH-01', 1, 'WARNING', 'SCHOOL_PRINCIPAL', '"ALL"', 'MOVE_STUDENTS', 'Move students to the assembly point; enter count', 'earliest arrival - 20 min', 'Students moved', true),
  ('WRN-CDO-01', 1, 'WARNING', 'CDO', '"ALL"', 'CONVENE_COMMITTEE', 'Convene the district disaster committee', 'now + 15 min', 'Committee convened', true),
  ('STD-ALL-01', 1, 'STAND_DOWN', 'HYDRO_CONTROL', '"ALL"', 'STAND_DOWN', 'Stand down; report damage seen', 'none', 'Stood down', false)
ON CONFLICT (rule_id, version) DO NOTHING;

-- 3. Pilot corridor registered duty holders
INSERT INTO duty_holder (id, actor_type, site_id, display_name, phone_enc, telegram_chat_id, language)
VALUES
  ('11111111-1111-1111-1111-111111111101', 'HYDRO_CONTROL', 'SYB', 'Chilime Hydropower Control Room', '\x010203', 'telegram_chilime_ctrl', 'ne'),
  ('11111111-1111-1111-1111-111111111102', 'HYDRO_SAFETY', 'MLG', 'Mailung HEP Safety Officer', '\x010204', 'telegram_mailung_safety', 'ne'),
  ('11111111-1111-1111-1111-111111111103', 'WARD_CHAIR', 'BTW', 'Betrawati Ward Chair (Uttargaya-5)', '\x010205', 'telegram_betrawati_ward', 'ne'),
  ('11111111-1111-1111-1111-111111111104', 'TRAFFIC_POLICE', 'GLB', 'Galchhi Highway Police Post', '\x010206', 'telegram_galchhi_traffic', 'hi'),
  ('11111111-1111-1111-1111-111111111105', 'CDO', 'DHC', 'Chief District Officer (Rasuwa)', '\x010207', 'telegram_rasuwa_cdo', 'ne'),
  ('11111111-1111-1111-1111-111111111106', 'GAUGE_READER', 'SYB', 'Syaphrubesi Gauge Reader', '\x010208', 'telegram_syp_reader', 'ne')
ON CONFLICT (id) DO NOTHING;
