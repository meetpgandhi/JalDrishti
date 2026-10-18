# JalDrishti v3 — Flash-Flood Warning-to-Action System

**Google Cloud AI Builder Cup 2026 — Official Submission**  
**Category:** Sustainability & Social Impact  
**Pilot Corridor:** Lende Khola → Bhote Koshi → Trishuli → Narayani (Rasuwagadhi to Devghat), Nepal

---

## 1. Executive Summary

Flash floods in steep Himalayan valleys strike with catastrophic speed. On **26 August 2026**, a glacial debris flow destroyed upstream river gauges in northern Nepal while they were transmitting calm water levels. The downstream telemetry simply stopped. Because traditional warning systems wait for water level thresholds to be exceeded or rely on manual telephone chains, **the first warning was sent 25 minutes after gauge destruction** — after the flood wave had already reached populated communities downstream. Over 1,100 lives were lost.

**JalDrishti v3** solves two systemic failures:
1. **The Silence Paradox:** Upstream telemetry gauges die *before* recording a flood peak. JalDrishti’s trigger engine models the river’s physical hydraulics ($3.0\text{ to }35.0\text{ m/s}$ wave speeds). When upstream and downstream gauges go silent in physical river order (Rule R5), JalDrishti automatically declares a **Corridor WARNING 22 minutes ahead of manual telephone reporting**.
2. **The "Color Code" Dilemma:** Generic broadcasts ("Red Alert") lead to confusion and inaction. JalDrishti pairs detection with an automated **Duty Matrix Action Loop**: translating flood arrival windows into site-specific, deadline-driven commands for hydropower dam operators, school principals, traffic police, and district magistrates, tracked by a real-time Acknowledgement Board and automated escalation ladder.

---

## 2. Technical Architecture & Google Cloud Integration

JalDrishti is designed as a **Hexagonal Multi-Module System (Ports & Adapters)** in pure Java 21, packaged in a minimal container on Google Cloud Run:

```
                      ┌────────────────────────────────────────┐
                      │        Cloud Scheduler (1 min)         │
                      └──────────────────┬─────────────────────┘
                                         │ /internal/sweep
                                         ▼
┌──────────────────┐           ┌──────────────────┐           ┌──────────────────┐
│  DHM Telemetry   │──────────▶│  Cloud Pub/Sub   │──────────▶│  jaldrishti-core │
│  or Sim Engine   │           │   (8 Topics)     │           │   (Cloud Run)    │
└──────────────────┘           └──────────────────┘           └─────────┬────────┘
                                                                        │
                 ┌──────────────────────────────────────────────────────┴───────────────┐
                 │                                                                      │
                 ▼                                                                      ▼
    ┌─────────────────────────┐                                            ┌─────────────────────────┐
    │   Cloud SQL (Postgres)  │                                            │  Vertex AI (Gemini)     │
    │  Append-Only Event Store│                                            │  gemini-3.6-flash       │
    │  Immutable Audit Log    │                                            │  Temperature: 0         │
    │  Deterministic SHA-256  │                                            │  Strict NumericGuard    │
    └─────────────────────────┘                                            └─────────────────────────┘
```

### Google Cloud Components Used:
- **Google Cloud Run:** Hosts the core Spring Boot application and embedded Vue 3 console with sub-second scaling.
- **Google Cloud Pub/Sub:** Manages event-driven asynchronous communication across 8 partitioned topics (`gauge-readings`, `field-reports`, `incident-events`, `dispatch-requests`, `acks`, etc.) with dedicated dead-letter queues.
- **Cloud SQL for PostgreSQL 16:** Hosts the append-only `incident_event` table, ensuring complete event sourcing and cryptographic auditability.
- **Vertex AI (`gemini-3.6-flash`, `asia-south1`):** Ingests multi-lingual eyewitness voice notes/text, normalises them to a corridor gazetteer, and localises emergency directives into Nepali and Hindi.
- **Cloud Build & Artifact Registry:** Continuous integration and deployment pipeline ensuring automated, secure image distribution.

---

## 3. Responsible AI & Strict Guardrails

Disaster early warning systems cannot tolerate AI hallucinations or nondeterminism. JalDrishti enforces strict architectural rules:

1. **Gemini NEVER Determines Alert Levels:**  
   Alert levels (`NORMAL`, `WATCH`, `WARNING`, `STAND_DOWN`) are governed 100% by deterministic Java 21 domain rules (Rules R1–R11). Gemini is used exclusively for report parsing and language localisation.
2. **The NumericGuard Safety Net:**  
   Every localised message produced by Gemini passes through a strict bidirectional token guard. It transliterates Devanagari numerals (०–९ to 0–9) and asserts that every flood arrival timestamp, evacuation deadline, and action code matches the verified payload exactly. Any mismatch triggers an immediate, silent fallback to reviewed, native-authored message templates.
3. **Deterministic Cryptographic Verification (FR-17):**  
   Every simulation run hashes its immutable event stream into a SHA-256 digest, guaranteeing that identical scenario inputs produce bit-for-bit identical decision timelines.
4. **Transparent Provenance Badging:**  
   Every metric and event displayed in the operator console carries an explicit provenance tag:
   - **`[P]` Published:** Verified ground truth from official post-disaster records.
   - **`[D]` Derived:** Exact mathematical calculation based on calibrated physical wave models.
   - **`[A]` Assumption:** Design assumptions, clearly identified for transparent sensitivity testing.

---

## 4. Demonstrable Impact & 26 August Proof

On the verified 26 August 2026 event record:
- **08:40 NPT:** Rasuwagadhi (RSW) transmits last good reading (1.62m).
- **08:50 NPT:** Syaphrubesi (SYP, 15 km downstream) and RSW go silent.
- **08:53 NPT:** JalDrishti's silence sweep computes the order-consistency arrival window $[08:47:08, 10:13:20\text{ NPT}]$. Because gauge loss overlaps this window, Rule R5 fires **Corridor WARNING**.
- **08:54 NPT:** Chilime and Mailung hydropower operators receive site-specific evacuation and gate bypass directives.
- **09:15 NPT:** Actual historical SMS was sent by authorities.
- **Outcome:** **22 MINUTES OF PRE-IMPACT LEAD TIME SAVED** without waiting for telephone calls or human confirmation.

---

## 5. Pilot Roadmap & Next Steps

1. **Shadow Mode Pilot (3 Months):** Partner with one run-of-river hydropower operator on the Trishuli river (e.g. Chilime or Mailung HEP) running in shadow mode with real-time DHM feeds to validate false-alarm rejection.
2. **National Protocol Standard:** Present the versioned Duty Matrix format to Nepal's National Disaster Risk Reduction and Management Authority (NDRRMA) as an open standard for multi-hazard warning-to-action automation.
3. **Multi-Basin Scale-Out:** Extend the corridor configuration model to other high-risk Himalayan valleys in Nepal and India (e.g., Koshi, Gandaki, Teesta).
