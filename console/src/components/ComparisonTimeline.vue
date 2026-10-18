<template>
  <div class="comparison-timeline card">
    <div class="timeline-header">
      <div class="header-title">
        <h3>26 August 2026 Disaster Timeline — Comparative Analysis</h3>
        <span class="badge badge-p">[P]</span>
      </div>
      <div v-if="minutesSaved !== null" class="delta-badge text-mono">
        ⚡ {{ minutesSaved }} MINUTES SAVED
        <span class="badge badge-d" style="margin-left:6px">[D]</span>
      </div>
    </div>

    <div v-if="loading" class="timeline-empty text-mono text-muted">Loading timeline…</div>
    <div v-else-if="!hasAnyData" class="timeline-empty text-mono text-muted">
      No events yet. Start the 26 August replay to see JalDrishti's detection compared against
      what actually happened that morning.
    </div>

    <div v-else class="tracks-container">
      <!-- Track 1: As Happened -->
      <div class="timeline-track track-as-happened">
        <div class="track-label">
          <span class="track-name text-mono">AS HAPPENED (Actual DHM Record)</span>
          <span class="badge badge-p">[P]</span>
        </div>
        <div class="events-rail">
          <div
            v-for="mark in asHappenedMarks"
            :key="'ah-' + mark.at"
            class="event-node"
            :class="nodeClass(mark.tag)"
            :style="{ left: positionPct(mark.at) + '%' }"
          >
            <div class="time-label text-mono">{{ formatTime(mark.at) }}</div>
            <div class="event-desc">{{ mark.label }}</div>
          </div>
        </div>
      </div>

      <!-- Track 2: JalDrishti -->
      <div class="timeline-track track-jaldrishti">
        <div class="track-label">
          <span class="track-name text-mono text-cyan">JALDRISHTI (Live Detection)</span>
          <span class="badge badge-d">[D]</span>
        </div>
        <div class="events-rail">
          <div
            v-for="ev in jalDrishtiMarks"
            :key="'jd-' + ev.seq"
            class="event-node"
            :class="ev.isWarning ? 'node-highlight' : 'node-ok'"
            :style="{ left: positionPct(ev.at) + '%' }"
          >
            <div class="time-label text-mono" :class="{ 'text-cyan': ev.isWarning }">{{ formatTime(ev.at) }}</div>
            <div class="event-desc">{{ ev.label }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'

const props = defineProps<{ runId: string | null }>()

interface TimelineMark {
  at: string
  label: string
  tag: string
}

interface IncidentEventDto {
  seq: number
  incidentId: string
  at: string
  type: 'OPENED' | 'LEVEL_CHANGED' | 'EVIDENCE_ADDED' | 'BANDS_UPDATED' | 'STOOD_DOWN'
  ruleId: string | null
  tag: string
}

interface JalDrishtiMark {
  seq: number
  at: string
  label: string
  isWarning: boolean
}

const DIRECT_WARNING_RULES = new Set(['R5', 'R6', 'R7', 'R8', 'R9', 'R10'])

const loading = ref(false)
const asHappenedMarks = ref<TimelineMark[]>([])
const incidentEvents = ref<IncidentEventDto[]>([])

const load = async (runId: string) => {
  loading.value = true
  try {
    const [timelineRes, eventsRes] = await Promise.all([
      fetch(`/api/v1/sim/runs/${encodeURIComponent(runId)}/timeline`),
      fetch(`/api/v1/incidents/events?runId=${encodeURIComponent(runId)}`)
    ])
    asHappenedMarks.value = timelineRes.ok ? await timelineRes.json() : []
    incidentEvents.value = eventsRes.ok ? await eventsRes.json() : []
  } catch (e) {
    asHappenedMarks.value = []
    incidentEvents.value = []
  } finally {
    loading.value = false
  }
}

watch(
  () => props.runId,
  (runId) => {
    if (runId) load(runId)
  },
  { immediate: true }
)

const jalDrishtiMarks = computed<JalDrishtiMark[]>(() => {
  const marks: JalDrishtiMark[] = []
  for (const ev of incidentEvents.value) {
    if (ev.type === 'BANDS_UPDATED') continue // internal derived artifact, not a narrative moment
    let label: string
    let isWarning = false
    if (ev.type === 'LEVEL_CHANGED') {
      label = `WARNING fired (Rule ${ev.ruleId})`
      isWarning = true
    } else if (ev.type === 'OPENED' && ev.ruleId && DIRECT_WARNING_RULES.has(ev.ruleId)) {
      label = `WARNING fired (Rule ${ev.ruleId})`
      isWarning = true
    } else if (ev.type === 'OPENED') {
      label = `WATCH opened (Rule ${ev.ruleId})`
    } else if (ev.type === 'EVIDENCE_ADDED') {
      label = `Corroborating evidence (Rule ${ev.ruleId})`
    } else {
      label = 'Stood down'
    }
    marks.push({ seq: ev.seq, at: ev.at, label, isWarning })
  }
  return marks
})

const firstWarningAt = computed(() => {
  const warning = jalDrishtiMarks.value.find(m => m.isWarning)
  return warning ? warning.at : null
})

const firstAsHappenedAt = computed(() => {
  if (asHappenedMarks.value.length === 0) return null
  return [...asHappenedMarks.value].sort((a, b) => a.at.localeCompare(b.at))[0].at
})

const minutesSaved = computed(() => {
  if (!firstWarningAt.value || !firstAsHappenedAt.value) return null
  const diffMs = new Date(firstAsHappenedAt.value).getTime() - new Date(firstWarningAt.value).getTime()
  const minutes = Math.round(diffMs / 60000)
  return minutes > 0 ? minutes : null
})

const hasAnyData = computed(() => asHappenedMarks.value.length > 0 || jalDrishtiMarks.value.length > 0)

const timeRange = computed(() => {
  const allTimes = [
    ...asHappenedMarks.value.map(m => new Date(m.at).getTime()),
    ...jalDrishtiMarks.value.map(m => new Date(m.at).getTime())
  ]
  if (allTimes.length === 0) return null
  return { min: Math.min(...allTimes), max: Math.max(...allTimes) }
})

const positionPct = (iso: string) => {
  const range = timeRange.value
  if (!range || range.max === range.min) return 5
  const t = new Date(iso).getTime()
  const pct = ((t - range.min) / (range.max - range.min)) * 90 + 5 // 5%-95% padding
  return Math.min(95, Math.max(5, pct))
}

const nodeClass = (tag: string) => {
  if (tag === 'P') return 'node-amber'
  return 'node-muted'
}

const formatTime = (isoString: string) => {
  try {
    const d = new Date(isoString)
    const nptMillis = d.getTime() + (5 * 60 + 45) * 60 * 1000
    const nptDate = new Date(nptMillis)
    return nptDate.toISOString().substring(11, 16)
  } catch (e) {
    return isoString
  }
}
</script>

<style scoped>
.comparison-timeline {
  padding: var(--space-4);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  margin-top: var(--space-4);
}

.timeline-empty {
  padding: var(--space-5);
  text-align: center;
  font-size: 13px;
}

.timeline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-4);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--color-border);
}

.header-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.header-title h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}

.delta-badge {
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  border: 1px solid #38bdf8;
  padding: 3px 10px;
  border-radius: 14px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.tracks-container {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.timeline-track {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.track-label {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 12px;
  font-weight: 700;
}

.text-cyan {
  color: #38bdf8;
}

.events-rail {
  position: relative;
  height: 64px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  margin-top: var(--space-1);
}

.events-rail::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 2px;
  background: rgba(255, 255, 255, 0.1);
  transform: translateY(-50%);
}

.event-node {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  white-space: nowrap;
}

.event-node::before {
  content: '';
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-text-muted);
  border: 2px solid var(--color-bg);
  z-index: 2;
  margin-bottom: 2px;
}

.node-danger::before {
  background: #ef4444;
}

.node-amber::before {
  background: #f59e0b;
}

.node-muted::before {
  background: #6b7280;
}

.node-success::before, .node-ok::before {
  background: #22c55e;
}

.node-highlight::before {
  background: #38bdf8;
  box-shadow: 0 0 10px #38bdf8;
  width: 14px;
  height: 14px;
}

.time-label {
  font-size: 11px;
  font-weight: 700;
}

.event-desc {
  font-size: 10px;
  color: var(--color-text-muted);
}
</style>
