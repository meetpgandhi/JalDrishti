# JalDrishti — Deploy Runbook (Google Cloud Run)

> Practical, copy-pasteable steps for this project's actual GCP setup.
> For architecture/why, see `docs/DECISIONS.md` ADR-011/ADR-012. For what's still
> unverified, see `docs/PROGRESS.md` Known Issues.

---

## 0. Where things live (already provisioned — don't redo this)

| Thing | Value |
|---|---|
| GCP Project | `jaldrishti-512011` |
| Region | `asia-south1` |
| Cloud Run service | `jaldrishti-core` |
| Live URL | `https://jaldrishti-core-282398392020.asia-south1.run.app` |
| Artifact Registry repo | `jaldrishti` (Docker, `asia-south1`) |
| Service account | `jaldrishti-sa@jaldrishti-512011.iam.gserviceaccount.com` (has `roles/aiplatform.user`, `pubsub.editor`, `secretmanager.secretAccessor`, `logging.logWriter`, `cloudtrace.agent`) |
| Database | Neon Postgres (`ap-southeast-1`, AWS) — connection details are **not** in this repo; keep them in your own notes/password manager, not in any committed file |
| GitHub repo | `https://github.com/meetpgandhi/JalDrishti` |

`./deploy/setup-gcp-infra.sh` created all of the above (APIs, Artifact Registry, Pub/Sub topics, service account + IAM). You only need to run it again if you start a *new* GCP project.

**Security note:** the Neon password has been shared in plaintext in chat/shell history during setup. It currently lives only as a Cloud Run env var (not in git). When you have a moment, rotate it on the Neon dashboard and move it to Secret Manager instead of a raw `--update-env-vars` — see AGENTS.md §11 ("Secrets: Secret Manager, never in code or config files"). Not urgent, but don't commit it anywhere.

---

## 1. Routine workflow: ship a code change

This is what you'll do almost every time.

```bash
# On your own machine: make changes, test, commit, push as usual
git add <files>
git commit -m "..."
git push origin main
```

```bash
# In Google Cloud Shell (or anywhere with gcloud authenticated + this repo cloned)
cd ~/JalDrishti
gcloud config set project jaldrishti-512011      # if not already active

git pull origin main

./deploy/deploy.sh
```

`deploy.sh` does three things: builds the Docker image, pushes it to Artifact Registry, and deploys it to Cloud Run via `cloudbuild.yaml` (which sets `SPRING_PROFILES_ACTIVE=sim` and the Gemini flags using `--update-env-vars`, so it **merges** rather than wipes other env vars like the DB connection).

### Then always re-apply the DB connection

The DB connection isn't baked into `cloudbuild.yaml` (so the Neon password never ends up in the repo). Every deploy, run this once, right after `deploy.sh`:

```bash
export NEON_DB_URL="jdbc:postgresql://<your-neon-host>/<dbname>?sslmode=require"
export NEON_DB_USER="<your-neon-user>"
export NEON_DB_PASSWORD="<your-neon-password>"

gcloud run services update jaldrishti-core \
  --region=asia-south1 \
  --update-env-vars="SPRING_DATASOURCE_URL=${NEON_DB_URL},SPRING_DATASOURCE_USERNAME=${NEON_DB_USER},SPRING_DATASOURCE_PASSWORD=${NEON_DB_PASSWORD}" \
  --quiet
```

`--update-env-vars` merges into whatever's already there, so this is safe to run repeatedly — it won't clobber the Gemini flags `deploy.sh` just set, or vice versa.

---

## 2. Verify the deploy worked

```bash
# Tail the last ~30 log lines of the current revision
gcloud run services logs read jaldrishti-core --region=asia-south1 --limit=30
```

Look for:
- `Schema "public" is up to date` (or a list of migrations applied) — DB connection is good.
- `Started JalDrishtiApplication in N seconds` — app booted.
- No stack trace ending in `Connection to localhost:5432 refused` — if you see this, the DB env vars weren't applied; redo step 1's second command.

Then open the URL and click through: `https://jaldrishti-core-282398392020.asia-south1.run.app`

---

## 3. Toggle Gemini on/off

Vertex AI is **not free** (pay-per-token; see chat history for current pricing) — the service account already has the IAM role it needs, so flipping this needs no rebuild, just:

```bash
# Turn ON
gcloud run services update jaldrishti-core \
  --region=asia-south1 \
  --update-env-vars=JALDRISHTI_GEMINI_VERTEX_ENABLED=true

# Turn OFF
gcloud run services update jaldrishti-core \
  --region=asia-south1 \
  --update-env-vars=JALDRISHTI_GEMINI_VERTEX_ENABLED=false
```

Note: the *default* in `cloudbuild.yaml` is `false`. If you redeploy via `./deploy/deploy.sh` after having turned Gemini on manually, it'll merge `JALDRISHTI_GEMINI_VERTEX_ENABLED=false` back in (since that's what `cloudbuild.yaml` sets) — re-run the "Turn ON" command above afterward if you want it to stay on across a redeploy.

---

## 4. Something's wrong — diagnostics & rollback

```bash
# What's actually running right now (image, env vars, resources, service account)
gcloud run services describe jaldrishti-core --region=asia-south1 --format=yaml

# List all revisions (to find one to roll back to)
gcloud run revisions list --service=jaldrishti-core --region=asia-south1

# Roll back: send 100% of traffic to a known-good revision
gcloud run services update-traffic jaldrishti-core \
  --region=asia-south1 \
  --to-revisions=REVISION_NAME=100

# Full logs for a specific revision (replace REVISION_NAME)
gcloud logging read \
  'resource.type="cloud_run_revision" AND resource.labels.revision_name="REVISION_NAME"' \
  --project=jaldrishti-512011 --limit=100 --format=json
```

A failed deploy (health check timeout) does **not** take the live site down — Cloud Run keeps the last healthy revision serving 100% of traffic until a new one passes its health check. So it's always safe to try a redeploy; worst case, nothing changes for users while you debug.

---

## 5. One-time setup (reference only — already done for this project)

```bash
export PROJECT_ID="jaldrishti-512011"
export REGION="asia-south1"
gcloud config set project "$PROJECT_ID"

./deploy/setup-gcp-infra.sh   # enables APIs, creates Artifact Registry repo,
                               # Pub/Sub topics+DLQs, service account + IAM roles
./deploy/deploy.sh            # first build + deploy
# ...then the DB env var step from section 1.
```

Only re-run `setup-gcp-infra.sh` if you're setting this up in a brand-new GCP project.
