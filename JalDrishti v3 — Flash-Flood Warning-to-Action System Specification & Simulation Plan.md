# JalDrishti v3 — Flash-Flood Warning-to-Action System

Oct 16, 2026 · @akshay

## Document control

Version 3.0 replaces the v2.0 "48-hour blueprint". It narrows JalDrishti to one verified gap: a warning that depends on someone noticing and carries no agreed duties. It also specifies a replay simulation built only from the published record of the 26 August 2026 flood.

| Field | Value |
| --- | --- |
| Product | JalDrishti — flash-flood warning-to-action system |
| Competition | Google Cloud AI Builder Cup 2026, Sustainability & Social Impact theme |
| Version | 3.0, draft for team review |
| Supersedes | v2.0 (JalDrishti AI end-to-end spec) |
| Pilot corridor | Lende Khola → Bhote Koshi → Trishuli → Narayani (Rasuwagadi to Devghat), Nepal |
| Primary buyer hypothesis | Run-of-river hydropower operators; secondary: national and district disaster authorities |

### Provenance tags

Every number in this spec carries one of three tags. Demo narration may only quote **\[P\]** and **\[D\]** values; **\[A\]** values must be labelled on screen.

| Tag | Meaning | Rule |
| --- | --- | --- |
| \[P\] | Published, with a linked source in Sources | Quote as-is; cite on the slide |
| \[D\] | Derived from \[P\] values; the calculation is shown next to it | Show the working in the replay report |
| \[A\] | Assumption or design default | Replace with DHM data or field calibration before any real use |

### What changed from v2.0

- **Removed:** telecom-ping and ANPR headcounts, victim-deposition search zones, the SAR vision agent, P50/P90/P99 inundation polygons, the impact-pressure model, the North Bihar scope, and embedded Kafka.
- **Added:** a duty matrix, acknowledgement and escalation, Gemini field-report ingestion, a numeric guard on generated messages, and a scenario engine with a virtual clock and live injection.
- **Kept:** rate-of-rise and gauge-silence triggers, provenance on every alert (old NFR-07), and deterministic behaviour when Gemini is unavailable.

## 1. Problem statement

On 26 August 2026 the first flood warning on the Bhote Koshi–Trishuli corridor came from a telephone call, not a gauge. Three gauges died below their thresholds. Nepal's disaster authority put the toll a week later at 1,114 dead and 3,916 missing ([ScienceBlog](https://scienceblog.com/?p=599238)) \[P\].

> **Problem statement.** Flash floods that start without rain — rock-ice avalanches, glacial lake outbursts, landslide-dam breaches — destroy river gauges before they cross height thresholds. On 26 August 2026 the warning depended on one phone call. It took 15 minutes to send, and reached some residents at 9:42 or 10:10. The colour code it carried assigned no duties to anyone. JalDrishti makes the warning independent of anyone noticing, and turns each alert level into pre-agreed, acknowledged actions for named duty-holders along the river.

### Verified timeline, 25–26 August 2026 (Nepal time)

