# JalDrishti v3 — 3-Minute Competition Video Script & Demo Runbook

> **Competition:** Google Cloud AI Builder Cup 2026  
> **Track:** Sustainability & Social Impact  
> **Corridor:** Lende Khola → Bhote Koshi → Trishuli → Narayani (Rasuwagadhi to Devghat), Nepal  
> **Target Duration:** Exactly 180 seconds (3:00)

---

## Storyline Overview

The pitch proves one critical breakthrough:  
**On the real 26 August 2026 disaster record, JalDrishti issues an automated corridor WARNING at 08:53:00 NPT — 22 minutes ahead of the manual telephone call and actual SMS — using only the physical order-consistency of silent gauges, and turns that warning into acknowledged, site-specific duty actions.**

---

## 3-Minute Second-by-Second Video Script

| Time | On-Screen Video | Spoken Narration (Clear, measured pace) | Technical Proof Point |
|---|---|---|---|
| **0:00–0:20**<br>(20s) | **Title Slide:** Dark satellite map of the Bhote Koshi gorge with timeline markers.<br>Pan to news clippings: *"Over 1,100 lives lost... gauges died showing calm water... the warning came by telephone at 09:15."* | *"On the morning of 26 August 2026, a sudden glacial debris flood swept down Nepal’s Bhote Koshi. Upstream telemetry gauges stopped reporting while showing normal water levels. The first warning didn't come from automated software — it came by a manual phone call 25 minutes later. Over 1,100 people died."* | Verified published facts from DHM, HiRISK, and Nepali Times. |
| **0:20–0:35**<br>(15s) | **JalDrishti Operator Console:** Live dashboard showing the 6 gauge tiles (RSW → SYP → BTW → GLC → MLK → DVG) with green `[P]`, `[D]`, and `[A]` provenance badges clearly visible. | *"This is JalDrishti. Built on Google Cloud and Vertex AI, it replays the verified record. Every single number on screen carries an explicit provenance tag: Published, Derived, or Assumption."* | Provenance tags ($[P]$, $[D]$, $[A]$) eliminate AI hallucination and overclaiming. |
| **0:35–1:10**<br>(35s) | **Simulation Play:**<br>• At 08:40, gauges show calm readings.<br>• At 08:50, RSW and SYP go dark.<br>• At 08:53, banner flashes **CORRIDOR WARNING**.<br>• Click **🔍 Why did this fire?** — `WhyModal` pops up displaying the physical formula $[08:47, 10:13\text{ NPT}]$.<br>• Highlight Comparison Track: **08:53 vs 09:15**. | *"At 08:50, Rasuwagadhi and Syaphrubesi go dark. At 08:53, our silence sweep evaluates Rule R5. We don't wait for phone calls. Two gauges going silent in upstream-to-downstream river order at flood wave velocities is itself the mathematical proof. JalDrishti declares a corridor WARNING 22 minutes ahead of the real SMS."* | Rule R5 Order-Consistency window ($[v_{\text{min}}=3\text{ m/s}, v_{\text{max}}=35\text{ m/s}]$) proves 22 minutes saved. |
| **1:10–1:45**<br>(35s) | **Split Screen (Console + Phone Simulator):**<br>• Console shows **Acknowledgement Board**.<br>• Mobile simulator (`/phone`) receives push alert in Nepali for Chilime Hydropower.<br>• Operator taps **"Confirm Action"**.<br>• Another row (Mailung HEP) stays unacknowledged; timer ticks down, pill turns amber, and auto-escalation voice call fires. | *"A colour-coded alert isn't a command. JalDrishti evaluates a versioned duty matrix. Hydropower operators, district magistrates, and schools get site-specific orders and exact deadlines based on flood arrival waves. When Chilime confirms, the board updates instantly. When another ignores it, our automated escalation ladder triggers a voice call."* | Action Loop, HMAC 1-tap ack, dynamic escalation ladder compression. |
| **1:45–2:15**<br>(30s) | **Field Report & Gemini Integration:**<br>• Teammate records Nepali voice report: *"म स्याफ्रुबेसीबाट बोल्दैछु। भोटेकोसीमा ठूलो बाढी आयो!"*<br>• Card appears in Report Feed: Gemini extracts location `SYP` and hazard `DEBRIS_FLOOD`.<br>• Show `NumericGuard` badge: green checkmark confirming zero numeric drift. | *"Gemini 3.6 Flash listens to eyewitness voice notes in Nepali and Hindi, grounding reports against a river gazetteer. But Gemini never sets an alert level or invents a number: our strict Numeric Guard validates every token against verified physics."* | Vertex AI `gemini-3.6-flash`, temperature 0, structured JSON schema, `NumericGuard`. |
| **2:15–2:40**<br>(25s) | **False Alarm Resilience (Scenario S3):**<br>• Trigger S3: Syaphrubesi loses power on a calm day.<br>• Watch opens internally only.<br>• Gauge reader inspects river and reports normal.<br>• Watch closes with zero downstream panic. | *"What happens when a gauge simply loses power? JalDrishti opens an internal watch, dispatches a gauge reader to verify, and stays quiet. Nobody downstream is woken up."* | Rule R3/R11 false alarm suppression; prevents warning fatigue. |
| **2:40–3:00**<br>(20s) | **Closing Architecture & Replay Export:**<br>• Click **📑 Replay Report** showing cryptographic SHA-256 event hash.<br>• Architecture graphic: Cloud Run, Pub/Sub, Cloud SQL, Vertex AI.<br>• Final slide: Call for pilot with Trishuli hydropower plants. | *"Engineered with hexagonal Java 21 on Google Cloud Run, Pub/Sub, and Cloud SQL. Every run produces a deterministic, cryptographically hashed audit report. JalDrishti is ready to shadow-pilot with hydropower operators on the Trishuli river."* | Deterministic replay (NFR-05), Cloud native serverless architecture. |

---

## Emergency Contingency & Fallback Protocol

If anything goes wrong during a live demonstration or recording:

1. **Gemini Latency or Quota Exhaustion:**  
   The system automatically falls back to `ReviewedTemplateEngine`. The UI displays `[D] Template Fallback` without crashing or blocking.
2. **Network Interruption:**  
   The application is fully containerized via `docker-compose.yml` and can run 100% offline using the embedded Pub/Sub emulator and local PostgreSQL.
3. **Running Over Time:**  
   Use the speed selector ($1\times, 2\times, 10\times, 60\times$) in the header or jump directly to bookmarks `08:50` and `08:53`.
