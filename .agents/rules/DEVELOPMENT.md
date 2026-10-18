# JalDrishti Development Rules

## Architecture Rules

1. **Maven only.** Never introduce Gradle or any other build tool.
2. **Hexagonal boundaries.** `jd-domain` must have zero Spring, Jackson, or database imports. Fail immediately if any are found.
3. **No cross-adapter dependencies.** Adapters (`jd-adapter-*`) must depend only on `jd-application`, never on each other.
4. **All versions in root POM.** Never declare a dependency version in a child module's `pom.xml` unless it overrides the root POM intentionally.
5. **Package structure.** Always use `org.jaldrishti.<module>.*` package naming (e.g., `org.jaldrishti.domain`, `org.jaldrishti.adapter.pubsub`).

## Code Rules

6. **Gemini never decides.** Gemini is used only for: (a) parsing field reports into structured JSON, (b) localising messages, (c) the incident assistant (FR-18). The trigger engine (R1–R11) is pure deterministic Java.
7. **SimClock everywhere.** All time logic reads `java.time.Clock` via injection — never `LocalDateTime.now()` or `Instant.now()` directly.
8. **Idempotent inserts.** The `reading` table insert must use `ON CONFLICT (run_id, station_id, observed_at) DO NOTHING`.
9. **Append-only events.** `incident_event` is append-only. Never UPDATE or DELETE from it.
10. **run_id on all tables.** Every table related to simulation state must include a `run_id` column. Live data uses `run_id = 'live'`.

## AI / Gemini Rules

11. **Temperature 0 always.** All Gemini calls (parse, localise, assistant) use `temperature: 0`.
12. **Numeric guard always runs.** After every Gemini localisation call, the numeric guard must run. On failure, send the reviewed template and log the failure.
13. **Devanagari digits.** Always convert Devanagari digits (०–९) to ASCII (0–9) before running the numeric guard.
14. **No PII in prompts.** Phone numbers, real names, and personal identifiers never go into a Gemini prompt. Pass role + trust level only.
15. **Fallback templates.** On any Gemini timeout, error, or guard failure, send the reviewed template. Never block dispatch waiting for Gemini.

## Simulation Rules

16. **One code path.** The simulator must publish to the same topics as the live DHM adapter. No special-casing in the trigger engine.
17. **CAP in sim = Exercise.** All CAP messages produced during a simulation run must have `status: Exercise`.
18. **Every injection is logged.** Live injections stored with `source = LIVE_INJECTION` in `incident_event`.
19. **Scenario validator is mandatory.** Never load a scenario that contradicts a `[P]` (published) data point.
20. **Golden diff in CI.** S1's `incident_event` sequence must be diffed against `scenarios/golden/s1-events.jsonl` on every commit (NFR-05).

## Security Rules

21. **OIDC for /internal/*.** The `/internal/sweep` and `/internal/escalate` endpoints accept only OIDC tokens from the Scheduler and simulator service accounts.
22. **Signed ack links.** Phone-simulator and ack links are signed per recipient and expire after 24 hours.
23. **No secrets in code.** Telegram token, SMS/voice provider keys go in Secret Manager. Never in `application.yml`, config files, or code.
24. **Encrypted phone numbers.** `duty_holder.phone_enc` stores phone numbers encrypted at rest.
25. **Audio retention.** Audio files are deleted after 30 days unless attached to an incident.

## UI / Provenance Rules

26. **Provenance badges required.** Every reading, event, and computed value shown in the operator console must display its provenance badge: [P] green, [D] blue, [A] amber.
27. **CAP = Exercise during demo.** Always verify CAP messages show `status: Exercise` before any live demo.
28. **Devanagari rendering.** Messages in Nepali and Hindi must use Noto Sans Devanagari.

## Testing Rules

29. **Domain unit tests are the gate.** Every domain rule (R1–R11) must have unit tests covering: happy path, edge cases (same slot, reversed order, speed > 35 m/s), and error cases.
30. **S1 target is immovable.** Scenario S1 with 3-min grace and Syaphrubesi lost by 08:50 must produce a WARNING at 08:53. If it does not, there is a bug.
31. **S3 target is immovable.** Scenario S3 (gauge power failure) must produce zero public warnings.
32. **Property-based tests for R5.** For random silences in random order, R5 fires only when order-consistent. Use jqwik.

## Commit & PR Rules

33. **No breaking changes to the scenario format** without updating all YAML files in `scenarios/` and the golden file.
34. **No new `[A]` values in domain logic** without a comment explaining why the value is an assumption and how to calibrate it.
35. **Spring Boot version.** Current POM: 3.4.3. The spec targets 4.1.x. Do not upgrade without: (a) checking Spring Cloud GCP compatibility, (b) full scenario suite regression, (c) team sign-off.
