# JalDrishti — Architecture Decision Records (ADR)

> One record per decision. Never delete records — add a "Superseded by" note if reversed.  
> Format: Context → Decision → Consequences → Status

---

## ADR-001: Hexagonal Architecture with Maven Multi-Module

**Date:** 2026-10-16  
**Status:** ACCEPTED  

**Context:**  
The project needs a trigger engine testable without any cloud infrastructure, plus multiple adapters (Pub/Sub, PostgreSQL, Gemini, Telegram, SMS, CAP). The competition deadline is tight.

**Decision:**  
Use hexagonal (ports & adapters) architecture with one Maven module per layer. `jd-domain` has zero framework dependencies; adapters depend only on `jd-application` interfaces.

**Consequences:**
- (+) Domain rules R1–R11 testable with no Docker, no DB, no network
- (+) Any adapter can be swapped (e.g., SMS provider) without touching domain logic
- (+) CI can run domain tests in seconds as a fast first gate
- (-) More module files to manage
- (-) jOOQ codegen requires a running Postgres for generation phase

**Enforcement:** CI job `validate-pom` greps `jd-domain/src/main/java` for `springframework`, `javax.persistence`, `com.fasterxml.jackson` — fails build if found.

---

## ADR-002: SimClock as Injectable java.time.Clock

**Date:** 2026-10-16  
**Status:** ACCEPTED  

**Context:**  
The spec requires identical `incident_event` sequences for the same scenario + seed (NFR-05). If domain code calls `Instant.now()` directly, tests are non-deterministic.

**Decision:**  
All time-dependent code accepts a `java.time.Clock` bean injected at construction. In the `sim` profile, `SimClock` is the primary bean. In `live`, `Clock.systemDefaultZone()` is the primary bean.

**Consequences:**
- (+) Deterministic replays — same clock ticks produce identical rule evaluations
- (+) SimScheduler can advance the clock without sleeping real threads
- (+) Unit tests can pass any fixed-time `Clock`
- (-) Every class needing time must have `Clock` injected (discipline required)

**Enforcement:** Code review. Grep for `Instant.now()` or `LocalDateTime.now()` without `clock` in `jd-domain` — flag as a bug.

---

## ADR-003: One Code Path for Sim and Live

**Date:** 2026-10-16  
**Status:** ACCEPTED  

**Context:**  
A simulation-specific code path would hide bugs that only manifest in production. The competition judges expect to see a real system, not a demo-mode shortcut.

**Decision:**  
The simulator publishes to **the same Pub/Sub topics** as the live DHM adapter. Only the clock and data source change. `jd-boot` profiles (`sim` | `live`) swap infrastructure beans, not business logic.

**Consequences:**
- (+) Whatever the simulator tests, production does
- (+) Live injection (`DESTROY_GAUGE`, `REPORT`, etc.) exercises the exact same path a real signal would
- (-) The sim must handle Pub/Sub emulator latency (usually < 10ms — acceptable)

---

## ADR-004: jOOQ + Flyway (No JPA/Hibernate)

**Date:** 2026-10-16  
**Status:** ACCEPTED  

**Context:**  
The `incident_event` table must be append-only. JPA/Hibernate makes this hard to enforce and adds impedance mismatch. The schema is event-sourced and has PostgreSQL-specific types (JSONB, BIGSERIAL, TIMESTAMPTZ).

**Decision:**  
Use **jOOQ** for all SQL (type-safe, PostgreSQL-native). Use **Flyway** for all migrations. No JPA, no Hibernate, no Spring Data JPA.

**Consequences:**
- (+) `incident_event` append-only enforced at the repository layer — `JooqIncidentEventRepository` has only `append()`, no `update()`/`delete()`
- (+) JSONB payloads handled natively
- (+) jOOQ generates type-safe classes from the actual schema
- (-) jOOQ code generation requires a running database (use Testcontainers in codegen Maven plugin config)
- (-) More boilerplate than Spring Data JPA for simple CRUD

---

## ADR-005: Testcontainers (Not H2) for Integration Tests

**Date:** 2026-10-18  
**Status:** ACCEPTED  

**Context:**  
H2's PostgreSQL compatibility mode doesn't support JSONB, `gen_random_uuid()`, TIMESTAMPTZ arithmetic, or `ON CONFLICT DO NOTHING` with composite PKs correctly. jOOQ generates PostgreSQL-dialect SQL.

**Decision:**  
Replace H2 (which was in the initial `jd-adapter-persistence/pom.xml`) with `testcontainers:postgresql`. Integration tests spin up a real PostgreSQL 16 container.

**Consequences:**
- (+) Tests run against exactly the same database engine as production
- (+) No compatibility surprises with JSONB, UUID, or TIMESTAMPTZ
- (-) Integration tests take ~10s longer to start (container spin-up)
- (-) Docker must be available in CI (GitHub Actions services handle this)

