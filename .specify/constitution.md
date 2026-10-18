# JalDrishti Engineering Constitution

This constitution governs development according to the **Spec-Driven Development (SDD)** lifecycle for JalDrishti v3.

## 1. Architectural Principles
- **Hexagonal Architecture (Ports & Adapters):**
  - `jd-domain`: Pure Java 21 LTS domain logic (Rules R1–R11, arrival estimation, state machines). **Zero framework dependencies** (no Spring, no Jackson, no database code).
  - `jd-application`: Orchestration and use-case services defining inbound and outbound ports.
  - `jd-adapter-*`: Separate modules implementing external adapters (Pub/Sub, REST, Postgres/jOOQ, Gemini, Channels, CAP).
  - `jd-boot`: Top-level Spring Boot wiring container.
- **Strict Build System:**
  - Build tool is strictly **Maven** (`pom.xml`), **not Gradle**.
  - All inter-module dependencies are explicitly managed in the root parent POM.

## 2. Event Sourcing & Immutability
- All state transitions emit immutable events.
- Auditability and replay reproducibility are first-class requirements.
- Clock abstraction: `SimClock` in simulation profile, real clock in production.

## 3. AI Ingestion & Determinism
- Field-report extraction utilizes Gemini on Vertex AI with deterministic settings (`temperature: 0`, strict JSON response schema, gazetteer alignment).
- Numeric values and downstream rules are protected by deterministic guards.
