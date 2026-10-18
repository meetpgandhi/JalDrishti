---
name: jaldrishti-sim-engine
description: >
  Complete reference for the JalDrishti simulation and replay engine:
  SimClock design, scenario YAML format, virtual clock mechanics, live-injection
  controls, speed profiles, scenario library (S1–S7), and the replay report format.
  Use this skill when working on jd-sim, writing scenario YAML files, or implementing
  the SimScheduler / clock-control topics.
---

# JalDrishti Simulation Engine Skill

## Design Principles

1. **One code path.** Simulator publishes to the same Pub/Sub topics as the live DHM adapter (`gauge-readings`, `field-reports`, `seismic-events`). Nothing in the trigger engine, Gemini calls, dispatch, or acknowledgement is special-cased.

2. **Injectable clock.** All time logic reads a `java.time.Clock` bean.
   - Sim profile: `SimClock` bean
   - Live profile: `Clock.systemDefaultZone()`

3. **Provenance on screen.** Every input carries its [P]/[D]/[A] tag. The console shows it as a badge.

4. **Deterministic.** Same scenario + seed + config = identical `incident_event` sequence. Gemini text varies, but the numeric guard pins its numbers.

5. **Two timelines.** The console draws the as-happened timeline beside JalDrishti's.

6. **Live injection.** Judges can change the run while it plays; every injection is logged with `source = LIVE_INJECTION`.

7. **CAP in sim always uses `status: Exercise`.**

## SimClock Mechanics

```
States: STOPPED | RUNNING | PAUSED

sim_time = anchorSimTime + (wallNow - anchorWallTime) × speed

On pause/resume/speed change: re-anchor both anchorSimTime and anchorWallTime.
```

Speed options: 1×, 2×, 10×, 30×, 60×, 120×, 240×

Special behaviours:
- **Pause on AI:** while Gemini handles a live-injected report, clock pauses. Real latency shown in overlay.
- **Live-recipient slow-down:** when a judge has an open action, clock drops to 1× until they act or 60 real seconds pass.

`SimScheduler` fires once per simulated minute and calls:
- `POST /internal/sweep` (SilenceSweep)
- `POST /internal/escalate` (EscalationLadder)

These are the same endpoints Cloud Scheduler calls in live mode.

## SimClock Pub/Sub Events (`clock-control` topic)

```json
{
  "runId": "uuid",
  "state": "RUNNING|PAUSED|STOPPED",
  "anchorSim": "2026-08-26T08:36:00+05:45",
  "anchorWall": "2026-10-16T14:00:00Z",
  "speed": 2
}
```

`sim_run` table persists this state so a Core restart resumes without losing position.

## Scenario YAML Format