---

## ADR-006: Gemini is Parser and Localiser — Never a Rule Engine

**Date:** 2026-10-16  
**Status:** ACCEPTED — NON-NEGOTIABLE  

**Context:**  
The competition asks for meaningful Gemini integration. However, Gemini is probabilistic and cannot guarantee the deterministic, auditable outcomes required for an emergency warning system.

**Decision:**  
Gemini is used for exactly three things:
1. Parsing field reports (voice/text → structured JSON)
2. Localising dispatch messages into ne/hi/en
3. Answering forecaster questions via read-only tools (FR-18 assistant)

Gemini **never** evaluates rules, sets alert levels, or computes arrival times.

**Consequences:**
- (+) Every WARNING is traceable to a specific rule, specific inputs, specific config version
- (+) Numeric guard catches any hallucinated numbers before they reach a duty-holder
- (+) Gemini failure causes fallback to reviewed templates — system never blocks on AI
- (-) Gemini integration is less central than a naive "AI decides" approach
- (-) Requires careful engineering of system prompts and structured output

**Enforcement:** Hard rule H4 in `AGENTS.md` and `DEVELOPMENT.md`. Code review blocks any Gemini call that returns an `IncidentLevel`.

---

## ADR-007: Spring Boot 3.4.3 (Not 4.1.x) for Now

**Date:** 2026-10-16  
**Status:** ACCEPTED — DEFERRED UPGRADE  

**Context:**  
The spec (Section 13) recommends Spring Boot 4.1.x. Boot 4.x is the Jakarta EE 10 era release (Spring Framework 7.x). As of October 2026, Spring Cloud GCP compatibility with Boot 4.x needs verification.

**Decision:**  
Keep Boot 3.4.3 for Sprints 0–3. Upgrade in finale prep (7 Nov–3 Dec) after:
- Verifying `spring-cloud-gcp-starter-pubsub` or plain `google-cloud-pubsub` works with Spring Framework 7
- Running full scenario regression

**Consequences:**
- (+) No mid-sprint upgrade risk
- (-) Running on a version the spec says is behind; must upgrade before finale

**When to revisit:** Start of finale prep (7 Nov). Assign one developer to spike the upgrade.

---

## ADR-008: CAP Messages Always Use Exercise Status in Sim

**Date:** 2026-10-16  
**Status:** ACCEPTED — NON-NEGOTIABLE  

**Context:**  
The demo plays a real past-event scenario. A CAP message with `status: Actual` could be misread as a live warning by any system monitoring the NDRRMA feed.

**Decision:**  
`jd-adapter-cap` reads `jaldrishti.cap.status-override` from config. In `application-sim.yml` it is `Exercise`. In `application-live.yml` it is `Actual`. The sim profile is active by default in all development environments.

**Consequences:**
- (+) Physically impossible to send an `Actual` CAP in any non-production environment
- (+) Reviewers can verify the CAP XML shows `<status>Exercise</status>` in the demo
- (-) Must remember to check this config when switching to a live Cloud Run deployment

---

## ADR-009: Console Build Output Goes to jd-boot Static

**Date:** 2026-10-18  
**Status:** ACCEPTED  

**Context:**  
The Vue 3 console must be served by the same Cloud Run instance as the API to avoid CORS complexity and simplify the demo setup.

**Decision:**  
`vite.config.ts` sets `build.outDir = '../jd-boot/src/main/resources/static'`. Running `npm run build` in `console/` produces static files that Spring Boot serves automatically. The `static/` directory is gitignored — it's a build artifact.

**Consequences:**
- (+) Single Cloud Run service, single URL, no CORS
- (+) Demo works from any network with one URL
- (-) Must run `npm run build` before `mvn package` in the production build pipeline
- (-) The `jd-boot/src/main/resources/static/` dir is always empty in the repo (gitignored)

**Build pipeline order:**
```bash
cd console && npm run build   # → populates jd-boot/src/main/resources/static/
cd .. && ./mvnw package       # → picks up static files in the jar
```

---

## ADR-010: Signed Ack Links Expire in 24 Hours

**Date:** 2026-10-16  
**Status:** ACCEPTED  

**Context:**  
Duty-holder ack links are sent via SMS/Telegram and must work on a phone without any login. But they must not work indefinitely (someone could re-ack an old incident).

**Decision:**  
All ack links are signed with a per-recipient HMAC using a key from Secret Manager. Expiry is 24 hours from issue. The `AckView.vue` calls `GET /api/v1/acks/:dispatchId?sig=...`; the server validates the signature and checks expiry before returning the action details.

**Consequences:**
- (+) No login required for duty-holders (usable on any phone)
- (+) Links expire automatically — no replay attack after 24h
- (-) If a duty-holder's phone is off for >24h, they receive a new message via escalation anyway (Step 2 = deputy)
