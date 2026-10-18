# JalDrishti v3 — Pre-Demo & Operational Readiness Checklist

> **Purpose:** Step-by-step verification protocol to run before any live judging session or video demonstration.  
> **Reference:** Section 12 of the Canonical Specification.

---

## 1. Cloud & Infrastructure Verification

- [ ] **Cloud Run Instances Kept Warm:**  
  Verify `min-instances: 1` on `jaldrishti-core` so judges experience zero cold-start delay (< 200ms response).
  ```bash
  gcloud run services describe jaldrishti-core --region=asia-south1 --format="value(spec.template.spec.containers[0].resources.limits)"
  ```

- [ ] **Pub/Sub Topics & Subscriptions Active:**  
  Verify all 8 main topics and dead-letter queues are active:
  ```bash
  gcloud pubsub topics list --filter="name:jaldrishti"
  ```

- [ ] **Vertex AI Region & Model Quota Checked:**  
  Confirm `gemini-3.6-flash` is available in `asia-south1` with active project quota.

- [ ] **Database Migration State:**  
  Verify PostgreSQL schema is at migration `V2__seed_duty_matrix_and_holders.sql`.

---

## 2. Fallback Path Stress Test

- [ ] **Offline Fallback Validation:**  
  Simulate a Vertex AI disconnection (set `JALDRISHTI_GEMINI_VERTEX_ENABLED=false` or pass empty credentials) and verify:
  1. Incident Assistant answers with offline hydrological knowledge.
  2. Directives fall back seamlessly to `ReviewedTemplateEngine`.
  3. No unhandled 500 errors in browser console or logs.

- [ ] **Local Offline Stack Validation:**  
  Verify Docker Compose local stack can start cleanly if venue Wi-Fi fails:
  ```bash
  docker compose up -d
  ```

---

## 3. Demo Data & Scenario Reset

- [ ] **S1 Scenario Warmup Run:**  
  Execute `scripts/run-e2e-demo.sh` to ensure the full decision sequence finishes in under 15 seconds.

- [ ] **State Reset Verification:**  
  Verify starting a new simulation run (`POST /api/v1/sim/runs`) generates a fresh UUID `runId` and cleanly isolates previous event logs.

- [ ] **Replay Hash Determinism:**  
  Confirm `GET /api/v1/sim/runs/{runId}/report` returns the SHA-256 event stream hash and 22-min lead time badge.

---

## 4. UI Presentation & Audience Devices

- [ ] **Browser Display Mode:**  
  Set operator console zoom to 110%–125% for high-definition projector and screen recording clarity.

- [ ] **Provenance Badges Visible:**  
  Confirm green `[P]`, blue `[D]`, and amber `[A]` badges are crisp and legible on all station tiles and arrival bands.

- [ ] **Mobile Duty Phone Simulator (`/phone`):**  
  Open `/phone` on a separate smartphone or browser tab and verify 1-tap HMAC confirmation updates the Acknowledgement Board in real time.

- [ ] **Local Video Backup:**  
  Store a pre-recorded full rehearsal MP4 file locally on the laptop and on a backup USB drive.
