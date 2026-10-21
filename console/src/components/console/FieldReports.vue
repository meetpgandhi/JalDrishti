<script setup lang="ts">
import { ref, computed } from 'vue'
import { Sparkles, Send, Mic } from '@lucide/vue'
import { useConsoleStore } from '@/stores/console'
import { TRUST, HAZARDS, EVIDENCE, SAMPLE_REPORTS, humanizeKey } from '@/lib/labels'
import { parseReport } from '@/lib/payload'
import type { Trust } from '@/types/api'
import UiPanel from '@/components/ui/UiPanel.vue'
import UiBadge from '@/components/ui/UiBadge.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiEmptyState from '@/components/ui/UiEmptyState.vue'

const store = useConsoleStore()

const text = ref('')
const selectedTrust = ref<Trust>('OFFICIAL')
const isSubmitting = ref(false)
const submitError = ref<string | null>(null)

const trustRoles: Trust[] = ['OFFICIAL', 'OBSERVER', 'PUBLIC']

function loadSample(sample: (typeof SAMPLE_REPORTS)[0]) {
  text.value = sample.text
  selectedTrust.value = sample.role
}

async function handleSubmit() {
  if (!text.value.trim() || !store.runId) return
  isSubmitting.value = true
  submitError.value = null
  try {
    await store.submitReport(text.value.trim(), selectedTrust.value)
    text.value = ''
  } catch (e: unknown) {
    submitError.value = e instanceof Error ? e.message : String(e)
  } finally {
    isSubmitting.value = false
  }
}

const parsedReportsMap = computed(() => {
  const map = new Map<string, ReturnType<typeof parseReport>>()
  store.reports.forEach((r) => {
    map.set(r.id, parseReport(r.parsedJson))
  })
  return map
})
</script>

<template>
  <UiPanel
    title="Field reports"
    subtitle="What people on the river report. Gemini extracts the facts; it never sets the alert level."
  >
    <template #actions>
      <UiBadge tone="ai" :icon="Sparkles">Gemini</UiBadge>
    </template>

    <div class="field-reports-content">
      <!-- Compose Form -->
      <form class="report-form" @submit.prevent="handleSubmit">
        <div v-if="!store.runId" class="disabled-overlay-hint">
          Start a run first
        </div>

        <div class="field-item">
          <textarea
            v-model="text"
            rows="3"
            class="deva"
            placeholder="Type what you see — Nepali, Hindi or English"
            :disabled="!store.runId"
          />
        </div>

        <div class="form-row">
          <div class="role-select-box">
            <label for="report-sender-select">Sender</label>
            <select
              id="report-sender-select"
              v-model="selectedTrust"
              :disabled="!store.runId"
            >
              <option
                v-for="r in trustRoles"
                :key="r"
                :value="r"
              >
                {{ TRUST[r].label }}
              </option>
            </select>
            <span class="role-effect">{{ TRUST[selectedTrust].effect }}</span>
          </div>

          <UiButton
            variant="primary"
            :icon="Send"
            :disabled="!store.runId || !text.trim()"
            :busy="isSubmitting"
            @click="handleSubmit"
          >
            Submit report
          </UiButton>
        </div>

        <div v-if="submitError" class="submit-error">
          {{ submitError }}
        </div>

        <div class="samples-row">
          <span class="samples-title">Try a sample:</span>
          <UiButton
            v-for="s in SAMPLE_REPORTS"
            :key="s.label"
            variant="ghost"
            size="sm"
            :disabled="!store.runId"
            @click="loadSample(s)"
          >
            {{ s.label }}
          </UiButton>
        </div>
      </form>

      <!-- Reports List -->
      <div v-if="store.runId && store.reports.length === 0" class="reports-empty">
        <UiEmptyState
          :icon="Mic"
          title="No reports yet"
          body="Submit one above, or start from a sample."
        />
      </div>

      <div v-else-if="store.reports.length > 0" class="reports-list">
        <article
          v-for="r in store.reports"
          :key="r.id"
          class="report-card"
        >
          <div class="report-meta">
            <UiBadge :tone="TRUST[r.trust].tone">
              {{ TRUST[r.trust].label }}
            </UiBadge>
            <span class="report-channel">via {{ r.channel }}</span>
          </div>

          <p class="deva report-raw-text">{{ r.rawText }}</p>

          <!-- Status: Parsing -->
          <div v-if="r.status === 'PENDING'" class="report-pending">
            <UiBadge tone="neutral">Parsing…</UiBadge>
          </div>

          <!-- Status: Parsed content -->
          <div
            v-else-if="parsedReportsMap.get(r.id)"
            class="parsed-block"
          >
            <div class="parsed-header">
              <Sparkles :size="14" aria-hidden="true" class="gemini-icon" />
              <span class="parsed-title">Gemini extracted</span>
            </div>

            <dl class="extracted-grid">
              <dt>Hazard:</dt>
              <dd>
                <UiBadge
                  :tone="HAZARDS[parsedReportsMap.get(r.id)!.hazardType].tone"
                >
                  {{ HAZARDS[parsedReportsMap.get(r.id)!.hazardType].label }}
                </UiBadge>
              </dd>

              <dt>Location:</dt>
              <dd>
                <span v-if="parsedReportsMap.get(r.id)!.locationId">
                  {{ store.siteName(parsedReportsMap.get(r.id)!.locationId) }} ({{ parsedReportsMap.get(r.id)!.locationId }})
                </span>
                <span v-else>
                  {{ parsedReportsMap.get(r.id)!.locationText ?? 'Not resolved' }}
                </span>
              </dd>

              <dt>Evidence:</dt>
              <dd>
                <div v-if="parsedReportsMap.get(r.id)!.evidence?.length" class="evidence-badges">
                  <UiBadge
                    v-for="ev in parsedReportsMap.get(r.id)!.evidence"
                    :key="ev"
                    tone="neutral"
                  >
                    {{ EVIDENCE[ev] ?? humanizeKey(ev) }}
                  </UiBadge>
                </div>
                <span v-else>—</span>
              </dd>

              <dt>Confidence:</dt>
              <dd class="confidence-row">
                <div class="confidence-track">
                  <div
                    class="confidence-fill"
                    :style="{ width: `${Math.round(parsedReportsMap.get(r.id)!.confidence * 100)}%` }"
                  />
                </div>
                <span class="confidence-val">
                  {{ Math.round(parsedReportsMap.get(r.id)!.confidence * 100) }}%
                </span>
                <span
                  v-if="parsedReportsMap.get(r.id)!.confidence < 0.7"
                  class="confidence-floor-warn"
                >
                  Below the 0.7 confidence floor — not used as evidence.
                </span>
              </dd>

              <template v-if="parsedReportsMap.get(r.id)!.translationEn">
                <dt>In English:</dt>
                <dd class="translation-text">
                  {{ parsedReportsMap.get(r.id)!.translationEn }}
                </dd>
              </template>
            </dl>
          </div>

          <!-- Status: Unparsed / rejected -->
          <div v-else class="unparsed-block">
            <UiBadge tone="watch">Not parsed</UiBadge>
            <p class="unparsed-desc">
              Gemini could not read this report. It is kept but not used as evidence.
            </p>
          </div>
        </article>
      </div>
    </div>

    <template #footer>
      <span>Shows reports sent from this console in this session. Reports sent from duty phones are not listed here yet.</span>
    </template>
  </UiPanel>
