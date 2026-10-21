<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ShieldCheck, TriangleAlert, Download } from '@lucide/vue'
import { useConsoleStore } from '@/stores/console'
import { api, endpoints } from '@/lib/api'
import { formatNpt } from '@/lib/time'
import { SCENARIOS, PROVENANCE, ruleLabel, humanizeKey } from '@/lib/labels'
import type { ReplayReport, Tag } from '@/types/api'
import UiModal from '@/components/ui/UiModal.vue'
import UiBadge from '@/components/ui/UiBadge.vue'
import UiStat from '@/components/ui/UiStat.vue'
import UiProvenance from '@/components/ui/UiProvenance.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiEmptyState from '@/components/ui/UiEmptyState.vue'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const store = useConsoleStore()

const report = ref<ReplayReport | null>(null)
const isLoading = ref(false)
const errorMsg = ref<string | null>(null)
const copiedSha = ref(false)

async function loadReport() {
  if (!store.runId) {
    report.value = null
    return
  }
  isLoading.value = true
  errorMsg.value = null
  try {
    report.value = await api.get<ReplayReport>(endpoints.replayReport(store.runId))
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : String(e)
    report.value = null
  } finally {
    isLoading.value = false
  }
}

watch(
  () => [props.open, store.runId],
  ([isOpen]) => {
    if (isOpen) {
      void loadReport()
    } else {
      report.value = null
      errorMsg.value = null
    }
  },
  { immediate: true },
)

const modalSubtitle = computed(() => {
  if (!report.value) return undefined
  const sInfo = SCENARIOS.find((s) => s.id === report.value?.scenario)
  const code = sInfo?.code ?? report.value.scenario
  const title = sInfo?.title ?? report.value.scenario
  return `${code} · ${title} · generated ${formatNpt(report.value.generatedAt)} NPT`
})

const provTags: Tag[] = ['P', 'D', 'A']

async function copySha() {
  if (!report.value?.deterministicSha256) return
  try {
    await navigator.clipboard.writeText(report.value.deterministicSha256)
    copiedSha.value = true
    setTimeout(() => {
      copiedSha.value = false
    }, 2000)
  } catch {
    /* clipboard access failed */
  }
}

