---
name: jaldrishti-domain-rules
description: >
  Complete reference for all 11 trigger-engine rules (R1–R11), station states,
  incident lifecycle, arrival-time model, duty matrix schema, and Gemini field-report
  ingestion. Use this skill when implementing or testing anything in jd-domain,
  jd-application, or jd-adapter-gemini.
---

# JalDrishti Domain Rules Skill

## Station States

```
OK        — reading within normal range
AMBER     — reading ≥ warning level (below danger)
RED       — reading ≥ danger level
RATE_ALERT— overlaid flag: rising faster than threshold (any band)
SILENT    — expected reading overdue > grace period (default: 3 min [A])
TELEMETRY_OUTAGE — silent, but explained by shared carrier failure (R4)
```

## Incident Levels

```
WATCH      → Internal only. Forecaster + nearby duty-holders (earliest arrival ≤ 2h)
WARNING    → All duty-holders (earliest arrival ≤ 6h) + CAP to NDRRMA. Public.
STAND_DOWN → Forecaster-initiated. All previously notified. All-clear CAP.
```

State machine: `NORMAL → WATCH → WARNING → STAND_DOWN`  
A WARNING never cancels itself. `STAND_DOWN` requires a written reason from the forecaster.

## Rules R1–R11

### R1 — Height Band
```
FIRES: reading >= warning_m  → WATCH
       reading >= danger_m   → WARNING (direct)
UNIT TEST: DVG warning=7.3m; reading 7.31m → AMBER within 5s
```

### R2 — Rate of Rise
```
FIRES: rise >= threshold over [10 | 30 | 60] min window → WATCH
Defaults: 0.5m/10min, 0.8m/30min, 1.0m/60min [A]
UNIT TEST: MLK rises 4.0m between consecutive readings → RATE_ALERT within 5s
```

### R3 — Silence Detection
```
FIRES: now > expected_slot + grace  (sweep every 60s)
Result: WATCH (internal — only forecaster + nearest gauge reader)
UNIT TEST: RSW last reported 08:40, cadence=10min, grace=3min
           → when 08:53 sweep runs → RSW SILENT, internal WATCH opens
```
> ⚠️ Missing data cannot trigger an event-driven consumer. This MUST be a scheduled sweep.

### R4 — Telemetry Outage Filter
```
FIRES: > 0.5 of a telemetry group silent in same slot (across basins)
Result: TELEMETRY_OUTAGE ticket; suppresses R3 for that group; NO WARNING
UNIT TEST: 6 of 10 stations on one carrier silent, 3 different basins → 0 WARNINGs
```

### R5 — Cascade Corroboration (THE CRITICAL RULE)
```
FIRES: station i SILENT + downstream station j SILENT or RATE_ALERT,
       where j's evidence overlaps the order-consistency window:
       
       t_earliest_j = t_lastGood_i  + D_ij / v_max  (v_max = 35 m/s)
       t_latest_j   = t_firstMissed_i + D_ij / v_min  (v_min = 3 m/s)

Result: WARNING (corridor-level)

WORKED EXAMPLE (26 Aug):
  RSW: lastGood=08:40, firstMissed=08:50, D_RSW→SYP=15km
  t_earliest_SYP = 08:40 + 15000/35 = 08:40 + 7.1min = 08:47:07
  t_latest_SYP   = 08:50 + 15000/3  = 08:50 + 83.3min = 10:13:20
  SYP loss interval (08:40, 08:50] overlaps [08:47, 10:13] → R5 FIRES
  At the 08:53 sweep: WARNING raised [D]

UNIT TEST: 26 Aug scenario with 3-min grace → WARNING at 08:53
```

### R6 — Rate Without Rain
```
FIRES: R2 fires AND catchment_rain < 5mm in 3h
Result: WARNING tagged NON_RAIN
UNIT TEST: MLK 4m rise on dry morning → WARNING with NON_RAIN tag
```

### R7 — Trusted Report (OFFICIAL)
```
FIRES: OFFICIAL sender field report, Gemini confidence >= 0.7
Result: WARNING (if none open)
UNIT TEST: Nepali voice note from registered OFFICIAL → SYB DEBRIS_FLOOD, WARNING
```

### R8 — Observer Reports
```
FIRES: Two independent OBSERVER reports within 30min AND 20km
       OR: one OBSERVER report + any R1/R2/R3 evidence
Result: WARNING
```

### R9 — Seismic Hint (Could — FR-19)
```
FIRES: Magnitude >= 3.5 inside watched headwater polygon
Result: WATCH; → WARNING if R3 fires within 30min
NOTE: Public seismic feed latency unmeasured [A]; not enabled by default
```

### R10 — Heightened Watch
```
FIRES: Forecaster declares known upstream hazard (FR-16)
Effect: R2 thresholds × 0.5; R3 at top station → WARNING (no corroboration needed)
UNIT TEST: Heightened watch on RSW → RSW goes silent → WARNING at next sweep
```

### R11 — Auto-Cancel
```
FIRES: Silent station returns with normal reading
       OR: OBSERVER at that site reports normal river
       OR: downstream window passes with no change
Result: Closes WATCH ONLY (never a WARNING)
```

## Arrival-Time Model

Calibrated from 26 August 2026 published data [D]:

| Calibration Point | River km | Travel time from RSW front |
|-------------------|----------|--------------------------|
| RSW (Rasuwagadhi) | 0 | 0 |
| BTW (Betrawati) | 38 | 36–46 min [D] |
| MLK (Malekhu) | 100 | 146–179 min [D] |
| Mugling | ~132 | 241–271 min [D]/[A] |
| DVG (Devghat) | 168 | 286–346 min [A] |