</template>

<style scoped>
.field-reports-content {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.report-form {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  padding: var(--sp-3);
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
}

.disabled-overlay-hint {
  font-size: var(--fs-xs);
  color: var(--text-3);
  font-weight: 500;
}

.field-item {
  width: 100%;
}

.form-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: var(--sp-3);
}

.role-select-box {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
  flex: 1;
}

.role-effect {
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.submit-error {
  color: var(--danger);
  font-size: var(--fs-sm);
}

.samples-row {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
  flex-wrap: wrap;
}

.samples-title {
  font-size: var(--fs-xs);
  color: var(--text-3);
  margin-right: var(--sp-1);
}

.reports-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.report-card {
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: var(--sp-3);
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  background: var(--surface-1);
}

.report-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.report-channel {
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.report-raw-text {
  color: var(--text);
}

.parsed-block {
  background: var(--ai-soft);
  border: 1px solid var(--ai-line);
  border-radius: var(--r-sm);
  padding: var(--sp-3);
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.parsed-header {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
}

.gemini-icon {
  color: var(--ai);
}

.parsed-title {
  font-size: var(--fs-xs);
  font-weight: 600;
  text-transform: uppercase;
  color: var(--ai);
  letter-spacing: 0.04em;
}

.extracted-grid {
  display: grid;
  grid-template-columns: 7rem 1fr;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
}

.extracted-grid dt {
  color: var(--text-3);
  font-weight: 500;
}

.extracted-grid dd {
  color: var(--text);
}

.evidence-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.confidence-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.confidence-track {
  width: 5rem;
  height: 6px;
  border-radius: var(--r-pill);
  background: var(--surface-3);
  overflow: hidden;
}

.confidence-fill {
  height: 100%;
  background: var(--ai);
  border-radius: var(--r-pill);
}

.confidence-val {
  font-weight: 600;
  color: var(--text);
}

.confidence-floor-warn {
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.translation-text {
  color: var(--text-2);
}

.unparsed-block {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}

.unparsed-desc {
  font-size: var(--fs-sm);
  color: var(--text-2);
}
</style>
