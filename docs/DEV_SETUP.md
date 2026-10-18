# JalDrishti — Developer Setup Guide

> **Keep this up to date.** If you change a tool version or add a dependency, update this file.

---

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java | 21 LTS (Temurin) | `brew install --cask temurin@21` |
| Maven | 3.9.x (bundled via mvnw) | Included — use `./mvnw` |
| Docker Desktop | Latest | https://www.docker.com/products/docker-desktop |
| Node.js | 20+ | `brew install node` |
| Git | Any | Pre-installed on macOS |

> ⚠️ **Java 21 must be on PATH.** Verify with `java -version`. If you see "Unable to locate Java Runtime", install Temurin 21 via Homebrew and restart your terminal.

---

## First-Time Setup

### 1. Clone and verify Maven

```bash
git clone <repo-url> JalDrishti
cd JalDrishti
./mvnw validate            # Should print BUILD SUCCESS
./mvnw compile -q          # Compiles all modules
```

### 2. Start local infrastructure

```bash
docker compose up -d       # Starts Postgres 16 + Pub/Sub emulator + topic bootstrapper
docker compose ps          # Verify all services are healthy
```

Expected services:
- `jd-postgres` → `localhost:5432` (db: `jaldrishti_dev`, user: `jaldrishti`, pw: `jaldrishti_dev`)
- `jd-pubsub` → `localhost:8085` (Pub/Sub emulator)

### 3. Run the application

```bash
# Sim profile (default — no real cloud needed)
./mvnw spring-boot:run -pl jd-boot

# Or build the jar first:
cd console && npm install && npm run build && cd ..
./mvnw package -q -pl jd-boot --am
java -jar jd-boot/target/jd-boot-*.jar
```

The application starts at `http://localhost:8080`.  
Console UI: `http://localhost:8080` (served from Spring Boot static)  
Dev server (with hot reload): `cd console && npm run dev` → `http://localhost:3000`

### 4. Run tests

```bash
# Domain unit tests only (fast, no Docker)
./mvnw test -pl jd-domain -am -q

# All tests (requires Docker for Testcontainers)
./mvnw test -q
```

---

## Environment Variables

### Local development (sim profile — no cloud needed)

```bash
# Optionally override the database password
export DB_PASSWORD=jaldrishti_dev

# Pub/Sub emulator (already set in application-sim.yml)
export PUBSUB_EMULATOR_HOST=localhost:8085
```

### If using Gemini locally

```bash
export GOOGLE_CLOUD_PROJECT=your-project-id
# Application Default Credentials:
gcloud auth application-default login
```

### Production (live profile — Cloud Run env vars)

These are injected by Cloud Run from Secret Manager:
```
SPRING_PROFILES_ACTIVE=live
SPRING_DATASOURCE_URL=jdbc:postgresql://...
DB_USER=...
DB_PASSWORD=...
PUBSUB_PROJECT_ID=...
GOOGLE_CLOUD_PROJECT=...
CAP_GATEWAY_URL=...
TELEGRAM_BOT_TOKEN=...   # from Secret Manager
```

---

## Module Structure Reference

```
./mvnw compile -pl jd-domain           # domain only
./mvnw test -pl jd-domain              # domain tests (no Docker)
./mvnw test -pl jd-adapter-persistence # integration tests (needs Docker)
./mvnw spring-boot:run -pl jd-boot     # run the whole application
```

---

## Console Development

```bash
cd console
npm install

# Dev server (hot reload, proxies /api to :8080)
npm run dev

# Type check
npm run type-check

# Production build (outputs to jd-boot/src/main/resources/static/)
npm run build
```

---

## Working with Scenarios

Scenarios live in `scenarios/`. To run a specific scenario, POST to the sim API:

```bash
# Start a new simulation run
curl -X POST http://localhost:8080/api/v1/sim/runs \
  -H "Content-Type: application/json" \
  -d '{"scenario": "aug26-as-happened", "variants": {"graceMinutes": 3}}'

# Response: {"runId": "abc123..."}

# Play the scenario at 10× speed
curl -X POST http://localhost:8080/api/v1/sim/runs/abc123.../play
curl -X POST http://localhost:8080/api/v1/sim/runs/abc123.../speed \
  -H "Content-Type: application/json" \
  -d '{"speed": 10}'
```

## SSE Stream

```bash
# Watch all events for a run
curl -N "http://localhost:8080/api/v1/stream?runId=abc123..."
```

---

## CI Pipeline (GitHub Actions)

Three jobs — all must pass for a PR to merge:

| Job | Trigger | Duration | What it does |
|-----|---------|----------|-------------|
| `domain-tests` | Every push | ~30s | `./mvnw test -pl jd-domain` |
| `build-and-test` | Every push | ~3 min | Full build + integration tests (Postgres + Pub/Sub emulator in services) |
| `validate-pom` | Every push | ~20s | `./mvnw validate` + grep for forbidden imports in `jd-domain` |

---

## Flyway Migrations

Migration files live in:
```
jd-adapter-persistence/src/main/resources/db/migration/
```

Naming: `V{n}__{description}.sql` (two underscores).  
Current migrations:
- `V1__baseline_schema.sql` — all 15 tables

**To add a new migration:**
1. Create `V2__your_description.sql`
2. Test against Testcontainers locally: `./mvnw test -pl jd-adapter-persistence`
3. Never modify an existing migration that has been committed to `main`

---

## jOOQ Code Generation (Sprint 1 task)

jOOQ generates type-safe classes from the Flyway-migrated schema.  
The Maven plugin config goes in `jd-adapter-persistence/pom.xml` and uses Testcontainers to spin up a temporary Postgres, runs Flyway, then generates classes into `src/main/generated/`.

Generated classes are gitignored and regenerated on each build.

---

## Known Tool Issues

| Issue | Workaround |
|-------|-----------|
| Java not found in agent shell | Install Temurin 21 via Homebrew; restart terminal |
| `./mvnw` permission denied | `chmod +x mvnw` |
| Pub/Sub emulator container not starting | Ensure Docker Desktop is running; `docker compose restart pubsub-emulator` |
| `npm run build` fails — vite not found | `cd console && npm install` first |
| Testcontainers can't find Docker | Ensure Docker Desktop is running and socket is at `/var/run/docker.sock` |
