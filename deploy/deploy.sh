#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# JalDrishti v3 — One-Command Cloud Build & Cloud Run Deploy
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || echo '')}"
REGION="${REGION:-asia-south1}"
SERVICE_NAME="jaldrishti-core"
SA_NAME="jaldrishti-sa"
SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

if [ -z "$PROJECT_ID" ]; then
  echo "Error: PROJECT_ID is not set. Run 'gcloud config set project YOUR_PROJECT_ID' or export PROJECT_ID=..."
  exit 1
fi

echo "=========================================================================="
echo " Deploying JalDrishti to Google Cloud Run"
echo " Project: $PROJECT_ID | Region: $REGION | Service: $SERVICE_NAME"
echo "=========================================================================="

echo "--> [1/2] Triggering Google Cloud Build..."
gcloud builds submit \
  --config=cloudbuild.yaml \
  --substitutions=_REGION="$REGION",_REPO_NAME="jaldrishti",_SERVICE_NAME="$SERVICE_NAME" \
  --project="$PROJECT_ID"

echo "--> [2/2] Updating Service Identity and Cloud Scheduler..."
# Ensure Service Account is attached
gcloud run services update "$SERVICE_NAME" \
  --service-account="$SA_EMAIL" \
  --region="$REGION" \
  --project="$PROJECT_ID" \
  --quiet

SERVICE_URL=$(gcloud run services describe "$SERVICE_NAME" --region="$REGION" --project="$PROJECT_ID" --format='value(status.url)')

# Create or Update Cloud Scheduler for silence sweeps
if ! gcloud scheduler jobs describe "jaldrishti-silence-sweep" --location="$REGION" --project="$PROJECT_ID" >/dev/null 2>&1; then
  gcloud scheduler jobs create http "jaldrishti-silence-sweep" \
    --schedule="* * * * *" \
    --uri="${SERVICE_URL}/internal/sweep" \
    --http-method=POST \
    --oidc-service-account-email="$SA_EMAIL" \
    --location="$REGION" \
    --project="$PROJECT_ID" \
    --quiet
else
  gcloud scheduler jobs update http "jaldrishti-silence-sweep" \
    --uri="${SERVICE_URL}/internal/sweep" \
    --oidc-service-account-email="$SA_EMAIL" \
    --location="$REGION" \
    --project="$PROJECT_ID" \
    --quiet
fi

echo ""
echo "=========================================================================="
echo " ✔ JALDRISHTI DEPLOYMENT SUCCESSFUL!"
echo " Public Console URL : ${SERVICE_URL}"
echo " Phone Simulator    : ${SERVICE_URL}/phone"
echo " Live SSE Stream    : ${SERVICE_URL}/api/v1/stream"
echo " Replay Report API  : ${SERVICE_URL}/api/v1/sim/runs/{runId}/report"
echo "=========================================================================="