```yaml
scenario: aug26-as-happened    # ID for pass/fail CI
version: 3
corridor: bhote-trishuli-narayani
configVersion: 12
start: 2026-08-26T08:00:00+05:45
end:   2026-08-26T16:30:00+05:45
seed: 42

variants:                        # judge-toggleable in the console
  syaphrubesiLastReading: "08:40"  # "08:40" or "08:50" — sources disagree
  malekhuCrossing: "11:20"         # "11:20" or "11:43"
  graceMinutes: 3                  # 3 or 10
  phoneCallAt0900: true            # false = counterfactual

speedProfile:
  - {from: "08:00", to: "08:36", speed: 60}
  - {from: "08:36", to: "09:05", speed: 2}   # the key half hour
  - {from: "09:05", to: "11:05", speed: 120}
  - {from: "11:05", to: "11:45", speed: 10}
  - {from: "11:45", to: "16:30", speed: 240}

bookmarks:
  - {at: "08:50", label: "Two gauges go dark"}
  - {at: "08:53", label: "JalDrishti WARNING expected"}
  - {at: "09:15", label: "Actual SMS send"}

stations:
  RSW:
    generator: {model: flat, level: 1.65, trendPer10Min: -0.01, noiseSd: 0.01, tag: A}
    pinned:
      - {t: "08:40", level: 1.62, tag: P, src: nepalitimes}
    outage: {fromSlot: "08:50", reason: DESTROYED, tag: P}
  SYP:
    generator: {model: flat, level: 3.78, noiseSd: 0.01, tag: A}
    pinned:
      - {t: "${variants.syaphrubesiLastReading}", level: 3.80, tag: P, src: spotlight}
    outage: {afterLastPinned: true, reason: DESTROYED, tag: P}
  BTW:
    generator: {model: flat, level: 3.52, noiseSd: 0.01, tag: A}
    pinned:
      - {t: "09:20", level: 3.55, tag: P, src: linkedin-installer}
    outage: {fromSlot: "09:30", reason: DESTROYED, tag: P}
  MLK:
    generator: {model: hydrograph, base: 4.0, jumpAt: "${variants.malekhuCrossing}", jumpM: 4.0, peakAfterMin: 40, recessionHalfLifeMin: 90, tag: A}
  DVG:
    generator: {model: hydrograph, base: 4.6, riseStart: "14:00", peak: {t: "16:00", level: 6.57, tag: P}, mustStayBelow: 7.3, tag: A}

weather:
  catchmentRainMm3h: {value: 0, tag: P, src: smartwatermagazine}

events:
  - {t: "08:37", type: SEISMIC, magnitude: 4.4, lat: 28.2887, lon: 85.5282, publishDelayMin: 15, tag: "P (event), A (delay)"}
  - {t: "09:00", type: FIELD_REPORT, sender: DAO_DHUNCHE, trust: OFFICIAL, lang: ne,
     text: "रसुवागढीतिर भोटेकोसीमा ठूलो बाढी आयो।", tag: "P (call happened), A (wording)",
     enabledWhen: variants.phoneCallAt0900}

asHappened:
  - {t: "09:15", label: "DHM activates early warning; over 600,000 SMS", tag: P}
  - {t: "09:42", label: "Some residents receive the SMS", tag: P}
  - {t: "11:00", label: "High-risk declaration, five districts", tag: P}
  - {t: "${variants.malekhuCrossing}", label: "First threshold crossed (Malekhu)", tag: P}

groundTruthArrivals:
  BTW-S: {from: "09:20", to: "09:30", tag: P}
  MLB:   {from: "11:10", to: "11:43", tag: P}
  MUG:   {at: "13:00", toleranceMin: 15, tag: P}
```

## Generator Models

| Model | Description | Parameters |
|-------|-------------|-----------|
| `flat` | Monsoon baseline: slow trend + seeded noise | `level`, `trendPer10Min`, `noiseSd`, `tag` |
| `hydrograph` | Rising limb to published points, then exponential recession | `base`, `jumpAt`/`riseStart`, `jumpM`/`peak`, `peakAfterMin`, `recessionHalfLifeMin`, `mustStayBelow`, `tag` |
| `outage` | Last published reading, then silence | `fromSlot` or `afterLastPinned`, `reason` |

All generated readings tagged `[A]` on screen. When DHM supplies real 10-minute series, generators are replaced; scenario format unchanged.

## Scenario Validation Rules

The validator rejects any YAML where:
- A generated reading contradicts a published `[P]` point
- A reading crosses a threshold the historical record says was NOT crossed

## Live Injection Controls

| Control | Type | Rules Exercised |
|---------|------|----------------|
| Destroy a gauge | DESTROY_GAUGE | R3, R5 |
| Restore a gauge | RESTORE_GAUGE | R11 |
| Set a level (type value or drag tile) | SET_LEVEL | R1, R2 |
| Rain on/off | RAIN | R6 |
| Send a report (voice/text, any lang) | REPORT | Gemini pipeline, R7, R8 |
| Become a duty-holder (QR code) | — | Dispatch, ack, escalation |
| Telemetry outage (whole group) | TELEMETRY_OUTAGE | R4 |
| Heightened watch | HEIGHTENED_WATCH | R10 |
| Variant toggles | — | Grace, Syaphrubesi timing, phone call |

All injections stored with `source = LIVE_INJECTION` and appear in replay report.

## Scenario Library (S1–S7)

