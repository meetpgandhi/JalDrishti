-- ============================================================
-- JalDrishti v3 — Seed gazetteer sites referenced by duty_holder
-- ============================================================
-- V1 creates the `site` table but never populates it. V2 then seeds
-- duty_holder rows that reference site_id values (SYB, BTW, MLG, GLB, DHC)
-- via a foreign key, which fails on an empty site table. This migration
-- seeds exactly the sites V2 needs, keeping V1 and V2 untouched.
--
-- River-km values marked here follow Section 4 of the canonical spec
-- where a matching gazetteer id exists; MLG (Mailung HEP) and DHC
-- (Rasuwa district office, not river-sited) have no published value
-- and are left NULL, tagged [A] in the operator console.

INSERT INTO site (id, corridor_id, name_en, name_ne, type, river_km)
VALUES
  ('SYB', 'bhote-trishuli-narayani', 'Syaphrubesi', NULL, 'SETTLEMENT', 15),
  ('BTW', 'bhote-trishuli-narayani', 'Betrawati', NULL, 'SETTLEMENT', 38),
  ('GLB', 'bhote-trishuli-narayani', 'Galchhi Bazaar', NULL, 'SETTLEMENT', 88),
  ('MLG', 'bhote-trishuli-narayani', 'Mailung HEP', NULL, 'HYDROPOWER', NULL),
  ('DHC', 'bhote-trishuli-narayani', 'Rasuwa District Administration Office (Dhunche)', NULL, 'DISTRICT_OFFICE', NULL)
ON CONFLICT (id) DO NOTHING;