| Time | Event | Tag | Source |
| --- | --- | --- | --- |
| 25 Aug 21:10 | Phalakhu Khola, which joins at Betrawati, crosses its warning level (2.67 m vs 2.6 m) | \[P\] | [Spotlight Nepal](https://www.spotlightnepal.com/2026/08/28/bhotekoshi-flood-warning-came-telephone/) |
| 26 Aug 08:00 | Daily bulletin puts Rasuwa, Nuwakot, Dhading, Gorkha and others at high flash-flood risk; 30 districts under some risk | \[P\] | [Nepali Times](https://nepalitimes.com/nepal-disaster-was-a-warning-to-fix-its-early-warning-system) |
| 08:37 | Rock-ice avalanche off the Lirung massif; seismic signal above magnitude 4 | \[P\] | [HiRISK rapid assessment](https://hirisk.org/wp-content/uploads/2026/08/RHA_NP3_RasuwaFlood.pdf) |
| 08:40 | Rasuwagadhi gauge sends its last reading: 1.62 m and falling (warning level 6.0 m) | \[P\] | [Nepali Times](https://nepalitimes.com/nepal-disaster-was-a-warning-to-fix-its-early-warning-system), [ScienceBlog](https://scienceblog.com/?p=599238) |
| 08:44 | Flood impact at Rasuwagadhi | \[P\] | [HiRISK](https://hirisk.org/wp-content/uploads/2026/08/RHA_NP3_RasuwaFlood.pdf) |
| 08:50 | Rasuwagadhi 08:50 reading never arrives; Syaphrubesi gauge lost at 3.80 m (warning 5.5 m) | \[P\] | [OnlineKhabar](https://english.onlinekhabar.com/rasuwa-flood-early-warning-system.html), [Spotlight Nepal](https://www.spotlightnepal.com/2026/08/28/bhotekoshi-flood-warning-came-telephone/) |
| 09:00 | Flood Forecasting Division learns of the flood by phone from the District Administration Office, Dhunche, then from an observer at Betrawati | \[P\] | [Nepali Times](https://nepalitimes.com/nepal-disaster-was-a-warning-to-fix-its-early-warning-system) |
| 09:15–09:16 | Early warning activated down to Mugling; over 600,000 SMS sent (sources give 632,295 to 679,295) | \[P\] | [Nepali Times](https://nepalitimes.com/nepal-disaster-was-a-warning-to-fix-its-early-warning-system), [ScienceBlog](https://scienceblog.com/?p=599238) |
| 09:20 | Betrawati sends its last report: 3.55 m (warning 4.1 m); the 09:30 reading never arrives | \[P\] | [Sensor installer's post](https://np.linkedin.com/in/nawa0606) |
| 09:42–10:10 | Some citizens receive the SMS; the Prime Minister said it was sent at 9:35 | \[P\] | [eKantipur](https://ekantipur.com/video/2026/09/13/en/early-warning-system-in-nepal-sms-reaches-citizens-mobile-phones-after-a-disaster-54-35.html) |
| 11:00 | High-risk declaration urges evacuation in Rasuwa, Nuwakot, Dhading, Gorkha and Chitwan | \[P\] | [ScienceBlog](https://scienceblog.com/?p=599238) |
| 11:20–11:43 | Malekhu (Furke) rises about 4 m between two 10-minute reports; first threshold crossed anywhere on the network | \[P\] | [Nepali Times](https://nepalitimes.com/nepal-disaster-was-a-warning-to-fix-its-early-warning-system), [Ratopati](https://english.ratopati.com/story/76390/floods-in-trishuli-exceed-danger-level) |
| \~13:00 | Flood reaches Mugling, more than 130 km downstream | \[P\] | [HiRISK](https://hirisk.org/wp-content/uploads/2026/08/RHA_NP3_RasuwaFlood.pdf) |
| 16:00 | Devghat peaks at 6.57 m (warning 7.3 m, danger 9.0 m); 5,850 m³/s, a 2–3-year flood | \[P\] | [Nepali Times](https://nepalitimes.com/nepal-disaster-was-a-warning-to-fix-its-early-warning-system), [ScienceBlog](https://scienceblog.com/?p=599238) |

Sources disagree on two points, and the simulation treats both as parameters: the Malekhu crossing time (11:20 or 11:43) and the SMS send time (09:15 or 09:35).

### The gaps the record shows

1. **Detection depended on a person.** No gauge crossed a threshold until Malekhu, about 2.5 hours after the collapse. The 09:00 phone call is what started the warning.
2. **Evidence took too long to reach people.** The call came at 09:00, the send at 09:15, and some phones got it at 09:42–10:10. Betrawati's gauge was lost between 09:20 and 09:30.
3. **The alert level carried no duties.** The corridor was already Code Orange that morning, like most monsoon mornings. Nobody has written down what Orange obliges a district officer, hydropower operator, bus company or school to do.

### What no software on the Nepal side can fix

The assessment judged that in-channel warning at Rasuwagadhi would have been minimal and likely insufficient for evacuation. It also found that 10 or more minutes of warning further downstream could have saved many lives ([HiRISK](https://hirisk.org/wp-content/uploads/2026/08/RHA_NP3_RasuwaFlood.pdf)). JalDrishti targets that downstream window, from Syaphrubesi to Devghat. It does not claim to protect the first few kilometres below the border.

### Why this is urgent now

The same gorge flooded on 8 July 2025, killing 18 people ([Nepali Times](https://nepalitimes.com/nepal-disaster-was-a-warning-to-fix-its-early-warning-system)). After 26 August the assessment warns that follow-on events are possible: unstable ice remains and loose sediment raises the flash-flood threat for months or years. Damage passed NPR 200 billion; 12 hydropower plants (431 MW) went offline, and 68 bridges were hit ([HiRISK](https://hirisk.org/wp-content/uploads/2026/08/RHA_NP3_RasuwaFlood.pdf)).

## 2. Goals, non-goals and success metrics

The headline target: in the 26 August replay, JalDrishti issues a corridor WARNING by 08:53 without the phone call, 22 minutes before the actual 09:15 send. That assumes Syaphrubesi was lost by 08:50, as two sources report; under the other reading it is 09:03 \[D\].

### Goals

1. **G1 — Detect without a human.** Raise a corridor WARNING from sensor evidence alone (silence, rate of rise, height), corroborated across two stations or by a trusted report.
2. **G2 — Issue in seconds.** Dispatch duty-holder notifications and a CAP message to the national gateway within 60 seconds of a WARNING, under pre-authorised rules.
3. **G3 — Make every alert actionable.** Every notification carries the recipient's actions for that level, taken from an approved duty matrix, plus an arrival-time band for their site.
4. **G4 — Close the loop.** Every duty-holder acknowledges each action or is escalated. The operator always sees who has not confirmed.
5. **G5 — Prove it honestly.** A replay engine reproduces published events and reports lead time per site against what actually happened.

### Non-goals

- Flood forecasting from rainfall, or inundation mapping. Existing DHM bulletins and Google Flood Hub cover rain-driven floods.
- Casualty or headcount estimation. No telecom or ANPR data is used.
- Cross-border data from Tibet. It is out of our control; the design accepts it as a future input.
- Public alerts on a single silent gauge. One silence produces an internal WATCH only.
- Replacing NDRRMA's mass-SMS channel. JalDrishti hands a CAP message to it.

### Success metrics

| Metric | Target | How measured | Tag |
| --- | --- | --- | --- |
| WARNING time, 26 Aug replay, 3-min grace | ≤ 08:53 if Syaphrubesi was lost by 08:50; ≤ 09:03 otherwise | Replay report | \[D\] |
| WARNING time, 26 Aug replay, 10-min grace | ≤ 09:00 or ≤ 09:10 under the same two readings, with no phone-call input | Replay report | \[D\] |
| WARNING time, "no phone call" counterfactual | Same as above; as-happened falls back to 11:20–11:43 | Replay report | \[D\] |
| Lead time at Betrawati | ≥ 27 min (WARNING 08:53 vs gauge loss 09:20–09:30), vs ≤ 15 min as happened | Replay report against the 09:20–09:30 gauge loss | \[D\] |
| Public warnings in the gauge power-failure scenario | 0 | Scenario test | \[A\] |
| Trigger-to-dispatch latency | ≤ 60 s at p95 | Event timestamps in the audit log | \[A\] |
| Field reports with location resolved to the gazetteer | ≥ 9 of 10 in the Nepali/Hindi/English test set | Labelled test set | \[A\] |
| Generated messages whose numbers differ from the structured source | 0 | Numeric guard log | \[A\] |
| Un-acknowledged duty-holders escalated | 100% within 5 min | Acknowledgement log | \[A\] |

The \[D\] targets come from the rules in Section 6 applied to the timeline in Section 1. The engine must reproduce them; if it does not, the spec is wrong, not the slide.

## 3. Users and duty-holders

The operator console serves one person: the DHM duty forecaster. Every other actor is a duty-holder who receives actions on a phone and must acknowledge them.

Hydropower operators are the strongest buyer. Some 900 hydropower project workers were among the missing on 26 August ([Smart Water Magazine](https://smartwatermagazine.com/news/smart-water-magazine/at-least-98-dead-and-403-unaccounted-for-as-a-flash-flood-from-tibet-sweeps-nepals-bhote-koshi-valley-developing)) \[P\]. Operators control their own intakes, sirens and crews, and they can buy and install sensors.

| ID | Actor | Role in JalDrishti | Receives | Must do | Report trust level |
| --- | --- | --- | --- | --- | --- |
| ACT-01 | DHM Flood Forecasting Division duty forecaster | Operator; owns the console | Every WATCH and WARNING, field-report feed, ack board | Confirm or cancel a WATCH; stand down; approve the duty matrix | OFFICIAL |
| ACT-02 | NDRRMA duty officer | Owner of the national mass-SMS gateway | CAP 1.2 message for public broadcast | Broadcast to affected wards | OFFICIAL |
| ACT-03 | Chief District Officer (Rasuwa, Nuwakot, Dhading, Chitwan) | District incident lead | District actions and arrival-time bands | Convene district committee; order evacuations | OFFICIAL |
| ACT-04 | Municipality or ward chair | Settlement lead | Ward actions, evacuation points | Move riverside households; confirm headcount | OFFICIAL |
| ACT-05 | Hydropower control room and site safety officer | Plant lead | Plant actions (intake, gates, crews) | Close intake; pull river-level crews; confirm crew count | OFFICIAL |
| ACT-06 | Highway traffic police (Pasang Lhamu Highway, Prithvi Highway) | Road lead | Road and bridge actions | Hold traffic at named checkpoints; close bridges | OFFICIAL |
| ACT-07 | Principal of a riverside school | School lead | School actions | Move students to the assembly point; confirm | OBSERVER |
| ACT-08 | Gauge reader or registered community observer | Eyes on the river | Requests to confirm what they see | Send voice note, SMS or call | OBSERVER |
| ACT-09 | Member of the public | Reporter | Public alerts via NDRRMA, not via JalDrishti | Optional: report through a public line | PUBLIC |

### Primary user journeys

1. **Forecaster, sudden flood.** A WATCH lands, a second station goes silent, and the system raises a WARNING. The forecaster sees the evidence trail, the dispatched actions and the un-acknowledged list. They intervene only by exception.
2. **Hydropower safety officer.** A phone alert names the plant, an arrival band (e.g. 25–45 min) and three actions. The officer taps to acknowledge each one. Silence after 5 minutes triggers a voice call, then the deputy.
3. **Gauge reader.** They record a 20-second voice note in Nepali. The system turns it into a structured observation, places it on the corridor and uses it as corroboration.
4. **Forecaster, false alarm.** One gauge goes silent on a calm day. The WATCH stays internal and auto-cancels when the gauge returns or the downstream window passes with no rise.

### Trust levels for field reports

- **OFFICIAL:** a registered duty-holder's number. One credible flood report from this source raises a WARNING, as the Dhunche call did.
- **OBSERVER:** a registered gauge reader, school or community observer. One report raises a WATCH; two independent reports, or one plus sensor evidence, raise a WARNING.
- **PUBLIC:** unregistered. Shown in the feed; it counts only as corroboration of other evidence.

## 4. Corridor model

The pilot corridor runs 168 km from Rasuwagadhi on the border to Devghat. It is modelled as an ordered chain of six gauges, four reaches with calibrated wave speeds, and a gazetteer of settlements and assets. All of it is configuration, so a second corridor (for example the Teesta) is new data, not new code.

### Gauges

DHM stations report six times an hour ([Nepali Times](https://nepalitimes.com/nepal-disaster-was-a-warning-to-fix-its-early-warning-system)). Danger levels marked "obtain" are not in any source we opened; request them from DHM.

| Code | Station | River | km from border | Warning level (m) | Danger level (m) | On 26 Aug | Tag |
| --- | --- | --- | --- | --- | --- | --- | --- |
| RSW | Rasuwagadhi | Bhote Koshi | 0 | 6.0 | obtain | Last reading 1.62 m at 08:40; lost | \[P\] |
| SYP | Syaphrubesi | Bhote Koshi | 15 | 5.5 | obtain | Lost at 3.80 m by 08:50 | \[P\] |
| BTW | Betrawati | Trishuli | 36–40 (model: 38) | 4.1 | obtain | Last report 3.55 m at 09:20; lost | \[P\] |
| GLC | Galchhi | Trishuli | \~88 | obtain | obtain | Recorded above 14 m ([IANS](https://ianslive.in/nepal-issues-fresh-flood-warning-as-bhote-koshi-water-level-rises--20260830082421)) | km \[A\], level \[P\] |
| MLK | Malekhu (Furke) | Trishuli | 100 | obtain | obtain | \~4 m rise in one 10-min step; crossed alert then danger, 11:20–11:43 | \[P\] |
| DVG | Devghat | Narayani | 168 | 7.3 | 9.0 | Peak 6.57 m at 16:00 | \[P\] |

The danger levels for DVG come from the DHM flood monitoring page as reported by [ScienceBlog](https://scienceblog.com/?p=599238). Rasuwagadhi, Syaphrubesi and Betrawati warning levels are from the same report and [Spotlight Nepal](https://www.spotlightnepal.com/2026/08/28/bhotekoshi-flood-warning-came-telephone/).

### Reach wave speeds

Arrival-time bands use a speed band per reach, calibrated from the published times of 26 August. The front slowed from about 60 km/h in the gorge to under 25 km/h below Malekhu.

| Reach | Distance | Times used | Speed band | Tag |
| --- | --- | --- | --- | --- |
| RSW → BTW | 38 km (36–40) | Impact at RSW 08:44; BTW lost 09:20–09:30 | 13.0–18.5 m/s (47–67 km/h) | \[D\] |
| BTW → MLK | 62 km | BTW lost 09:20–09:30; MLK jump in the 11:10–11:43 window | 7.2–10.3 m/s (26–37 km/h) | \[D\] |
| MLK → Mugling | \~32 km | MLK 11:10–11:43; Mugling \~13:00 | 4.8–6.9 m/s (17–25 km/h) | \[D\], distance \[A\] |
| Mugling → DVG | \~36 km | Mugling \~13:00; DVG rise from \~14:00 | 6–12 m/s | \[A\], low confidence |

Working for the first row: 36 km in 46 min (08:44→09:30) = 13.0 m/s; 40 km in 36 min (08:44→09:20) = 18.5 m/s. A sensor installer's independent estimate of 16.6 m/s sits inside this band ([LinkedIn](https://np.linkedin.com/in/nawa0606)). The rapid assessment's rough estimate of above 30 m/s in the upper reach applies above Rasuwagadhi.

The silence-cascade rule (Section 6) uses a wider, uncalibrated plausibility band of 3–35 m/s \[A\]. This is so that it still works on a corridor or event with different speeds.

### Settlements and assets (gazetteer)

River-km values marked \[A\] are placeholders. Sprint 0 computes them by snapping each feature to a HydroSHEDS or OpenStreetMap river line and measuring along the channel from Rasuwagadhi.

| ID | Name | Type | River km | Duty-holders | What happened on 26 Aug | Tag |
| --- | --- | --- | --- | --- | --- | --- |
| TIM | Timure | Settlement, customs | \~3 | Ward chair, police post | Destroyed again after rebuilding from the 2025 flood ([GlobalGiving report](https://cl.globalgiving.org/pfil/78980/projdoc.pdf)) | km \[A\] |
| RHP | Rasuwagadhi HPP, 111 MW | Hydropower | \~5 | Control room, safety officer | Powerhouse and substation reported destroyed ([SANDRP](https://sandrp.in/2026/08/28/)) | km \[A\] |
| SYB | Syaphrubesi | Settlement | 15 | Ward chair, school | Over 240 buildings destroyed in one mapped area ([Copernicus](https://global-flood.emergency.copernicus.eu/news/259-floods-in-nepal-and-tibet-august-2026/)) | \[P\] |
| UT1 | Upper Trishuli-1 (under construction) | Hydropower | \~25 | Site safety officer | Destroyed ([SANDRP](https://sandrp.in/2026/08/28/)) | km \[A\] |
| BTW-S | Betrawati | Settlement, bridge | 38 | Ward chair, traffic police | Gauge lost 09:20–09:30 | \[P\] |
| BID | Trishuli Bazaar / Bidur | Settlement, school | \~55 | Ward chair, school principal | About 60 houses swept away; residents moved after the warning ([ShareSansar](https://www.sharesansar.com/newsdetail/flood-from-rasuwa-sweeps-away-around-60-houses-in-nuwakots-trishuli-bazaar-damage-to-hydroelectric-power-2026-08-26)); 1,643 students evacuated | km \[A\] |
| TDH | Trishuli and Devighat HPPs | Hydropower | \~55–60 | Control rooms | Damaged ([ShareSansar](https://www.sharesansar.com/newsdetail/flood-from-rasuwa-sweeps-away-around-60-houses-in-nuwakots-trishuli-bazaar-damage-to-hydroelectric-power-2026-08-26)) | km \[A\] |
| GLB | Galchhi Bazaar | Settlement, highway junction | \~88 | Ward chair, traffic police | Damaged ([Copernicus](https://global-flood.emergency.copernicus.eu/news/259-floods-in-nepal-and-tibet-august-2026/)) | km \[A\] |
| MLB | Malekhu | Settlement, Prithvi Highway | 100 | Ward chair, traffic police | Danger level crossed 11:20–11:43 | \[P\] |
| MUG | Mugling | Highway junction | \~132 | Traffic police | Flood arrived around 13:00 | km \[A\] |
| DVS | Devghat | Settlement | 168 | Ward chair | Peak 16:00 | \[P\] |

### Weather context

The valley had no rain on the morning of 26 August ([Smart Water Magazine](https://smartwatermagazine.com/news/smart-water-magazine/at-least-98-dead-and-403-unaccounted-for-as-a-flash-flood-from-tibet-sweeps-nepals-bhote-koshi-valley-developing)) \[P\]. The engine's "rising with no rain" tag (Section 6) relies on this. The scenario file sets catchment rainfall to 0 mm for that morning.

## 5. Functional requirements

There are 20 requirements: 16 Must, 3 Should, 1 Could. Every Must has a Given/When/Then test that runs in the replay harness, so the demo and the test suite exercise the same path.

| ID | Requirement | Acceptance criteria (Given / When / Then) | Priority |
| --- | --- | --- | --- |
| FR-01 | **Telemetry ingestion.** Accept gauge readings (station, observedAt, levelM, optional rainMm, batteryV) from the DHM adapter or the simulator through one topic. Idempotent on (station, observedAt); out-of-order safe. | Given the same reading twice, when both arrive, then one row is stored. Given a reading 4 min late, when it arrives, then it is stored and any SILENT state for that slot is cleared. | Must |
| FR-02 | **Height bands.** Classify each reading against the station's warning and danger levels. | Given DVG warning 7.3 m, when 7.31 m arrives, then DVG is AMBER within 5 s. | Must |
| FR-03 | **Rate of rise.** Compute the rise over 10, 30 and 60 min windows and raise RATE\_ALERT against per-station thresholds, whatever the height band. | Given MLK rises 4.0 m between two consecutive readings, when the second arrives, then RATE\_ALERT(MLK) is raised within 5 s. | Must |
| FR-04 | **Silence detection.** A clock-driven sweep every 60 s marks a station SILENT when now > expected slot + grace. Missing data cannot trigger an event-driven consumer, so this is scheduled. | Given RSW last reported 08:40, cadence 10 min and grace 3 min, when the 08:53 sweep runs, then RSW is SILENT and an internal WATCH opens. | Must |
| FR-05 | **Telemetry-outage filter.** Stations share telemetry groups (same carrier or VSAT link). If more than half of a group goes silent in one slot, including stations in other basins, classify it as TELEMETRY\_OUTAGE. | Given 6 of 10 stations on one carrier go silent in the same slot across three basins, when the sweep runs, then no WARNING is raised and an outage ticket opens. | Must |
| FR-06 | **Cascade corroboration.** Raise a corridor WARNING when a SILENT station is followed downstream, in hydraulic order and within the 3–35 m/s plausibility band, by another SILENT station or a RATE\_ALERT. | Given the 26 Aug replay with 3-min grace, when the sweeps run, then a WARNING is raised at 08:53 if Syaphrubesi was lost by 08:50, or at 09:03 if it lasted to 08:50. | Must |
| FR-07 | **Field-report ingestion.** Turn voice notes, SMS and web reports into a structured observation using Gemini, resolve the location against the gazetteer and apply the sender's trust level. | Given a Nepali voice note from a registered OFFICIAL number reporting a debris flood at Syaphrubesi, when processed, then the observation shows location SYB and type DEBRIS\_FLOOD within 15 s, and a WARNING opens if none exists. | Must |
| FR-08 | **Incident lifecycle.** One incident per corridor event, with levels WATCH → WARNING → STAND\_DOWN. Every transition stores its evidence. The forecaster may escalate, cancel a WATCH or stand down; cancelling an automatic WARNING needs a written reason. | Given an automatic WARNING, when the forecaster cancels without a reason, then the request is rejected. | Must |
| FR-09 | **Arrival-time bands.** For every gazetteer site downstream of the front, compute \[earliest, latest\] arrival from the front-time estimate and the reach speed bands. Narrow it as stations are lost or report. | Given the WARNING at 08:53, when bands are computed, then the BTW-S band is 09:16–09:36 and it is shown on every BTW-S notification. | Must |
| FR-10 | **Duty matrix.** A versioned, approved table maps (level, actor type, site) → actions, each with a deadline relative to earliest arrival. Only an APPROVED version is live. | Given a draft version, when a WARNING fires, then actions come from the last APPROVED version and the version id is logged. | Must |
| FR-11 | **Message generation.** Build a structured payload for each recipient, localise it with Gemini (ne, hi, en), run the numeric guard, and fall back to fixed templates on any failure. | Given Gemini returns text whose ETA differs from the payload, when the guard runs, then the template message is sent and the mismatch is logged. | Must |
| FR-12 | **Dispatch.** Send through pluggable channels (web phone simulator, Telegram, SMS, voice) and send a CAP 1.2 message to the national-gateway endpoint. | Given a WARNING with 40 recipients, when dispatch runs, then 95% of messages leave within 60 s. | Must |
| FR-13 | **Acknowledgement and escalation.** Each action is acknowledged separately. If none comes: voice call at +5 min, deputy at +10 min, CDO at +15 min. | Given a plant safety officer who has not acknowledged, when 5 min pass, then a voice call is placed and logged. | Must |
| FR-14 | **Operator console.** Corridor strip with station tiles (height, rate, heartbeat), incident panel with evidence trail, acknowledgement board, report feed and event timeline, updated live. | Given a state change, when it commits, then the console reflects it within 2 s over SSE. | Must |
| FR-15 | **Audit and provenance.** Every state change is an append-only event carrying rule id, inputs, config version and model version. An incident exports as JSON and PDF. | Given any alert on screen, when the forecaster opens "why", then the rule, the readings and the config version are shown. | Must |
| FR-16 | **Heightened-watch mode.** The forecaster can declare a known upstream hazard (e.g. a blocked tributary). For a set period this halves rate thresholds and turns a single silence of the top station into a WARNING. | Given heightened watch on RSW, when RSW goes silent, then a WARNING is raised at the next sweep. | Should |
| FR-17 | **Replay report.** After a scenario, compute per-site lead time for JalDrishti against the scenario's as-happened timeline. | Given the 26 Aug replay, when it ends, then the report lists every gazetteer site with both lead times and the tags of the inputs used. | Should |
| FR-18 | **Incident assistant.** The forecaster asks questions in plain language ("which plants have not confirmed?"); Gemini answers by calling read-only tools over the incident store. | Given 3 un-acknowledged plants, when asked, then the answer names those 3 and links each to its record. | Should |
| FR-19 | **Seismic hint.** Poll a public earthquake feed. An event inside a watched headwater polygon raises a WATCH, and adds corroboration to a silence. | Given a magnitude-4 event in the Lhende headwater and RSW silent within 30 min, when both are present, then a WARNING is raised. | Could |
| FR-20 | **Simulation and live injection.** Covered in full in Section 10. | See Section 10. | Must |

FR-19 is Could because the latency of public seismic feeds for this region is unmeasured \[A\]. Measure it before relying on it in the demo.

## 6. Trigger engine

The trigger engine is deterministic Java with no AI in the decision path. Gemini parses reports and words messages; it never sets an alert level. Every decision is a named rule over stored inputs and a versioned configuration, so it can be replayed and explained.

### Station states

- **OK / AMBER / RED** — height band against the station's warning and danger levels (DHM semantics).
- **RATE\_ALERT** — a flag on top of any band: the river is rising faster than its threshold.
- **SILENT** — the expected reading is overdue by more than the grace period.
- **TELEMETRY\_OUTAGE** — silent, but explained by a shared telemetry failure (rule R4).

### Incident levels

| Level | Meaning | Who is told | Public? |
| --- | --- | --- | --- |
| WATCH | Possible sudden flood, not yet corroborated | Forecaster, plus duty-holders at sites with earliest arrival ≤ 2 h \[A\] | No |
| WARNING | Corroborated; act now | All duty-holders at sites with earliest arrival ≤ 6 h \[A\]; CAP message to NDRRMA | Yes |
| STAND\_DOWN | Forecaster ends the incident | Everyone previously notified; all-clear CAP message | Yes |

A WARNING never cancels itself. Only the forecaster can stand it down, with a reason.

&#91;embedded content: Incident state machine · 5 states; rule ids as in the Rules table\]

A single silent gauge stays internal. Only corroboration (R5–R8, R10) or a danger-level reading makes a warning public. STAND\_DOWN closes the incident and the corridor returns to NORMAL.

### Rules

| Rule | Fires when | Result |
| --- | --- | --- |
| R1 Height | Reading ≥ warning level (≥ danger level) | WATCH (WARNING) |
| R2 Rate | Rise ≥ threshold over 10, 30 or 60 min | WATCH |
| R3 Silence | Reading overdue by more than the grace period | WATCH, internal only (forecaster and nearest gauge reader) |
| R4 Outage | More than half of one telemetry group silent in the same slot | Suppresses R3 for that group; opens an outage ticket |
| R5 Cascade | A SILENT station followed downstream by a SILENT station or RATE\_ALERT, order-consistent (test below) | WARNING |
| R6 Rate without rain | R2 fires while catchment rain is < 5 mm in 3 h | WARNING, tagged NON\_RAIN |
| R7 Trusted report | An OFFICIAL report of a flood or debris flow, parse confidence ≥ 0.7 | WARNING |
| R8 Observer reports | Two independent OBSERVER reports within 30 min and 20 km, or one plus any R1–R3 evidence | WARNING |
| R9 Seismic hint (Could) | Magnitude ≥ 3.5 inside a watched headwater polygon | WATCH; WARNING if R3 fires within 30 min |
| R10 Heightened watch | Declared by the forecaster (FR-16) | R2 thresholds × 0.5; R3 at the top station → WARNING |
| R11 Auto-cancel | The silent station returns with a normal reading, an OBSERVER at that site reports a normal river, or the downstream window passes with no change | Closes a WATCH only |

R6 encodes the Narayani rising almost 2 m in two hours on a dry, falling day ([Nepali Times](https://nepalitimes.com/nepal-disaster-was-a-warning-to-fix-its-early-warning-system)).

### Order-consistency test for R5

Upstream station i lost contact between its last good reading and its first missed slot. A front from there can reach downstream station j, at distance D, only inside this window:

```latex
t^{\text{earliest}}_j = t^{\text{lastGood}}_i + \frac{D_{ij}}{v_{\max}}, \qquad t^{\text{latest}}_j = t^{\text{firstMissed}}_i + \frac{D_{ij}}{v_{\min}}
```

R5 fires when j's evidence overlaps that window. For silence, the evidence is j's own loss interval; for a rate alert, it is the reading time. The plausibility band is v\_min = 3 m/s, v\_max = 35 m/s \[A\].

**Worked example, 26 August.** RSW: last good 08:40, first missed 08:50. SYP is 15 km downstream. The window is 08:40 + 7.1 min = 08:47 to 08:50 + 83 min = 10:13. SYP's loss interval (08:40, 08:50\] overlaps it, so R5 fires at the 08:53 sweep \[D\].

The 3.80 m and 3.55 m readings were calm values below warning levels, so R2 does not fire on them; detection comes from silence. Two sources say Syaphrubesi was lost by 08:50. If its last reading was instead 08:50, it goes silent at the 09:03 sweep and R5 fires then; with the phone call enabled, R7 fires first at 09:00 \[D\].

### Arrival-time model

Arrival bands come from a travel-time curve measured from the front's time at RSW. It is calibrated on 26 August, with the front at RSW taken as 08:44 ([HiRISK](https://hirisk.org/wp-content/uploads/2026/08/RHA_NP3_RasuwaFlood.pdf)).

| Calibration point | River km | Travel time from RSW front | Basis | Tag |
| --- | --- | --- | --- | --- |
| RSW | 0 | 0 | — | — |
| BTW | 38 | 36–46 min | Front 08:44; BTW lost 09:20–09:30 | \[D\] |
| MLK | 100 | 146–179 min | MLK jump in the 11:10–11:43 window | \[D\] |
| Mugling | \~132 | 241–271 min | Arrival \~13:00 ± 15 min | \[D\]/\[A\] |
| DVG | 168 | 286–346 min | Rise from \~14:00 ± 30 min | \[A\] |

1. For a site at km x, take the earliest and latest travel times by linear interpolation between calibration points.
2. Earliest arrival = anchor's last good time + earliest travel time. Latest arrival = anchor's first missed slot + latest travel time.
3. The anchor is the most downstream station with evidence. When a new station is lost or alerts, sites below it shift by (observed − predicted midpoint).

With the RSW anchor of 08:40–08:50, the bands are: Betrawati 09:16–09:36, Bidur 09:46–10:12, Galchhi 10:45–11:23, Malekhu 11:06–11:39, Mugling 12:41–13:21 and Devghat 13:26–14:36 \[D\].

**Honesty note.** These bands are calibrated on the same event they are tested on. The demo must say "calibrated on 26 August". Sprint 0 validates the curve out-of-sample on the 8 July 2025 flood if DHM supplies its 10-minute series.

### Configuration parameters

All parameters live in a versioned corridor config. A change creates a new version, and every alert logs the version it used.

| Parameter | Default | Tag | How to calibrate |
| --- | --- | --- | --- |
| Reporting cadence | 10 min | \[P\] | DHM station metadata |
| Grace before SILENT | 3 min | \[A\] | 99th percentile of observed transmission delay per station |
| Silence sweep interval | 60 s | \[A\] | Fixed |
| Rate thresholds (10 / 30 / 60 min) | 0.5 / 0.8 / 1.0 m | \[A\] | 99.9th percentile of monsoon rises per station, from DHM history |
| Cascade plausibility band | 3–35 m/s | \[A\] | Literature on debris-flow and outburst wave speeds |
| Outage fraction per telemetry group | 0.5 | \[A\] | Telemetry history |
| No-rain threshold | < 5 mm in 3 h | \[A\] | DHM rain gauges in the catchment |
| Report parse confidence floor | 0.7 | \[A\] | Labelled test set (Section 16) |
| WATCH notification radius | Earliest arrival ≤ 2 h | \[A\] | Agreed with duty-holders |
| WARNING notification radius | Earliest arrival ≤ 6 h | \[A\] | Agreed with duty-holders |
| Heightened-watch multiplier and maximum duration | 0.5, 7 days | \[A\] | Forecaster policy |

The demo shows how the headline depends on both unknowns. With Syaphrubesi lost by 08:50, a 3-minute grace gives 08:53 and a 10-minute grace gives 09:00. With its last reading at 08:50, they give 09:03 and 09:10. Every combination beats the 09:15 send without the phone call \[D\].

## 7. Field-report ingestion (Gemini)

Gemini turns a voice note or message in Nepali, Hindi or English into one fixed JSON observation. The trigger engine decides what that observation means. On 26 August, a report like this — the call from Dhunche — is what actually started the warning.

### Pipeline

1. **Receive.** Accept a report from the Telegram bot (voice or text), an inbound SMS webhook, or the simulator's phone panel. Store audio in Cloud Storage and the raw record in Postgres.
2. **Identify the sender.** Look up the number in the duty-holder registry to get its trust level (OFFICIAL, OBSERVER or PUBLIC) and home site.
3. **Parse.** Call Gemini with the audio or text, the gazetteer list, the response schema below and temperature 0.
4. **Validate.** Check the schema and enums. The location id must exist in the gazetteer, and the observed time must fall within 2 h of receipt. A failed check stores the report as UNPARSED for the forecaster; it never triggers a rule.
5. **Resolve location.** If Gemini returns no id but gives place text, try alias matching across Latin and Devanagari spellings. If that fails too, leave it unresolved.
6. **Publish.** Emit a `FieldObservation` event. Rules R7 and R8 (Section 6) consume it.

### Model settings

| Setting | Value | Note |
| --- | --- | --- |
| Model | `gemini-3.6-flash` on Vertex AI | Third-party catalogues list it on Vertex with audio input and structured output ([Maxim model library](https://www.getmaxim.ai/bifrost/model-library/compare/vertex_ai/gemini-3.6-flash)). Confirm in Model Garden and pin it in config. |
| SDK | `com.google.genai:google-genai`, Vertex backend | Use the latest release on Maven Central. Google's install page still shows a preview-era version ([Google Cloud docs](https://docs.cloud.google.com/vertex-ai/generative-ai/docs/start/libraries)). |
| Temperature | 0 | Deterministic extraction |
| Response | `responseMimeType: application/json` plus `responseSchema` | No free text to parse |
| Timeout and retry | 10 s, one retry | Then UNPARSED |
| Region | `asia-south1` or `asia-southeast1` | Pick one with the model available; keep data in-region |

### System instruction

```text
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

### Output schema

```json
{
  "type": "object",
  "required": ["language", "transcript", "translationEn", "locationId", "hazardType", "confidence"],
  "properties": {
    "language": {"type": "string", "enum": ["ne", "hi", "en", "mixed"]},
    "transcript": {"type": "string"},
    "translationEn": {"type": "string"},
    "locationId": {"type": "string", "nullable": true},
    "locationText": {"type": "string", "nullable": true},
    "hazardType": {"type": "string", "enum": ["DEBRIS_FLOOD", "RAPID_RISE", "LANDSLIDE", "BRIDGE_DAMAGE", "PEOPLE_TRAPPED", "NO_HAZARD", "UNCLEAR"]},
    "evidence": {"type": "array", "items": {"type": "string", "enum": ["ROARING_SOUND", "MUDDY_WATER", "BOULDERS", "FAST_RISE", "BRIDGE_GONE", "HOUSES_HIT", "PEOPLE_FLEEING"]}},
    "observedTimeText": {"type": "string", "nullable": true},
    "peopleAtRiskCount": {"type": "integer", "nullable": true},
    "confidence": {"type": "number"},
    "needsCallback": {"type": "boolean"}
  }
}
```

### Worked example

Input (Nepali text or voice): म स्याफ्रुबेसीबाट बोल्दैछु। भोटेकोसीमा ठूलो गेग्रान सहितको बाढी आयो, पुल बग्यो, मानिसहरू माथि भाग्दैछन्।

Expected output:

```json
{
  "language": "ne",
  "translationEn": "I am speaking from Syaphrubesi. A big flood with debris came down the Bhote Koshi, the bridge was swept away, people are running uphill.",
  "locationId": "SYB",
  "hazardType": "DEBRIS_FLOOD",
  "evidence": ["BOULDERS", "BRIDGE_GONE", "PEOPLE_FLEEING"],
  "peopleAtRiskCount": null,
  "confidence": 0.9,
  "needsCallback": false
}
```

From an OFFICIAL sender this fires R7 (WARNING). From an OBSERVER it opens a WATCH, or a WARNING if a station is already silent.

A Hindi test line for the demo: गल्छी के पास त्रिशूली नदी बहुत तेज़ी से बढ़ रही है, पानी मटमैला है और पत्थरों की आवाज़ आ रही है। It should resolve to GLB with RAPID\_RISE and MUDDY\_WATER.

### Privacy

- Store phone numbers once, in the registry. Logs and events carry a salted hash.
- Delete audio after 30 days \[A\] unless it is attached to an incident.
- PUBLIC senders' numbers are visible to the forecaster only.
- No names or numbers ever go into a prompt; the sender is passed as a role and trust level.

## 8. Duty matrix and warning generation

Actions come from an approved table, never from Gemini. Gemini only phrases a fixed payload in the recipient's language, and a numeric guard swaps in a reviewed template if any number or id drifts.

Nepal has never written down the link between DHM's colour levels and anyone's duties ([Nepali Times](https://nepalitimes.com/nepal-disaster-was-a-warning-to-fix-its-early-warning-system)). The matrix below is a **draft proposal for the demo**, labelled as such on screen. Real content must be agreed with DHM, NDRRMA, district committees and operators.

### Duty-rule schema

| Field | Type | Example |
| --- | --- | --- |
| ruleId | string | `W-HYD-01` |
| matrixVersion | integer | 7 |
| level | WATCH, WARNING, STAND\_DOWN | WARNING |
| actorType | enum (Section 3) | HYDRO\_CONTROL |
| siteScope | ALL, list of site ids, or site types | type = HYDROPOWER |
| actionCode | string | CLOSE\_INTAKE |
| actionTextEn | canonical English text | Close headworks intake gates and stop river-level work |
| deadline | expression over `now` and `etaEarliest` | `min(etaEarliest - 15m, now + 10m)` |
| ackMeans | text shown on the ack button | Gates closed |
| requiresAck | boolean | true |

A new version is drafted by one person and approved by a second (four-eyes). Only the latest APPROVED version is live; every dispatched action logs its `matrixVersion`.

### Draft matrix for the pilot corridor \[A\]

| Level | Actor | Action | Deadline | Ack means |
| --- | --- | --- | --- | --- |
| WATCH | Hydropower control room | Put intake-closure crew on standby; test siren | now + 15 min | Crew ready |
| WATCH | Ward chair (earliest arrival ≤ 2 h) | Alert volunteers; check the evacuation route | now + 15 min | Volunteers alerted |
| WATCH | Gauge reader nearest the silent station | Look at the river and send a report | now + 10 min | Report sent |
| WARNING | NDRRMA duty officer | Broadcast the CAP message to affected wards | now + 2 min | Broadcast sent |
| WARNING | Hydropower control room | Close headworks intake gates; stop river-level work | earliest arrival − 15 min, at most now + 10 min | Gates closed |
| WARNING | Hydropower safety officer | Move all crews at river level and in tunnels near the river to the assembly point; enter headcount | earliest arrival − 20 min | Headcount entered |
| WARNING | Highway traffic police | Stop vehicles entering the corridor at the named checkpoint; close the named bridge | now + 10 min | Traffic held |
| WARNING | Ward chair | Move riverside households to the named evacuation point | earliest arrival − 15 min | Evacuation started |
| WARNING | School principal | Move students to the assembly point; enter count | earliest arrival − 20 min | Students moved |
| WARNING | Chief District Officer | Convene the district disaster committee | now + 15 min | Committee convened |
| STAND\_DOWN | Everyone notified | Stand down; report damage seen | none | Stood down |

If a deadline is already past when the action is sent, the message says NOW and the ack board marks it late.

### Message pipeline

1. **Payload.** For each recipient, build a fixed JSON payload: incident id, level, site name in the recipient's language, arrival band (local time), actions with deadlines, a one-line evidence summary, the NON\_RAIN tag, and reply instructions.
2. **Localise.** Gemini (same model, temperature 0) writes the message in `ne`, `hi` or `en` from that payload. Limits: 480 characters for SMS, 1,000 for Telegram, and no links except the ack link.
3. **Numeric guard.** Convert Devanagari digits (०–९) to 0–9. Then check that every number, time and action code in the text is in the payload, and that every arrival time and action code in the payload appears in the text.
4. **Fallback.** On any guard failure, timeout or error, send the reviewed template for that language and log the reason.

### Reviewed templates

Templates are the safety net and must be reviewed by a native speaker before any real use.

```text
EN: JALDRISHTI WARNING - {siteName}: sudden flood possible between {etaEarliest} and {etaLatest}. Not a rain flood; expect debris. Your action: 1) {action1} by {deadline1}. Reply 1 when done. Ref {ref}
NE: जलदृष्टि चेतावनी - {siteName}: {etaEarliest} देखि {etaLatest} बीच अचानक बाढी आउन सक्छ। यो वर्षाको बाढी होइन, गेग्रान आउन सक्छ। तपाईंको काम: १) {action1} - {deadline1} भित्र। सकेपछि 1 पठाउनुहोस्। सन्दर्भ {ref}
```

### CAP 1.2 message to the national gateway

Every WARNING and STAND\_DOWN produces an OASIS CAP 1.2 alert for NDRRMA's gateway. **The simulator always sends `status` = Exercise**, so a demo message can never be read as real.

| CAP field | Value |
| --- | --- |
| status | Actual (live) or Exercise (simulator) |
| msgType | Alert (new), Update (band or level change), Cancel (stand-down) |
| scope | Public |
| category | Geo, Met |
| event | Flash flood with debris |
| responseType | Evacuate |
| urgency / severity / certainty | Immediate / Extreme / Observed for a corroborated WARNING; Likely when based on a report only |
| onset / expires | earliest arrival / latest arrival + 6 h |
| area | One `<area>` per affected site: areaDesc plus a circle around its coordinates |
| parameter | `jaldrishti:ruleId`, `jaldrishti:configVersion`, `jaldrishti:matrixVersion` for provenance |

```xml
<alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
  <identifier>JD-2026-08-26-RSW-001</identifier>
  <sender>jaldrishti@dhm.example</sender>
  <sent>2026-08-26T08:53:00+05:45</sent>
  <status>Exercise</status>
  <msgType>Alert</msgType>
  <scope>Public</scope>
  <info>
    <category>Geo</category><category>Met</category>
    <event>Flash flood with debris</event>
    <responseType>Evacuate</responseType>
    <urgency>Immediate</urgency><severity>Extreme</severity><certainty>Observed</certainty>
    <onset>2026-08-26T09:16:00+05:45</onset>
    <headline>Sudden flood on Bhote Koshi - Trishuli: two gauges lost upstream</headline>
    <parameter><valueName>jaldrishti:ruleId</valueName><value>R5</value></parameter>
    <area><areaDesc>Betrawati</areaDesc><circle>27.97,85.18 1.5</circle></area>
  </info>
</alert>
```

The Betrawati coordinates in the example are placeholders \[A\]; Sprint 0 replaces them from the gazetteer.

## 9. Delivery, acknowledgement and escalation

Every dispatched action ends in one of two states: acknowledged, or escalated until a human owns it. The acknowledgement board, not the map, is the console's main view.

### Channels

All channels sit behind one `ChannelAdapter` interface (`send`, `receiveAck`, `deliveryStatus`). The demo can run on the first two alone.

| Channel | Used for | Demo | Production note |
| --- | --- | --- | --- |
| Web phone simulator | Duty-holder inbox and ack buttons in a mobile web page | Primary: judges scan a QR code and become a duty-holder | Not a production channel |
| Telegram bot | Real push to a phone, with inline ack buttons and voice-note reports | Secondary: real push to judges' phones | Free; depends on recipients installing Telegram |
| SMS | Warnings and "1" replies | Optional, via a provider sandbox | Bulk SMS in Nepal runs through NDRRMA's arrangements with operators; sender registration rules apply |
| Voice call | Escalation step 1 | Optional | Use pre-recorded clips per action code; confirm Nepali text-to-speech quality before relying on it \[A\] |
| CAP over HTTPS | Hand-off to NDRRMA's mass-SMS gateway | Simulated endpoint showing the received XML | Needs NDRRMA integration agreement |

### Action lifecycle

1. **SENT** — handed to the channel, keyed by (incident, recipient, action code, matrix version) so retries never duplicate.
2. **DELIVERED** — channel receipt received, where the channel provides one.
3. **ACKED** — button tap, SMS reply (`1`, or `1 2` for several actions), or keypad 1 on a call. Some acks carry data, such as a crew headcount.
4. **DECLINED** — the recipient replies that they cannot do it, with a reason. This goes straight to the forecaster.
5. **ESCALATED\_1..3** — no ack by the ladder step (below).
6. **UNRESOLVED** — the ladder is exhausted. The forecaster sees a red row and a phone number.

### Escalation ladder

| Step | Default time after SENT | Action |
| --- | --- | --- |
| 1 | +5 min | Voice call to the same person |
| 2 | +10 min | Message and call to the registered deputy |
| 3 | +15 min | Flag to the CDO and forecaster as UNRESOLVED |

When the action's deadline is close, the ladder compresses: step interval = min(5 min, (deadline − now) / 3). A hydropower crew-pull with 12 minutes left escalates every 4 minutes.

### Alert-fatigue controls

- An UPDATE goes out only when the level changes or an arrival band moves by more than 10 minutes \[A\].
- A WATCH reaches only duty-holders whose earliest arrival is within 2 hours.
- STAND\_DOWN goes to everyone who got the WARNING, so nobody is left on alert.

### Acknowledgement board

One row per dispatched action: site, actor, action, deadline, status and time since sent. Rows sort by deadline, and late rows turn red. A filter shows "not yet confirmed" only, which is the forecaster's working list during an incident.

## 10. Simulation and replay engine

The simulator feeds the production code path through the same Pub/Sub topics as the live DHM adapter. Only the clock and the data source change. Nothing in the trigger engine, Gemini calls, dispatch or acknowledgement is special-cased for the demo, which is what makes the demo evidence rather than theatre.

### Design rules

1. **One code path.** The simulator publishes to `gauge-readings`, `field-reports` and `seismic-events`. Every downstream service runs unchanged.
2. **Injectable clock.** All time logic reads a `java.time.Clock` bean. In simulation that bean is a `SimClock`; the silence sweep and escalation ladder tick on simulated minutes.
3. **Provenance on screen.** Every input carries its \[P\], \[D\] or \[A\] tag, and the console shows it as a badge on the reading or event.
4. **Deterministic.** The same scenario, seed and config give the same alerts and times. Gemini text varies, but the numeric guard pins its numbers.
5. **Two timelines.** The console draws the as-happened timeline beside JalDrishti's, so the comparison is visible, not narrated.
6. **Live injection.** Judges can change the run while it plays; every injection is logged as an event.

### Virtual clock

- **States:** STOPPED, RUNNING, PAUSED.
- **Controls:** play, pause, step to next event, jump to bookmark, and speed 1×, 2×, 10×, 30×, 60×, 120× or 240×.
- **Mechanics:** while running, simTime = anchorSimTime + (wallNow − anchorWallTime) × speed. Any pause or speed change re-anchors.
- **Ticks:** a `SimScheduler` fires once per simulated minute and calls the same `SilenceSweep` and `EscalationLadder` endpoints that Cloud Scheduler calls in live mode.
- **Event time:** every Pub/Sub message carries an `eventTime` attribute (simulated) and a `runId`. Consumers order by `eventTime`, never by publish time.
- **Speed profile:** each scenario sets speeds per time band, so dull stretches run fast and key moments run near real time.
- **Pause on AI:** while Gemini handles a live-injected report, the clock pauses. Real Gemini latency appears in an overlay, so the demo hides nothing.
- **Live-recipient slow-down:** when a judge is a duty-holder with an open action, the clock drops to 1× until they act or 60 real seconds pass.

### Scenario file format

Scenarios are YAML files in `scenarios/`. On load, a validator rejects any file where a generated reading contradicts a published point or crosses a threshold the record says was not crossed.

```yaml
scenario: aug26-as-happened
version: 3
corridor: bhote-trishuli-narayani
configVersion: 12
start: 2026-08-26T08:00:00+05:45
end: 2026-08-26T16:30:00+05:45
seed: 42

variants:                       # judge-toggleable
  syaphrubesiLastReading: "08:40"   # sources disagree: "08:40" or "08:50"
  malekhuCrossing: "11:20"          # sources disagree: "11:20" or "11:43"
  graceMinutes: 3                   # sensitivity: 3 or 10
  phoneCallAt0900: true             # false = counterfactual

speedProfile:
  - {from: "08:00", to: "08:36", speed: 60}
  - {from: "08:36", to: "09:05", speed: 2}     # the key half hour
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
    generator: {model: flat, level: 3.78, noiseSd: 0.01, tag: A}   # calm, consistent with the published 3.80
    pinned:
      - {t: "${variants.syaphrubesiLastReading}", level: 3.80, tag: P, src: spotlight}
    outage: {afterLastPinned: true, reason: DESTROYED, tag: P}
  BTW:
    generator: {model: flat, level: 3.52, noiseSd: 0.01, tag: A}   # calm, consistent with the published 3.55
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

The seismic event's place and magnitude come from the assessment and the USGS report ([HiRISK](https://hirisk.org/wp-content/uploads/2026/08/RHA_NP3_RasuwaFlood.pdf), [Snow Lion](https://snowliontours.com/?p=108099)). Its 15-minute publication delay is an assumption, so FR-19 stays a Could.

### Filling the gaps between published points

Only a handful of readings were published. Between them, the simulator generates readings with three documented models, all tagged \[A\] on screen:

- **flat** — a monsoon baseline with a slow trend and 1 cm seeded noise.
- **hydrograph** — a rising limb shaped to hit the published points (Malekhu's \~4 m step; Devghat's \~2 m in 2 h to 6.57 m at 16:00), then exponential recession.
- **outage** — the last published reading, then nothing.

When DHM supplies the real 10-minute series, it replaces the generators. The scenario format is unchanged; pinned points simply cover every slot.

### Live injection controls

| Control | What it does | Rules exercised |
| --- | --- | --- |
| Destroy a gauge | Stops readings from the next slot | R3, R5 |
| Restore a gauge | Resumes readings | R11 |
| Set a level | Type a value or drag the level on a station tile | R1, R2 |
| Rain on or off | Sets catchment rain to 0 or 20 mm/h | R6 |
| Send a report | Record a voice note in the browser or type in any language; pick a trust level | Gemini pipeline, R7, R8 |
| Become a duty-holder | Scan a QR code, choose a site and role, then receive actions and acknowledge or ignore them | Dispatch, ack, escalation |
| Telemetry outage | Silences a whole telemetry group, including stations in other basins | R4 |
| Heightened watch | Declares a known upstream hazard | R10 |
| Variant toggles | Grace 3 or 10 min; Syaphrubesi 08:40 or 08:50; phone call on or off | Sensitivity |

Every injection is stored as an event with `source = LIVE_INJECTION` and appears in the replay report.

### Replay report

At the end of a run, FR-17 produces a report with one row per site. Expected result for the 26 August run with 3-minute grace \[D\]:

| Site | Actual arrival | First warning as happened | JalDrishti WARNING | JalDrishti lead before arrival |
| --- | --- | --- | --- | --- |
| Betrawati | 09:20–09:30 \[P\] | SMS sent 09:15; some received 09:42–10:10 | 08:53 | 27–37 min (17–27 if Syaphrubesi lasted to 08:50) |
| Malekhu | 11:10–11:43 \[P\] | 09:15 | 08:53 | 137–170 min |
| Mugling | \~13:00 \[P\] | 09:15 | 08:53 | about 4 h |

Below Betrawati the time gain is the same 22 minutes everywhere. There, the value is the acknowledged, site-specific actions rather than the clock. The report also lists every rule firing with its inputs and tags, the config and matrix versions, and each Gemini call with its guard result. It exports as JSON and PDF.

## 11. Scenario library

Seven scenarios cover the real event, the counterfactual, three ways to raise a false alarm, a second-wave watch and an open sandbox. Each one is a YAML file with pass criteria that run in CI.

| ID | Scenario | What it proves | Pass criteria | Data basis |
| --- | --- | --- | --- | --- |
| S1 | 26 Aug, as happened | Silence corroboration beats the phone call | WARNING ≤ 08:53 (≤ 09:03 variant); every \[P\] point reproduced | \[P\] points, \[A\] fill |
| S2 | 26 Aug, no phone call | The warning no longer depends on someone noticing | JalDrishti WARNING unchanged; the as-happened track has nothing until Malekhu, 11:20–11:43 | Counterfactual \[D\] |
| S3 | Gauge power failure | One silent gauge never becomes a public alert | 0 WARNINGs; only forecaster and gauge reader notified; WATCH closes | \[A\] |
| S4 | Telemetry outage | A carrier failure is not a flood | 0 WATCHes; one outage ticket | \[A\] |
| S5 | Monsoon rise | Rain floods follow the normal path without false NON\_RAIN tags | WATCH at 7.3 m; no WARNING below 9.0 m; no R6 | \[A\] |
| S6 | Second wave, heightened watch | A known blocked lake changes the rules | WARNING on a single silence of the top sensor | Context \[P\], data \[A\] |
| S7 | Judge sandbox | Anything a judge tries goes through the real path | No crash; every injection logged | Live |

### S1 — 26 August, as happened (variant: Syaphrubesi lost by 08:50, 3-min grace)

| Sim time | Input | Expected engine output |
| --- | --- | --- |
| 08:37 | Seismic event; feed publication at 08:52 \[A\] | Stored as a hint (FR-19 off by default) |
| 08:40 | RSW 1.62 m, SYP 3.80 m \[P\]; BTW \~3.5 m \[A\] | All stations OK |
| 08:50 | RSW and SYP readings never arrive \[P\] | Nothing yet (grace running) |
| 08:53 | Silence sweep | RSW and SYP SILENT → R5 → **WARNING**; arrival bands computed; actions dispatched; CAP (Exercise) sent |
| 08:54 | — | Messages delivered; acknowledgement board fills |
| 08:58–09:05 | Un-acknowledged actions | Escalation step 1 (compressed ladder for short deadlines) |
| 09:00 | DAO Dhunche report, OFFICIAL \[P fact, A wording\] | Gemini parse; attached as corroboration; level unchanged |
| 09:15 | As-happened marker | "Actual SMS send" shown on the comparison track |
| 09:20 | BTW 3.55 m \[P\] | OK (calm value) |
| 09:33 | Silence sweep | BTW SILENT → re-anchor; bands narrow for sites below BTW |
| 11:10–11:20 | MLK jumps \~4 m \[P\] | R2 and R6 (NON\_RAIN) added as evidence; MLK RED |
| \~13:00 | Mugling arrival marker \[P\] | Shown on the comparison track |
| 16:00 | DVG peaks at 6.57 m \[P\] | Stays below 7.3 m; evidence only |
| 16:30 | Forecaster stands down | All-clear CAP; STAND\_DOWN messages |

### S2 — 26 August, no phone call

Same file with `phoneCallAt0900: false`. The as-happened track is replaced by a counterfactual one: with no call, the first automated signal on the network was the Malekhu threshold at 11:20–11:43 \[D\]. The JalDrishti track does not move. This is the scenario that answers "what if the Betrawati observer had been away?"

### S3 — Gauge power failure

1. 14:00–17:00 on a calm monsoon day; all stations normal \[A\].
2. SYP loses power at 14:15 and misses its 14:20 slot.
3. 14:23: SYP SILENT → internal WATCH. The forecaster and the SYP gauge reader get a "look at the river" action.
4. 14:30: the gauge reader reports a normal river (pre-recorded, or a judge records it). R11 closes the WATCH and opens a maintenance ticket.
5. 15:10: SYP returns. Pass if no other duty-holder was notified.

### S4 — Telemetry outage

At 02:10, six of ten stations on one carrier stop reporting, including RSW, BTW and four stations in other basins \[A\]. R4 classifies it as TELEMETRY\_OUTAGE and opens a ticket. Pass if no WATCH or WARNING opens.

### S5 — Monsoon rise

Catchment rain of 35 mm in 3 h \[A\]. DVG rises about 0.15 m per 10 minutes to 7.45 m at 03:20 \[A\]. R1 opens a WATCH when DVG crosses 7.3 m, and WATCH actions go to duty-holders within 2 hours of arrival. Pass if there is no WARNING below 9.0 m and no NON\_RAIN tag.

### S6 — Second wave, heightened watch

On 30 August NDRRMA warned of another flood because the Lhende Khola was obstructed, and Galchhi ran 3 m above normal ([IANS](https://ianslive.in/nepal-issues-fresh-flood-warning-as-bhote-koshi-water-level-rises--20260830082421)) \[P\]. In the scenario, the forecaster declares heightened watch on a temporary sensor that replaces RSW \[A\]. At 10:20 it goes silent, and R10 raises a WARNING at the 10:23 sweep with no second station needed.

### S7 — Judge sandbox

A calm day at 1–10× with every live control enabled and no script. Pass if every injection flows through the real path, is logged, and nothing crashes.

## 12. Demo runbook

The demo makes one claim and proves it live: on the real 26 August record, JalDrishti warns without the phone call, and every warning becomes acknowledged action. It also shows the system staying quiet when it should. There are two formats: a 3-minute video and a 5–7 minute live session with judges taking part.

### 3-minute video script

| Time | On screen | Narration (key line) | Proof point |
| --- | --- | --- | --- |
| 0:00–0:20 | Title card: the verified timeline from Section 1 | "On 26 August the gauges died showing calm numbers. The first warning came by telephone." | Published facts only, with sources |
| 0:20–0:35 | Console with S1 loaded; provenance badges visible | "This replays the published record. Every value says whether it is published, derived or assumed." | \[P\]/\[D\]/\[A\] badges |
| 0:35–1:10 | Play: RSW and SYP tiles go dark at 08:50; WARNING at 08:53; open the "why" panel; the comparison track shows 08:53 against 09:15. Toggle S2: the as-happened track falls back to 11:20. | "Two gauges going silent in river order is itself the signal. We don't wait for a phone call." | R5 evidence trail with the order-consistency window |
| 1:10–1:45 | Acknowledgement board; a phone gets the Nepali message for a hydropower site; tap ack; another row goes red and escalates to a call | "A colour code isn't a command. Each person gets their action, their deadline, and we know who hasn't confirmed." | Ack board, escalation log |
| 1:45–2:15 | A teammate records a Nepali voice note; the parsed card appears with location SYB; a Hindi message passes the numeric guard | "Gemini listens to the people on the river and words the orders in their language, but never decides the alert or a number." | Parsed JSON, guard badge |
| 2:15–2:40 | S3: one gauge loses power; internal WATCH; the gauge reader reports normal; WATCH closes | "And when one gauge simply loses power, nobody downstream is woken up." | 0 public alerts |
| 2:40–3:00 | Architecture strip, the replay report, the pilot ask | "Built on Cloud Run, Pub/Sub, Cloud SQL and Gemini on Vertex AI. We want to pilot it with one hydropower operator on the Trishuli." | Replay report PDF |

### Live session with judges (5–7 min)

1. **Hand out roles.** Show a QR code. Each judge scans it, picks a role (hydropower safety officer, ward chair, traffic police) and gets a phone inbox.
2. **Run S1 from 08:36 at 2×.** At 08:53 the judges' phones receive their actions. The clock drops to 1× while they decide.
3. **Let one judge ignore the message.** The board shows the escalation firing.
4. **Ask a judge to speak a report** into the console mic in English, Hindi or Nepali, naming any place on the corridor. The card appears with the resolved site.
5. **Hand over the sandbox (S7).** Let a judge destroy any gauge. Show that one silence stays internal, and that two in river order raise a WARNING.
6. **Close on the replay report**, generated from what the judges just did.

### Pre-demo checklist

- [ ] Cloud Run services warm (min instances 1) and the S1 rehearsal passed within the last hour
- [ ] Gemini quota and region checked; the fallback path tested by revoking the key once
- [ ] Telegram bot reachable from the venue network; QR page loads on mobile data
- [ ] Phones charged, on a separate hotspot from the laptop
- [ ] Console in presentation mode (125% zoom, high contrast)
- [ ] Recorded video backup on the laptop and on a USB drive

### Fallbacks

| Failure | Fallback | What the judges see |
| --- | --- | --- |
| Gemini unavailable or slow | Reviewed templates; demo voice notes use cached parses keyed by audio hash | An "AI fallback" banner; nothing hidden |
| Venue Wi-Fi down | Local Docker Compose stack: Pub/Sub emulator, Postgres, both services; phones join the laptop hotspot | Same console, local URL |
| Telegram blocked | Web phone simulator through the QR code | Same inbox in a browser |
| Running over time | Jump to bookmarks: 08:50, 08:53, 09:15 | Skips the dull stretch |
| Projector too small | Presentation mode and the one-screen ack board | Larger text |

## 13. Architecture and Google Cloud deployment

JalDrishti is one hexagonal Spring Boot service plus a simulator, joined by Pub/Sub and backed by Cloud SQL. The trigger engine is pure Java in the domain module; Pub/Sub, Postgres, Gemini and the channels are adapters around it.

&#91;embedded content: Architecture · Google Cloud services in the demo deployment\]

In simulation, the simulator replaces the DHM adapter and `SimScheduler` replaces Cloud Scheduler; every box below Pub/Sub runs unchanged.

### Components

| Component | Google Cloud service | Responsibility |
| --- | --- | --- |
| `jaldrishti-core` | Cloud Run | REST and SSE API, Pub/Sub push handlers, trigger engine, arrival bands, duty matrix, dispatcher, ack webhooks, silence-sweep and escalation endpoints |
| `jaldrishti-sim` | Cloud Run | Scenario engine, `SimClock`, scenario validator, live-injection API; publishes to the same topics as the DHM adapter |
| `dhm-adapter` | Cloud Run job, every minute (after DHM grants access) | Polls DHM station data and publishes readings |
| Topics | Pub/Sub | `gauge-readings`, `field-reports`, `seismic-events`, `observations`, `incident-events`, `dispatch-requests`, `acks`, `clock-control`, each with a dead-letter topic |
| Store | Cloud SQL for PostgreSQL | Append-only event store and read projections; jOOQ for access, Flyway for migrations |
| Objects | Cloud Storage | Voice notes, scenario files, replay exports |
| AI | Vertex AI, Gemini | Report parsing, message localisation, incident assistant |
| Clock in live mode | Cloud Scheduler | Calls the silence-sweep and escalation endpoints every minute |
| Secrets | Secret Manager | Telegram token, SMS and voice provider keys |
| Console | Static Vue 3 build served by `jaldrishti-core` | Operator console and phone-simulator page |
| Observability | Cloud Logging, Monitoring, Trace | OpenTelemetry traces from reading to dispatched message |
| CI/CD | Cloud Build, Artifact Registry | Test, run the scenario suite, build the image, deploy |

### Key design decisions

1. **Pub/Sub, not Kafka.** About 50 readings an hour per corridor does not need a broker cluster. Pub/Sub is serverless and native to the judged platform. Ordering keys per station keep each station's readings in order.
2. **Silence needs a clock.** A consumer cannot react to a message that never arrives, so silence detection is a scheduled sweep. Cloud Scheduler drives it live; `SimScheduler` drives it in simulation.
3. **Event-sourced incidents.** Every state change is an immutable event; the console reads projections. This gives the audit trail (FR-15) and makes replays exact.
4. **One instance for the demo.** Run `jaldrishti-core` with min and max instances = 1. SSE connections and the simulated clock then live in one process, and clock state is saved in Postgres so a restart resumes. Section 18 lists what changes to scale out.
5. **SSE with resume.** Each event has a sequence id; the browser reconnects with `Last-Event-ID` to survive Cloud Run's request timeout and network blips.

### Maven modules (hexagonal)

| Module | Contents | Depends on |
| --- | --- | --- |
| `jd-domain` | Rules R1–R11, order-consistency test, arrival-time model, incident and action state machines; no framework code | nothing |
| `jd-application` | Use cases: ingest reading, sweep, ingest report, raise or update incident, dispatch, ack, stand down | `jd-domain` |
| `jd-adapter-pubsub` | Push endpoints and publishers | `jd-application` |
| `jd-adapter-rest` | REST controllers, SSE stream | `jd-application` |
| `jd-adapter-persistence` | jOOQ repositories, Flyway migrations, projections | `jd-application` |
| `jd-adapter-gemini` | Parse, localise and assistant clients; numeric guard | `jd-application` |
| `jd-adapter-channels` | Web simulator, Telegram, SMS, voice | `jd-application` |
| `jd-adapter-cap` | CAP 1.2 builder and gateway client | `jd-application` |
| `jd-sim` | Scenario loader and validator, generators, `SimClock`, injection API | `jd-application` |
| `jd-boot` | Spring Boot wiring, profiles `live` and `sim` | all adapters |
| `console` | Vue 3, TypeScript, Pinia, MapLibre GL | REST and SSE API |

### Versions

| Item | Choice | Note |
| --- | --- | --- |
| Java | 21 LTS | Spring Boot 4 requires 17 and recommends 21+ ([HeroDevs](https://www.herodevs.com/blog-posts/spring-boot-versions-eol-dates-and-latest-releases-april-2026)) |
| Spring Boot | Latest 4.1.x | All 3.x lines are out of open-source support as of June 2026 ([Dan Vega](https://www.danvega.dev/blog/2025/11/24/spring-boot-4-is-here)); 4.0 support ends December 2026 |
| Pub/Sub client | Plain `google-cloud-pubsub` | Use Spring Cloud GCP only if a release supports Boot 4.1; check before Sprint 1 |
| Gemini SDK | `com.google.genai:google-genai`, latest | See Section 7 |
| Model | `gemini-3.6-flash`, pinned in config | Confirm availability in the chosen region |
| Postgres | Cloud SQL for PostgreSQL 16 \[A\] | Any supported version works |
| Region | `asia-south1`, fallback `asia-southeast1` | Check Gemini availability in each |

Version numbers marked "latest" must be pinned in the POM before the demo build, and the build must be re-run with them. Nothing here has been compiled yet.

## 14. Data model, events and API

Every table and event carries a `runId`. Live data uses `runId = live`; each simulation gets its own, so replays never touch live state and can run side by side.

### Tables (PostgreSQL)

| Table | Key columns | Notes |
| --- | --- | --- |
| `corridor_config` | version, corridor\_id, params (jsonb), created\_at | Every Section 6 parameter; immutable once used |
| `station` | id, corridor\_id, name, river, river\_km, lat, lon, warning\_m, danger\_m, cadence\_min, telemetry\_group | Section 4 gauges |
| `site` | id, corridor\_id, name\_en, name\_ne, aliases (text\[\]), type, river\_km, lat, lon | Gazetteer |
| `reading` | PK (run\_id, station\_id, observed\_at); level\_m, rain\_mm, received\_at, source, tag | Idempotent insert on the key |
| `station_state` | (run\_id, station\_id); band, rate\_flag, silent\_since, outage\_ticket | Projection |
| `incident` | id, run\_id, corridor\_id, level, opened\_at, closed\_at, anchor\_station, config\_version, matrix\_version | Projection |
| `incident_event` | seq (bigserial), incident\_id, at, type, rule\_id, payload (jsonb) | Append-only source of truth |
| `field_report` | id, run\_id, received\_at, sender\_hash, trust, channel, audio\_uri, raw\_text, parsed (jsonb), status, model\_id | Status: PARSED, UNPARSED, REJECTED |
| `duty_holder` | id, actor\_type, site\_id, display\_name, phone\_enc, telegram\_chat\_id, language, deputy\_id | Phone encrypted at rest |
| `duty_matrix_version` | version, status, created\_by, approved\_by, approved\_at | Status: DRAFT, APPROVED, RETIRED |
| `duty_rule` | rule\_id, version, level, actor\_type, site\_scope (jsonb), action\_code, action\_text\_en, deadline\_expr, ack\_means, requires\_ack | Section 8 schema |
| `dispatched_action` | id, run\_id, incident\_id, duty\_holder\_id, action\_code, matrix\_version, deadline\_at, state, sent\_at, acked\_at, ack\_data (jsonb), escalation\_step | Section 9 lifecycle |
| `message` | id, dispatched\_action\_id, channel, language, text, guard\_result, fallback\_used, model\_id | One row per send attempt |
| `cap_message` | identifier, incident\_id, msg\_type, status, xml, sent\_at, gateway\_response | Exercise vs Actual recorded |
| `sim_run` | run\_id, scenario, variants (jsonb), seed, state, anchor\_sim, anchor\_wall, speed | Survives restarts |

### Pub/Sub events

Every message has the attributes `eventTime`, `runId` and `schemaVersion`.

| Topic | Ordering key | Payload |
| --- | --- | --- |
| `gauge-readings` | stationId | runId, stationId, observedAt, levelM, rainMm, source (DHM, SIM, INJECT), tag |
| `field-reports` | none | runId, reportId, receivedAt, channel, senderHash, trust, audioUri or text |
| `seismic-events` | none | runId, eventId, originTime, publishedAt, magnitude, lat, lon, source |
| `observations` | none | runId, reportId, parsed (Section 7 schema), modelId |
| `incident-events` | incidentId | runId, incidentId, seq, at, type (OPENED, LEVEL\_CHANGED, EVIDENCE\_ADDED, BANDS\_UPDATED, STOOD\_DOWN), ruleId, payload |
| `dispatch-requests` | dutyHolderId | runId, dispatchId, incidentId, dutyHolderId, actions\[\], language, channel |
| `acks` | dispatchId | runId, dispatchId, actionCode, at, via, data |
| `clock-control` | runId | runId, state, anchorSim, anchorWall, speed |

### REST and SSE API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/corridors/{id}` | Config, stations, sites |
| GET | `/api/v1/corridors/{id}/state?runId=` | Station tiles and the open incident |
| GET | `/api/v1/incidents/{id}` | Incident with evidence and bands |
| GET | `/api/v1/incidents/{id}/events/{seq}/why` | Rule, inputs, tags, config version for one decision |
| POST | `/api/v1/incidents/{id}/escalate`, `/cancel-watch`, `/stand-down` | Forecaster actions; body carries a reason |
| GET | `/api/v1/incidents/{id}/actions` | Acknowledgement board |
| POST | `/api/v1/reports` | Web field report: multipart audio or text |
| POST | `/api/v1/acks/{dispatchId}` | Ack from the phone simulator (signed link) |
| POST | `/api/v1/webhooks/telegram`, `/api/v1/webhooks/sms` | Inbound channel webhooks |
| POST | `/api/v1/assistant/ask` | Incident assistant (FR-18) |
| GET | `/api/v1/stream?runId=` | SSE: reading, station-state, incident, action, report, clock |
| POST | `/internal/sweep`, `/internal/escalate` | Called by Cloud Scheduler or `SimScheduler`; OIDC-authenticated |
| POST | `/api/v1/sim/runs` | Start a run: scenario, variants, seed → runId |
| POST | `/api/v1/sim/runs/{runId}/play`, `/pause`, `/step`, `/jump`, `/speed` | Clock control |
| POST | `/api/v1/sim/runs/{runId}/inject` | Live injection: DESTROY\_GAUGE, RESTORE\_GAUGE, SET\_LEVEL, RAIN, REPORT, TELEMETRY\_OUTAGE, HEIGHTENED\_WATCH |
| GET | `/api/v1/sim/runs/{runId}/report` | Replay report, JSON or `?format=pdf` |

### Access control

- The console sits behind Identity-Aware Proxy with team accounts \[A\].
- Phone-simulator and ack links are signed per recipient and expire after 24 h.
- `/internal/*` accepts only OIDC tokens from the Scheduler and simulator service accounts.

## 15. Non-functional requirements

Latency targets are set where they matter: after a sweep or a reading, not inside a broker. Readings arrive every 10 minutes, so millisecond stream latency adds nothing. All targets are design defaults \[A\] until measured.

| ID | Category | Requirement | How verified |
| --- | --- | --- | --- |
| NFR-01 | Decision latency | Station state updated ≤ 5 s (p95) after a reading arrives; WARNING committed ≤ 10 s after the sweep tick that justifies it | Trace spans in the scenario suite |
| NFR-02 | Dispatch latency | 95% of WARNING messages sent ≤ 60 s after the WARNING, Gemini localisation included; on a 10 s Gemini timeout, the template goes out | Audit-log timestamps |
| NFR-03 | Report parsing | A 30-second voice note parsed ≤ 15 s (p95) | Test-set timing |
| NFR-04 | Degraded operation | With Gemini unavailable, detection, dispatch (templates) and acknowledgement all still work | Fallback test with the key revoked |
| NFR-05 | Determinism | The same scenario, seed and config produce an identical `incident_event` sequence (message text excluded) | CI replay diff |
| NFR-06 | Explainability | Every level change can be explained from stored rule, inputs, tags and config version | FR-15 test on every S1 event |
| NFR-07 | Privacy | No names or phone numbers in prompts, logs or events; phones encrypted at rest; audio deleted after 30 days unless attached to an incident | Log scan in CI; retention job |
| NFR-08 | Security | `/internal/*` OIDC-only; signed recipient links; secrets in Secret Manager; one least-privilege service account per service | Security checklist review |
| NFR-09 | Accessibility | WCAG 2.2 AA contrast; every status shown by shape, label and colour; console usable at 1366×768; phone page usable at 360 px wide | Automated contrast check; manual pass |
| NFR-10 | Languages | Nepali, Hindi and English in messages and the phone page; Devanagari rendered with Noto Sans Devanagari | Visual check on Android and iOS |
| NFR-11 | Availability (after the hackathon) | 99.9% monthly for detection and dispatch; multi-instance, with the clock and SSE moved off the single instance | Cloud Monitoring SLO |
| NFR-12 | Observability | One trace id from reading to dispatched message; dashboards for sweep lag, dispatch latency, guard failures and unacknowledged actions | Cloud Trace and Monitoring |
| NFR-13 | Cold start | Core ready ≤ 10 s from cold; the demo runs warm (min instances 1) | Cloud Run metrics |
| NFR-14 | Cost | The simulator scales to zero; min instances 1 only during demo windows | Billing report |

## 16. Test and validation plan

The scenario files are the test suite. CI runs S1–S6 at full speed on every commit, so the demo cannot drift from what is tested. Two things need outside validation before any real use: real DHM data and a native-speaker evaluation of Gemini.

### Test layers

| Layer | What is tested | Tools | Gate |
| --- | --- | --- | --- |
| Domain unit | Every rule; order-consistency edge cases (same slot, reversed order, implied speed above 35 m/s); arrival interpolation; deadline expressions; ladder compression; numeric guard with Devanagari digits | JUnit 5 | Every commit |
| Property-based | For random silences in random order, R5 fires only when order-consistent | jqwik | Every commit |
| Scenario | S1–S6 pass criteria from Section 11, run with the clock in fast-forward (ticks as fast as possible, no real waiting) | Scenario runner on Testcontainers | Every commit |
| Golden replay | S1's `incident_event` sequence diffed against a stored golden file (NFR-05) | Scenario runner | Every commit |
| Contract | Pub/Sub payloads against JSON Schema; CAP XML against the OASIS CAP 1.2 schema | JSON Schema validator, XSD | Every commit |
| Integration | Postgres, Pub/Sub push, channel adapters | Testcontainers, Pub/Sub emulator | Every merge |
| End-to-end | Console runs S1; the WARNING tile appears at 08:53; a phone-page ack updates the board | Playwright | Nightly and before demos |
| Gemini evaluation | Parsing and localisation quality (below) | Eval harness | Nightly |
| Load | 10 corridors × 10 stations at 1 reading/min (10× real cadence); a 500-recipient WARNING dispatched ≤ 60 s | k6 or Gatling | Before the finale |
| Resilience | Kill the core instance mid-run: it resumes from `sim_run` and SSE reconnects. Inject Gemini timeouts: templates go out | Manual and scripted | Before the finale |

### Gemini evaluation set

1. **Build it with a native speaker.** 30 reports \[A\]: 10 Nepali, 10 Hindi, 10 English or mixed, half as recorded voice notes.
2. **Include hard cases.** A place off the corridor, a normal-river report, noisy audio, two places in one message, a number of people, and a report about yesterday.
3. **Targets \[A\]:** location correct ≥ 90%; hazard type correct ≥ 90%; a NO\_HAZARD report never classified as DEBRIS\_FLOOD.
4. **Localisation.** 50 payloads × 3 languages. Guard pass rate ≥ 95%, and a native speaker reviews 10 per language for meaning.

### Validation with real data

- [ ] Request DHM's 10-minute series for the corridor stations on 26 August 2026 and 8 July 2025 through the data-request form on [dhm.gov.np](https://dhm.gov.np/hydrology/hms-Single/4636).
- [ ] Replace the generators with real readings, and recompute the travel-time curve and rate thresholds.
- [ ] Test the 26 August curve out-of-sample on 8 July 2025, and report the result honestly even if bands widen.
- [ ] Measure the real transmission delay per station to set the grace parameter.
- [ ] Ask a DHM or ICIMOD hydrologist to review the rules and parameters in Section 6.

## 17. Delivery plan and team split

Four sprints get a deployed, tested prototype to submission by Nov 5, 2026, then four weeks harden it for the finale on Dec 4, 2026 ([Opportunities Radar](https://opportunitiesradar.com/google-ai-builder-cup-2026/)). The prototype deadline is not confirmed: a third-party listing gives the evaluation phase as 5 Oct – 6 Nov ([Scholarship Union](https://scholarshipunion.com/ai-builder-cup-scholarship-2026/)). Check the Hack2skill dashboard today, and if the deadline is earlier, apply the cut list below.

&#91;embedded content: Delivery plan · dates from the sprint table; submission date assumed\]

If the confirmed deadline falls before 5 November, Sprint 3 moves up and the cut list below applies.

### Sprint contents

| Sprint | Dates | Deliverables | Done when |
| --- | --- | --- | --- |
| 0 — Decisions and data | 17–20 Oct | Repo, CI, module skeleton; gazetteer river-km from OSM or HydroSHEDS; DHM data request sent; Gemini model and region confirmed; submission rules confirmed | CI green on an empty build; gazetteer file committed |
| 1 — Detection core | 21–27 Oct | Rules R1–R11, order-consistency test, arrival model; `SimClock` and scenario loader; Pub/Sub and Postgres adapters; console station strip over SSE | S1 and S3 pass in CI |
| 2 — Action loop and Gemini | 28 Oct – 2 Nov | Duty matrix, dispatcher, phone simulator, ack and escalation; report parsing, localisation, numeric guard; CAP builder; Telegram | S1–S6 pass; a phone receives and acknowledges an action |
| 3 — Ship | 3–5 Nov | Replay report; Cloud Run deployment; 3-minute video; submission | Submitted; public demo URL works from a phone on mobile data |
| Finale prep | 7 Nov – 3 Dec | Gemini evaluation set with native speakers; DHM data if received; load and resilience tests; hydrologist review; three full live-session rehearsals | Section 12 checklist passes twice in a row |

### Team split (2–4 people)

| Role | Owns | If the team has 3 |
| --- | --- | --- |
| A — Backend lead | `jd-domain`, `jd-application`, `jd-sim`; rules, arrival model, scenario engine | — |
| B — Backend | Persistence, Pub/Sub, dispatcher, channels, CAP, deployment | Also takes role D's pipeline code |
| C — Frontend | Vue console, ack board, comparison track, phone page, presentation mode | — |
| D — AI and data | Gemini prompts and schemas, eval set, gazetteer, scenario YAML, the replay narrative | Role A takes the scenario YAML |

### Cut list if time runs short

Cut from the top; never cut the last group.

1. **Cut first:** seismic hint (FR-19), incident assistant (FR-18), voice-call escalation, SMS channel.
2. **Cut next:** Telegram (keep the web phone simulator), heightened watch (FR-16, scenario S6), telemetry-outage scenario S4.
3. **Never cut:** silence and cascade rules (R3, R5), the simulator with provenance badges, the ack board, the numeric guard and the S1 and S3 scenarios.

## 18. Risks, limitations and ethics

The biggest risk to the pitch is overclaiming; the biggest risk to a real deployment is false alarms. Both are designed for explicitly below.

### Risks

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| No DHM data before the demo, so most readings are generated | High | Medium | Provenance badges on every value; pinned \[P\] points; the request goes out in Sprint 0; say it out loud in the pitch |
| Arrival bands calibrated on the event they are tested on | Certain | Medium | Labelled "calibrated on 26 August"; out-of-sample test on 8 July 2025 once data arrives |
| False alarms erode trust | Medium | High | One silence stays internal; telemetry-outage filter; S3 and S4 in CI; track precision in any pilot |
| Auto-issued public warnings need legal authority | High | High | Pilot setting: the forecaster has 2 minutes to cancel before the CAP message goes public; duty-holder actions go out at once \[A\] |
| Gemini misplaces a report | Medium | Medium | Gemini never sets a level; gazetteer validation; confidence floor; trust levels; R8 needs two observers |
| Wrong or unnatural Nepali or Hindi wording | Medium | Medium | Numeric guard; native-reviewed templates; eval set |
| Model or SDK version changes before the finale | Medium | Low | Pin versions; contract tests; fallback templates |
| Single-instance demo deployment | Certain | Low for demo, high for production | Scale-out plan below |

### Limitations to state plainly

- **The first kilometres.** The assessment judged that in-channel warning at Rasuwagadhi would have been minimal ([HiRISK](https://hirisk.org/wp-content/uploads/2026/08/RHA_NP3_RasuwaFlood.pdf)). JalDrishti does not change that; only upstream data from Tibet or seismic detection could.
- **Fixed reporting intervals.** With 10-minute reporting, detection cannot beat one interval plus grace. A sensor installer recommends event-triggered transmission when levels jump ([LinkedIn](https://np.linkedin.com/in/nawa0606)); that is a hardware change the roadmap supports.
- **Downstream gain is modest in time.** Below Betrawati the saving is about 22 minutes. The larger value there is acknowledged action, which the pilot must measure.
- **People without phones.** Community networks saved many lives on 26 August ([Kathmandu Post](https://kathmandupost.com/editorial/2026/09/03/nepal-needs-to-overhaul-its-early-warning-system)). Volunteers and sirens are duty-holders too; JalDrishti adds to these channels, not replaces them.

### Scale-out plan (after the hackathon)

1. Make the core stateless: SSE fan-out through a per-instance Pub/Sub subscription to `incident-events`.
2. Make the sweep and escalation ladder idempotent per (runId, tick), so duplicate Scheduler calls are harmless.
3. Run the simulator's clock in `jaldrishti-sim` only; the core reads clock state from `clock-control`.
4. Run two regions, with Postgres high availability.

### Ethics

- **Respect the event.** Over 1,100 people died. The demo uses no casualty imagery and never claims JalDrishti "would have saved" a number of lives.
- **Honesty over drama.** Every number on screen is tagged. The limitations above go in the pitch, not only in this document.
- **Data minimisation.** No telecom, location-tracking or vehicle data. Duty-holder phone numbers are the only personal data, encrypted and never sent to a model.
- **Human authority.** The system recommends and dispatches agreed actions; authorities own the duty matrix, and the forecaster can always stand down.

## 19. Roadmap after the hackathon

The next step is a shadow-mode pilot with one hydropower operator on the Trishuli, using real DHM data. It measures false-alarm rate, lead time and acknowledgement rate before any public alert is automated.

1. **Shadow pilot, 3 months.** Read-only DHM feed; one operator's control room and safety officers as duty-holders; no public CAP. Measure precision of WATCH and WARNING, lead time against the next real event, and median time to acknowledge.
2. **Agreed duty matrix.** Work with DHM, NDRRMA and the district committees in Rasuwa, Nuwakot, Dhading and Chitwan to replace the draft matrix with an agreed one. This is the national protocol the post-event analysis calls for.
3. **Sensor improvements.** Event-triggered transmission when levels jump, sensors at more than one elevation, and satellite backhaul. Nepal already plans VSAT-linked monitoring at the border ([Irish News / Reuters](https://www.irishnews.com/news/world/nepal-to-rebuild-early-warning-system-on-china-border-after-deadly-flood-sources-say-7FFQVECQVJB7TBIN5H3UCKMZBQ/)).
4. **Upstream signals.** Seismic detection of mass movements, which the rapid assessment suggests could support warnings at scale, and cross-border feeds when agreements allow.
5. **More corridors.** The same configuration model can cover other Himalayan valleys with run-of-river plants and glacial hazards, in Nepal and in India (for example the Teesta and the upper Ganga tributaries).
6. **Business model.** Per-plant subscription for hydropower operators, who carry the crew risk and the asset loss. A government licence for DHM and NDRRMA. The duty-matrix format can be published as an open standard.

## Sources

Three sources were read in full and anchor the timeline; the rest were read as search excerpts. Before quoting any excerpt-only figure on a slide, open the page and check it.

| Source | Used for | Read |
| --- | --- | --- |
| [Nepali Times — Nepal disaster was a warning to fix its early-warning system](https://nepalitimes.com/nepal-disaster-was-a-warning-to-fix-its-early-warning-system) | Gauge readings, phone call, SMS, Code Orange, recommendations | Full |
| [ScienceBlog — the last river gauge on the Narayani](https://scienceblog.com/?p=599238) | Thresholds, Devghat levels, SMS split, death toll, Malekhu times | Full |
| [HiRISK rapid hazard assessment NP3](https://hirisk.org/wp-content/uploads/2026/08/RHA_NP3_RasuwaFlood.pdf) | Source mechanism, 08:37/08:44 times, Mugling arrival, impacts, warning-time verdict, seismic signal | Full |
| [Spotlight Nepal — The warning that came by telephone](https://www.spotlightnepal.com/2026/08/28/bhotekoshi-flood-warning-came-telephone/) | Warning levels, Syaphrubesi and Betrawati times, Phalakhu Khola | Excerpt |
| [OnlineKhabar — Rasuwa flood raises questions over early warning](https://english.onlinekhabar.com/rasuwa-flood-early-warning-system.html) | 08:50 missed reading; Betrawati distance | Excerpt |
| [Sensor installer's post (LinkedIn)](https://np.linkedin.com/in/nawa0606) | 10-min reporting; Betrawati 09:20/09:30; 16.6 m/s; event-triggered transmission | Excerpt |
| [eKantipur — SMS reached citizens after the disaster](https://ekantipur.com/video/2026/09/13/en/early-warning-system-in-nepal-sms-reaches-citizens-mobile-phones-after-a-disaster-54-35.html) | SMS receipt times 09:42 and 10:10 | Excerpt |
| [Ratopati — Trishuli exceeds danger mark](https://english.ratopati.com/story/76390/floods-in-trishuli-exceed-danger-level) | Malekhu (Furke) danger-level crossing | Excerpt |
| [IANS — fresh flood warning, 30 August](https://ianslive.in/nepal-issues-fresh-flood-warning-as-bhote-koshi-water-level-rises--20260830082421) | Galchhi above 14 m; second-wave warning | Excerpt |
| [Copernicus GFM — Floods in Nepal and Tibet](https://global-flood.emergency.copernicus.eu/news/259-floods-in-nepal-and-tibet-august-2026/) | Syaphrubesi damage; communications damage | Excerpt |
| [SANDRP — 28 August 2026](https://sandrp.in/2026/08/28/) | Hydropower damage | Excerpt |
| [ShareSansar — Trishuli Bazaar houses swept away](https://www.sharesansar.com/newsdetail/flood-from-rasuwa-sweeps-away-around-60-houses-in-nuwakots-trishuli-bazaar-damage-to-hydroelectric-power-2026-08-26) | Trishuli Bazaar, Devighat and Trishuli plants | Excerpt |
| [Smart Water Magazine](https://smartwatermagazine.com/news/smart-water-magazine/at-least-98-dead-and-403-unaccounted-for-as-a-flash-flood-from-tibet-sweeps-nepals-bhote-koshi-valley-developing) | No rain that morning; hydropower workers missing | Excerpt |
| [Snow Lion Tours](https://snowliontours.com/?p=108099) | USGS M4.4 classification | Excerpt |
| [GlobalGiving situation report](https://cl.globalgiving.org/pfil/78980/projdoc.pdf) | Timure | Excerpt |
| [Kathmandu Post editorial](https://kathmandupost.com/editorial/2026/09/03/nepal-needs-to-overhaul-its-early-warning-system) | Community networks | Excerpt |
| [Irish News / Reuters](https://www.irishnews.com/news/world/nepal-to-rebuild-early-warning-system-on-china-border-after-deadly-flood-sources-say-7FFQVECQVJB7TBIN5H3UCKMZBQ/) | VSAT border monitoring plan | Excerpt |
| [DHM station page](https://dhm.gov.np/hydrology/hms-Single/4636) | Data-request form | Excerpt |
| [Opportunities Radar](https://opportunitiesradar.com/google-ai-builder-cup-2026/), [Scholarship Union](https://scholarshipunion.com/ai-builder-cup-scholarship-2026/) | Finale date; evaluation window (third-party) | Excerpt |
| [Maxim model library — gemini-3.6-flash](https://www.getmaxim.ai/bifrost/model-library/compare/vertex_ai/gemini-3.6-flash) | Model id, audio input, structured output (third-party) | Excerpt |
| [Google Cloud — Gen AI libraries](https://docs.cloud.google.com/vertex-ai/generative-ai/docs/start/libraries) | Java SDK artifact | Excerpt |
| [HeroDevs](https://www.herodevs.com/blog-posts/spring-boot-versions-eol-dates-and-latest-releases-april-2026), [Dan Vega](https://www.danvega.dev/blog/2025/11/24/spring-boot-4-is-here) | Spring Boot versions and support | Excerpt |