### S1 — 26 Aug As-Happened
- **Proves:** Silence corroboration beats the phone call
- **Pass:** WARNING ≤ 08:53 (≤ 09:03 with 08:50 variant); every [P] point reproduced
- **Key sequence:**
  - 08:37 → Seismic (stored as hint; FR-19 off by default)
  - 08:40 → RSW 1.62m, SYP 3.80m [P]; BTW ~3.5m [A]
  - 08:50 → RSW + SYP readings never arrive [P]
  - 08:53 → Sweep: RSW + SYP SILENT → R5 → **WARNING**; bands computed; actions dispatched; CAP (Exercise)
  - 09:00 → DAO Dhunche report (OFFICIAL); attached as corroboration
  - 09:20 → BTW 3.55m [P] (calm value)
  - 09:33 → Sweep: BTW SILENT → re-anchor; bands narrow
  - 11:10–11:20 → MLK jumps ~4m → R2 + R6 (NON_RAIN) added
  - 16:30 → Forecaster stands down; all-clear CAP

### S2 — No Phone Call
- Same YAML as S1, `phoneCallAt0900: false`
- **Proves:** JalDrishti WARNING unchanged; as-happened falls back to Malekhu 11:20–11:43

### S3 — Gauge Power Failure
- **Proves:** One silent gauge stays internal; no downstream alert
- SYP loses power at 14:15; gauge reader reports normal river at 14:30; WATCH closes
- **Pass:** 0 public WARNINGs; only forecaster and SYP gauge reader notified

### S4 — Telemetry Outage
- **Proves:** Carrier failure is not a flood
- 6 of 10 stations on one carrier silent (incl. RSW, BTW, other basins)
- **Pass:** 0 WATCHes; one outage ticket opened

### S5 — Monsoon Rise
- **Proves:** Rain floods follow normal path without false NON_RAIN tags
- 35mm rain/3h; DVG rises to 7.45m at 03:20
- **Pass:** WATCH at 7.3m; no WARNING below 9.0m; no R6/NON_RAIN tag

### S6 — Second Wave, Heightened Watch
- **Proves:** Known upstream hazard changes the rules (R10)
- Forecaster declares heightened watch; temporary sensor replaces RSW; goes silent at 10:20
- **Pass:** WARNING at 10:23 sweep with no second station needed

### S7 — Judge Sandbox
- **Proves:** Any injection flows through the real path safely
- **Pass:** No crash; every injection logged

## Replay Report Format

At scenario end, `GET /api/v1/sim/runs/{runId}/report` returns:

```json
{
  "runId": "...",
  "scenario": "aug26-as-happened",
  "variants": {...},
  "siteSummary": [
    {
      "siteId": "BTW-S",
      "siteName": "Betrawati",
      "actualArrival": {"from": "09:20", "to": "09:30", "tag": "P"},
      "asHappenedWarning": "09:15",
      "jalDrishtiWarning": "08:53",
      "leadTimeMinutes": {"min": 27, "max": 37},
      "note": "17–27 min if Syaphrubesi lasted to 08:50"
    }
    // ...
  ],
  "ruleFirings": [
    {"at": "08:53", "rule": "R5", "inputs": {...}, "configVersion": 12, "tag": "D"}
    // ...
  ],
  "geminiCalls": [
    {"at": "...", "type": "PARSE|LOCALISE", "guardResult": "PASS|FAILED", "fallbackUsed": false}
    // ...
  ],
  "liveInjections": [],
  "configVersion": 12,
  "matrixVersion": 7
}
```

Export as PDF: `GET /api/v1/sim/runs/{runId}/report?format=pdf`

## Expected Replay Report (S1, 3-min grace, Syaphrubesi lost by 08:50) [D]

| Site | Actual Arrival | As-Happened Warning | JalDrishti Warning | Lead Time |
|------|---------------|--------------------|--------------------|-----------|
| Betrawati | 09:20–09:30 [P] | SMS 09:15 (received 09:42–10:10) | 08:53 | 27–37 min |
| Malekhu | 11:10–11:43 [P] | 09:15 | 08:53 | 137–170 min |
| Mugling | ~13:00 [P] | 09:15 | 08:53 | ~4 hours |

## Scenario File Location

```
scenarios/
├── aug26-as-happened.yaml     (S1/S2 via variant phoneCallAt0900)
├── gauge-power-failure.yaml   (S3)
├── telemetry-outage.yaml      (S4)
├── monsoon-rise.yaml          (S5)
├── second-wave.yaml           (S6)
├── sandbox.yaml               (S7)
└── golden/
    └── s1-events.jsonl        (golden incident_event sequence for NFR-05 diff)
```