function exportJson() {
  if (!report.value) return
  const blob = new Blob([JSON.stringify(report.value, null, 2)], {
    type: 'application/json',
  })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `jaldrishti-report-${report.value.runId}.json`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
</script>

<template>
  <UiModal
    :open="open"
    title="Replay report"
    :subtitle="modalSubtitle"
    max-width="880px"
    @close="emit('close')"
  >
    <!-- No run state -->
    <UiEmptyState
      v-if="!store.runId"
      title="Start a run first"
      body="Replay reports are generated from completed or active simulation runs."
    />

    <!-- Loading skeleton -->
    <div v-else-if="isLoading" class="loading-skeletons">
      <div class="skeleton bar-header" />
      <div class="skeleton bar-stats" />
      <div class="skeleton bar-table" />
    </div>

    <!-- Error state (Never show fallback data!) -->
    <UiEmptyState
      v-else-if="errorMsg"
      tone="danger"
      title="Could not load replay report"
      :body="errorMsg"
    >
      <UiButton variant="secondary" @click="loadReport">Retry</UiButton>
    </UiEmptyState>

    <!-- Report content -->
    <div v-else-if="report" class="report-content">
      <!-- 1. Claim card -->
      <div
        class="claim-card"
        :class="report.primaryClaimVerified ? 'claim-verified' : 'claim-unverified'"
      >
        <div class="claim-badge-row">
          <UiBadge
            v-if="report.primaryClaimVerified"
            tone="ok"
            :icon="ShieldCheck"
          >
            Claim verified
          </UiBadge>
          <UiBadge
            v-else
            tone="watch"
            :icon="TriangleAlert"
          >
            Claim not verified
          </UiBadge>
        </div>

        <p class="claim-sentence">
          <template v-if="report.warningTimeNpt && report.asHappenedPhoneCallTimeNpt">
            JalDrishti warned at {{ report.warningTimeNpt }} NPT. The first public warning on 26 Aug went out at {{ report.asHappenedPhoneCallTimeNpt }} NPT.
            <span v-if="report.minutesSaved > 0"> That is {{ report.minutesSaved }} minutes earlier.</span>
          </template>
          <template v-else-if="!report.warningTimeNpt">
            No warning was raised in this run.
          </template>
        </p>
      </div>

      <!-- 2. Three stats -->
      <div class="stats-row">
        <UiStat
          label="Warning at"
          :value="report.warningTimeNpt ?? '—'"
        />
        <UiStat
          label="Record's first warning"
          :value="report.asHappenedPhoneCallTimeNpt ?? '—'"
        />
        <UiStat
          label="Minutes earlier"
          :value="report.minutesSaved > 0 ? String(report.minutesSaved) : '—'"
          :tone="report.minutesSaved > 0 ? 'ok' : undefined"
        />
      </div>

      <!-- 3. Provenance of inputs -->
      <section class="report-section">
        <h3 class="section-title">Provenance of inputs</h3>
        <div class="provenance-counts-grid">
          <div
            v-for="t in provTags"
            :key="t"
            class="prov-stat-item"
          >
            <UiProvenance :tag="t" />
            <span class="prov-stat-label">{{ PROVENANCE[t].label }}</span>
            <span class="prov-stat-val mono">{{ report.provenanceCounts[t] ?? 0 }}</span>
          </div>
        </div>
      </section>

      <!-- 4. Actions -->
      <section
        v-if="Object.keys(report.actionsSummary || {}).length > 0"
        class="report-section"
      >
        <h3 class="section-title">Actions</h3>
        <div class="actions-summary-grid">
          <div
            v-for="(val, key) in report.actionsSummary"
            :key="key"
            class="action-summary-item"
          >
            <span class="action-summary-key">{{ humanizeKey(key) }}:</span>
            <span class="action-summary-val">{{ String(val) }}</span>
          </div>
        </div>
      </section>

      <!-- 5. Decision sequence -->
      <section class="report-section">
        <h3 class="section-title">Decision sequence</h3>
        <div class="decision-table-wrapper">
          <table class="decision-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Rule</th>
                <th>Event</th>
                <th>Description</th>
                <th>Tag</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="ev in report.decisionTimeline"
                :key="ev.seq"
              >
                <td class="cell-time mono">{{ ev.timeNpt }}</td>
                <td class="cell-rule">{{ ruleLabel(ev.ruleId) }}</td>
                <td class="cell-event">{{ humanizeKey(ev.eventType) }}</td>
                <td class="cell-desc">{{ ev.description }}</td>
                <td class="cell-tag">
                  <UiProvenance :tag="ev.tag" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- 6. Determinism -->
      <section class="report-section">
        <h3 class="section-title">Determinism</h3>
        <p class="determinism-intro">
          Same scenario, seed and configuration give the same event stream. SHA-256 of this run's events:
        </p>
        <div class="sha-box">
          <span class="sha-val mono">{{ report.deterministicSha256 }}</span>
          <UiButton
            variant="ghost"
            size="sm"
            @click="copySha"
          >
            {{ copiedSha ? 'Copied' : 'Copy' }}
          </UiButton>
        </div>
      </section>
    </div>

    <template #footer>
      <UiButton
        v-if="report"
        variant="secondary"
        :icon="Download"
        @click="exportJson"
      >
        Export JSON
      </UiButton>
      <UiButton
        variant="primary"
        @click="emit('close')"
      >
        Close
      </UiButton>
    </template>
  </UiModal>
</template>

<style scoped>
.loading-skeletons {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  padding: var(--sp-3) 0;
}

.bar-header {
  height: 5rem;
  width: 100%;
}

.bar-stats {
  height: 4rem;
  width: 100%;
}

.bar-table {
  height: 10rem;
  width: 100%;
}

.report-content {
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
}

.claim-card {
  background: var(--surface-2);
  padding: var(--sp-4);
  border-radius: var(--r-sm);
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.claim-verified {
  border-left: 4px solid var(--ok);
}

.claim-unverified {
  border-left: 4px solid var(--watch);
}

.claim-badge-row {
  display: flex;
  align-items: center;
}

.claim-sentence {
  font-size: var(--fs-lg);
  color: var(--text);
  line-height: var(--lh-normal);
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--sp-4);
  padding: var(--sp-3);
  background: var(--surface-2);
  border-radius: var(--r-sm);
}

.report-section {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.section-title {
  font-size: var(--fs-xs);
  font-weight: 600;
  text-transform: uppercase;
  color: var(--text-3);
  letter-spacing: 0.04em;
}

.provenance-counts-grid {
  display: flex;
  align-items: center;
  gap: var(--sp-5);
  flex-wrap: wrap;
}

.prov-stat-item {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.prov-stat-label {
  font-size: var(--fs-sm);
  color: var(--text-2);
}

.prov-stat-val {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text);
}

.actions-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr));
  gap: var(--sp-2);
  font-size: var(--fs-sm);
}

.action-summary-item {
  display: flex;
  gap: var(--sp-2);
}

.action-summary-key {
  color: var(--text-3);
}

.action-summary-val {
  color: var(--text);
  font-weight: 500;
}

.decision-table-wrapper {
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  overflow-x: auto;
}

.decision-table {
  width: 100%;
}

.cell-time {
  white-space: nowrap;
}

.cell-rule {
  white-space: nowrap;
  font-weight: 500;
}

.cell-event {
  white-space: nowrap;
}

.cell-desc {
  min-width: 12rem;
}

.cell-tag {
  white-space: nowrap;
}

.determinism-intro {
  font-size: var(--fs-sm);
  color: var(--text-2);
}

.sha-box {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-2) var(--sp-3);
  background: var(--surface-2);
  border-radius: var(--r-sm);
  border: 1px solid var(--border);
}

.sha-val {
  font-size: var(--fs-xs);
  color: var(--text);
  word-break: break-all;
  flex: 1;
}
</style>