**Algorithm:**
1. Linear interpolation between calibration points for site at km x
2. `earliestArrival = anchorLastGood + earliestTravelTime`
3. `latestArrival = anchorFirstMissed + latestTravelTime`
4. Anchor = most downstream station with evidence; update when new station lost/alerts

**Expected bands (08:40–08:50 RSW anchor, 08:53 WARNING):**
- Betrawati: 09:16–09:36
- Bidur: 09:46–10:12
- Galchhi: 10:45–11:23
- Malekhu: 11:06–11:39
- Mugling: 12:41–13:21
- Devghat: 13:26–14:36

> ⚠️ These bands are calibrated on the same event they are tested on. Label as "calibrated on 26 August".

## Gauge Reference Data

| Code | Station | River | km | Warning (m) | Danger (m) | 26 Aug |
|------|---------|-------|-----|-------------|------------|--------|
| RSW | Rasuwagadhi | Bhote Koshi | 0 | 6.0 | obtain | Lost at 1.62m, 08:40 [P] |
| SYP | Syaphrubesi | Bhote Koshi | 15 | 5.5 | obtain | Lost at 3.80m, 08:40/08:50 [P] |
| BTW | Betrawati | Trishuli | 38 | 4.1 | obtain | Last 3.55m at 09:20; lost 09:30 [P] |
| GLC | Galchhi | Trishuli | ~88 | obtain | obtain | Above 14m [P], km [A] |
| MLK | Malekhu (Furke) | Trishuli | 100 | obtain | obtain | ~4m rise 11:20–11:43 [P] |
| DVG | Devghat | Narayani | 168 | 7.3 | 9.0 | Peak 6.57m at 16:00 [P] |

## Field-Report JSON Schema (Gemini Output)

```json
{
  "type": "object",
  "required": ["language", "transcript", "translationEn", "locationId", "hazardType", "confidence"],
  "properties": {
    "language":        {"type": "string", "enum": ["ne", "hi", "en", "mixed"]},
    "transcript":      {"type": "string"},
    "translationEn":   {"type": "string"},
    "locationId":      {"type": "string", "nullable": true},
    "locationText":    {"type": "string", "nullable": true},
    "hazardType":      {"type": "string", "enum": ["DEBRIS_FLOOD","RAPID_RISE","LANDSLIDE","BRIDGE_DAMAGE","PEOPLE_TRAPPED","NO_HAZARD","UNCLEAR"]},
    "evidence":        {"type": "array", "items": {"type": "string", "enum": ["ROARING_SOUND","MUDDY_WATER","BOULDERS","FAST_RISE","BRIDGE_GONE","HOUSES_HIT","PEOPLE_FLEEING"]}},
    "observedTimeText":{"type": "string", "nullable": true},
    "peopleAtRiskCount":{"type": "integer", "nullable": true},
    "confidence":      {"type": "number"},
    "needsCallback":   {"type": "boolean"}
  }
}
```

## Gemini System Instruction (Field Reports)

```
You extract flood observations from field reports sent along the Bhote Koshi - Trishuli - Narayani river corridor in Nepal.
Reports arrive as voice notes or text in Nepali, Hindi, English or a mix.
Rules:
1. Report only what the speaker says. Never infer a number, place or time they did not state.
2. locationId must be one id from the GAZETTEER list, matched by name or alias. If unsure, return null and copy the place words into locationText.
3. hazardType NO_HAZARD is correct when the speaker reports a normal river.
4. Give confidence between 0 and 1 for the whole extraction. Use below 0.5 when audio is unclear or the place is ambiguous.
5. translationEn is a faithful translation, not a summary.
GAZETTEER:
{id, nameEn, nameNe, aliases, riverKm} ... (injected from config)
```

Model settings: `gemini-3.6-flash`, `temperature: 0`, `responseMimeType: application/json`, `responseSchema` (above schema), timeout: 10s, one retry.

## Numeric Guard Algorithm

1. Convert all Devanagari digits (०–९) to 0–9 in Gemini output
2. Assert: every number, time, action code in the Gemini text IS in the source payload
3. Assert: every arrival time and action code in the payload APPEARS in the Gemini text
4. If any assertion fails → send reviewed template, log mismatch with `guard_result: FAILED`

## Duty-Rule Schema

```
ruleId:        string (e.g. W-HYD-01)
matrixVersion: integer
level:         WATCH | WARNING | STAND_DOWN
actorType:     HYDRO_CONTROL | HYDRO_SAFETY | WARD_CHAIR | CDO | TRAFFIC_POLICE | SCHOOL_PRINCIPAL | NDRRMA | GAUGE_READER
siteScope:     ALL | [siteId, ...] | {type: HYDROPOWER}
actionCode:    string (e.g. CLOSE_INTAKE)
actionTextEn:  string
deadline:      expression over `now` and `etaEarliest` (e.g. "min(etaEarliest - 15m, now + 10m)")
ackMeans:      string shown on ack button
requiresAck:   boolean
```

A new matrix version requires draft by one person + approval by a second (four-eyes). Only the latest APPROVED version is live.

## Escalation Ladder

```
+5 min after SENT  → Step 1: Voice call to same person
+10 min            → Step 2: Message + call to registered deputy
+15 min            → Step 3: Flag to CDO + forecaster as UNRESOLVED

Compression: when deadline is close:
  stepInterval = min(5min, (deadline - now) / 3)
```

## Trust Levels

```
OFFICIAL — registered duty-holder number
  → one credible flood report → WARNING (R7)

OBSERVER — registered gauge reader, school, community observer  
  → one report → WATCH; two independent → WARNING (R8)
  → one report + any sensor evidence → WARNING (R8)

PUBLIC — unregistered
  → shown in feed; counts only as corroboration of other evidence
```
