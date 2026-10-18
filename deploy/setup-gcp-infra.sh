#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# JalDrishti v3 — Google Cloud Infrastructure Provisioning Script
# Prepares all GCP services for the competition deployment:
# - Enables Required Google Cloud APIs
# - Provisions Artifact Registry
# - Creates 8 Pub/Sub Topics + Dead Letter Queues
# - Configures Cloud Run Service Account & IAM Roles (Vertex AI, Pub/Sub)
# - Configures Cloud Scheduler for Automated Silence Sweeps
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || echo '')}"
REGION="${REGION:-asia-south1}" # Mumbai region (closest to Nepal)
SERVICE_NAME="jaldrishti-core"
SA_NAME="jaldrishti-sa"
REPO_NAME="jaldrishti"

if [ -z "$PROJECT_ID" ]; then
  echo "Error: PROJECT_ID is not set. Run 'gcloud config set project YOUR_PROJECT_ID' or set PROJECT_ID environment variable."
  exit 1
fi

echo "=========================================================================="
echo " Provisioning JalDrishti Infrastructure on Google Cloud"
echo " Project: $PROJECT_ID | Region: $REGION"
echo "=========================================================================="

# 1. Enable Required Cloud APIs
echo "--> [1/5] Enabling Google Cloud APIs..."
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  pubsub.googleapis.com \
  aiplatform.googleapis.com \
  secretmanager.googleapis.com \
  cloudscheduler.googleapis.com \
  cloudlogging.googleapis.com \
  cloudtrace.googleapis.com \
  --project="$PROJECT_ID"

# 2. Create Artifact Registry Repository
echo "--> [2/5] Creating Artifact Registry Repository ($REPO_NAME)..."
if ! gcloud artifacts repositories describe "$REPO_NAME" --location="$REGION" --project="$PROJECT_ID" >/dev/null 2>&1; then
  gcloud artifacts repositories create "$REPO_NAME" \
    --repository-format=docker \
    --location="$REGION" \
    --description="JalDrishti Docker Repository" \
    --project="$PROJECT_ID"
  echo "    Repository $REPO_NAME created."
else
  echo "    Repository $REPO_NAME already exists."
fi

# 3. Create 8 Pub/Sub Topics and DLQs
echo "--> [3/5] Creating Pub/Sub Topics and Subscriptions..."
TOPICS=(
  "gauge-readings"
  "field-reports"
  "seismic-events"
  "observations"
  "incident-events"
  "dispatch-requests"
  "acks"
  "clock-control"
)

for topic in "${TOPICS[@]}"; do
  # Create DLQ
  if ! gcloud pubsub topics describe "${topic}-dlq" --project="$PROJECT_ID" >/dev/null 2>&1; then
    gcloud pubsub topics create "${topic}-dlq" --project="$PROJECT_ID" --quiet
  fi

  # Create Main Topic
  if ! gcloud pubsub topics describe "$topic" --project="$PROJECT_ID" >/dev/null 2>&1; then
    gcloud pubsub topics create "$topic" --project="$PROJECT_ID" --quiet
    echo "    Created topic: $topic"
  fi

  # Create Subscription with Dead-Letter Topic
  if ! gcloud pubsub subscriptions describe "${topic}-sub" --project="$PROJECT_ID" >/dev/null 2>&1; then
    gcloud pubsub subscriptions create "${topic}-sub" \
      --topic="$topic" \
      --dead-letter-topic="${topic}-dlq" \
      --max-delivery-attempts=5 \
      --project="$PROJECT_ID" \
      --quiet
    echo "    Created subscription: ${topic}-sub"
  fi
done

# 4. Service Account and IAM Roles
echo "--> [4/5] Setting up Service Account ($SA_NAME) with Vertex AI and Pub/Sub IAM roles..."
SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

if ! gcloud iam service-accounts describe "$SA_EMAIL" --project="$PROJECT_ID" >/dev/null 2>&1; then
  gcloud iam service-accounts create "$SA_NAME" \
    --display-name="JalDrishti Cloud Run Service Account" \
    --project="$PROJECT_ID"
fi

ROLES=(
  "roles/aiplatform.user"       # For Vertex AI Gemini 3.6 Flash
  "roles/pubsub.editor"         # For Pub/Sub publish and subscribe
  "roles/secretmanager.secretAccessor" # For Secret Manager tokens
  "roles/logging.logWriter"     # For Cloud Logging
  "roles/cloudtrace.agent"      # For Distributed Tracing
)

for role in "${ROLES[@]}"; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:$SA_EMAIL" \
    --role="$role" \
    --quiet >/dev/null
done
echo "    IAM roles bound to $SA_EMAIL."

# 5. Summary & Next Steps
echo "--> [5/5] Infrastructure setup complete!"
echo ""
echo "Next step: Run './deploy/deploy.sh' to trigger Cloud Build and deploy to Cloud Run."
