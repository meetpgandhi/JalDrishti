<script setup lang="ts">
import { computed } from 'vue'
import { History } from '@lucide/vue'
import { useConsoleStore } from '@/stores/console'
import { SCENARIOS, eventLabel, ruleLabel } from '@/lib/labels'
import { toMs, formatNpt, formatNptRange, minutesBetween, formatDuration } from '@/lib/time'
import type { IncidentEvent } from '@/types/api'
import UiPanel from '@/components/ui/UiPanel.vue'
import UiProvenance from '@/components/ui/UiProvenance.vue'
import UiEmptyState from '@/components/ui/UiEmptyState.vue'

const store = useConsoleStore()

const currentScenario = computed(() =>
  SCENARIOS.find((s) => s.id === store.run?.scenario),
)

const panelTitle = computed(() =>
  store.mode === 'training' && currentScenario.value?.hasPublishedRecord
    ? 'Lead time vs. the published record'
    : 'Timeline',
)

// Headline sentence
const headlineData = computed(() => {
  const firstWarning = store.firstWarningEvent
  const asHappened = store.asHappened
  if (!firstWarning || asHappened.length === 0) return null

  const sortedMarks = [...asHappened].sort(
    (a, b) => (toMs(a.at) ?? 0) - (toMs(b.at) ?? 0),
  )
  const first = sortedMarks[0]
  if (!first) return null

  const m = minutesBetween(firstWarning.at, first.at)
  if (m === null) return null

  return {
    firstWarningAt: firstWarning.at,
    firstMarkLabel: first.label,
    firstMarkAt: first.at,
    minutesSaved: m,
  }
})

// Timeline domain calculation
const timelineScale = computed(() => {
  const allIso: string[] = []
  store.asHappened.forEach((m) => allIso.push(m.at))
  store.timelineEvents.forEach((e) => allIso.push(e.at))
  store.bands.forEach((b) => {
    allIso.push(b.earliestArrival)
    allIso.push(b.latestArrival)
  })
  if (store.simNow) allIso.push(store.simNow)

  const ms = allIso.map(toMs).filter((x): x is number => x !== null)
  if (ms.length === 0) return null

  const HALF = 30 * 60_000
  let min = Math.floor((Math.min(...ms) - 10 * 60_000) / HALF) * HALF
  let max = Math.ceil((Math.max(...ms) + 10 * 60_000) / HALF) * HALF
  if (max - min < 3 * HALF) max = min + 3 * HALF
  const step = max - min > 12 * HALF ? 2 * HALF : HALF

  const pct = (iso: string | null | undefined): number => {
    const t = toMs(iso)
    if (t === null) return 0
    return Math.min(100, Math.max(0, ((t - min) / (max - min)) * 100))
  }

  const ticks: Array<{ time: number; pct: number; label: string }> = []
  for (let t = min; t <= max; t += step) {
    ticks.push({
      time: t,
      pct: ((t - min) / (max - min)) * 100,
      label: formatNpt(new Date(t).toISOString()),
    })
  }

  const nowPct = store.simNow ? pct(store.simNow) : null

  return { min, max, ticks, pct, nowPct }
})

const showPublishedSection = computed(() => {
  if (store.mode === 'live') {
    return store.asHappened.length > 0
  }
  return true
})

// Filtered event rows for JalDrishti section
const jalDrishtiEvents = computed(() => {
  return store.timelineEvents.filter(
    (e) => e.type === 'OPENED' || e.type === 'LEVEL_CHANGED' || e.type === 'STOOD_DOWN',
  )
})

function getEventDotClass(event: IncidentEvent): string {
  if (event.type === 'STOOD_DOWN') return 'dot-info'
  if (event.type === 'LEVEL_CHANGED') return 'dot-danger'
  if (event.type === 'OPENED') {
    return event.ruleId && ['R5', 'R6', 'R7', 'R8', 'R10'].includes(event.ruleId)
      ? 'dot-danger'
      : 'dot-watch'
  }
  return 'dot-watch'
}

const bandsSorted = computed(() => {
  return [...store.bands].sort((a, b) => a.riverKm - b.riverKm)
})
</script>

<template>
  <UiPanel
    :title="panelTitle"
    subtitle="All times NPT. The blue line is the simulation clock."
  >
    <!-- Empty state -->
    <UiEmptyState
      v-if="!store.runId"
      :icon="History"
      title="Nothing to show yet"
      body="Start the 26 Aug replay to compare JalDrishti with what actually happened."
    />

    <div v-else class="timeline-container">
      <!-- Headline sentence -->
      <div v-if="headlineData" class="headline-banner">
        <template v-if="headlineData.minutesSaved > 0">
          <span>JalDrishti raised a warning at <strong>{{ formatNpt(headlineData.firstWarningAt) }}</strong> NPT — </span>
          <span class="saved-duration">{{ formatDuration(headlineData.minutesSaved) }}</span>
          <span> before the first entry in the published record ({{ headlineData.firstMarkLabel }}, {{ formatNpt(headlineData.firstMarkAt) }} NPT).</span>
        </template>
        <template v-else>
          <span>JalDrishti's first warning came at {{ formatNpt(headlineData.firstWarningAt) }} NPT, {{ formatDuration(-headlineData.minutesSaved) }} after the first entry in the published record.</span>
        </template>
      </div>

      <!-- Chart grid -->
      <div v-if="timelineScale" class="chart-grid">
        <!-- Header row -->
        <div class="row-label header-label" />
        <div class="row-track header-track">
          <!-- Ticks -->
          <span
            v-for="tick in timelineScale.ticks"
            :key="tick.time"
            class="tick-label mono"
            :style="{ left: `${tick.pct}%` }"
          >
            {{ tick.label }}
          </span>

          <!-- Now label -->
          <span
            v-if="timelineScale.nowPct !== null"
            class="now-header-label"
            :style="{ left: `${timelineScale.nowPct}%` }"
          >
            now
          </span>
        </div>

        <!-- Section 1: Published record -->
        <template v-if="showPublishedSection">
          <div class="group-header">
            <div class="group-title-row">
              <span>Published record</span>
              <UiProvenance tag="P" />
            </div>
          </div>

          <template v-if="store.asHappened.length > 0">
            <template
              v-for="mark in store.asHappened"
              :key="mark.at + mark.label"
            >
              <div class="row-label" :title="mark.label">
                <span class="label-text">{{ mark.label }}</span>
              </div>
              <div class="row-track">
                <!-- Gridlines -->
                <div
                  v-for="tick in timelineScale.ticks"
                  :key="'grid-' + tick.time"
                  class="grid-line"
                  :style="{ left: `${tick.pct}%` }"
                />
                <!-- Now line -->
                <div
                  v-if="timelineScale.nowPct !== null"
                  class="now-line"
                  :style="{ left: `${timelineScale.nowPct}%` }"
                />
                <!-- Data point -->
                <div
                  class="data-point dot-published"
                  :style="{ left: `${timelineScale.pct(mark.at)}%` }"
                />
                <span
                  class="point-time mono"
                  :class="{ 'is-flipped': timelineScale.pct(mark.at) > 85 }"
                  :style="{
                    [timelineScale.pct(mark.at) > 85 ? 'right' : 'left']:
                      timelineScale.pct(mark.at) > 85
                        ? `calc(100% - ${timelineScale.pct(mark.at)}% + 8px)`
                        : `calc(${timelineScale.pct(mark.at)}% + 8px)`
                  }"
                >
                  {{ formatNpt(mark.at) }}
                </span>
              </div>
            </template>
          </template>
          <template v-else-if="store.mode === 'training' && !currentScenario?.hasPublishedRecord">
            <div class="no-record-row">
              <span>This scenario has no published record to compare against.</span>
            </div>
          </template>
        </template>

        <!-- Section 2: JalDrishti this run -->
        <div class="group-header">
          <div class="group-title-row">
            <span>JalDrishti — this run</span>
          </div>
        </div>

        <template
          v-for="e in jalDrishtiEvents"
          :key="e.seq ?? e.at"
        >
          <div
            class="row-label"
            :title="`${eventLabel(e.type, e.ruleId)} · ${ruleLabel(e.ruleId)}`"
          >
            <span class="label-text">
              {{ eventLabel(e.type, e.ruleId) }} · {{ ruleLabel(e.ruleId) }}
            </span>
          </div>
          <div class="row-track">
            <!-- Gridlines -->
            <div
              v-for="tick in timelineScale.ticks"
              :key="'grid-jd-' + tick.time"
              class="grid-line"
              :style="{ left: `${tick.pct}%` }"
            />
            <!-- Now line -->
            <div
              v-if="timelineScale.nowPct !== null"
              class="now-line"
              :style="{ left: `${timelineScale.nowPct}%` }"
            />
            <!-- Data point -->
            <div
              class="data-point"
              :class="getEventDotClass(e)"
              :style="{ left: `${timelineScale.pct(e.at)}%` }"
            />
            <span
              class="point-time mono"
              :class="{ 'is-flipped': timelineScale.pct(e.at) > 85 }"
              :style="{
                [timelineScale.pct(e.at) > 85 ? 'right' : 'left']:
                  timelineScale.pct(e.at) > 85
                    ? `calc(100% - ${timelineScale.pct(e.at)}% + 8px)`
                    : `calc(${timelineScale.pct(e.at)}% + 8px)`
              }"
            >
              {{ formatNpt(e.at) }}
            </span>
          </div>
        </template>

        <!-- Section 3: Arrival windows -->
        <div v-if="bandsSorted.length > 0" class="group-header">
          <div class="group-title-row">
            <span>Arrival windows</span>
            <UiProvenance tag="D" />
          </div>
        </div>

        <template
          v-for="b in bandsSorted"
          :key="b.siteId"
        >
          <div class="row-label" :title="b.siteName">
            <span class="label-text">{{ b.siteName }}</span>
          </div>
          <div class="row-track">
            <!-- Gridlines -->
            <div
              v-for="tick in timelineScale.ticks"
              :key="'grid-b-' + tick.time"
              class="grid-line"
              :style="{ left: `${tick.pct}%` }"
            />
            <!-- Now line -->
            <div
              v-if="timelineScale.nowPct !== null"
              class="now-line"
              :style="{ left: `${timelineScale.nowPct}%` }"
            />
            <!-- Range bar -->
            <div
              class="range-bar"
              :style="{
                left: `${timelineScale.pct(b.earliestArrival)}%`,
                width: `max(4px, ${timelineScale.pct(b.latestArrival) - timelineScale.pct(b.earliestArrival)}%)`
              }"
            />
            <span
              class="point-time mono"
              :class="{ 'is-flipped': timelineScale.pct(b.latestArrival) > 85 }"
              :style="{
                [timelineScale.pct(b.latestArrival) > 85 ? 'right' : 'left']:
                  timelineScale.pct(b.latestArrival) > 85
                    ? `calc(100% - ${timelineScale.pct(b.earliestArrival)}% + 8px)`
                    : `calc(${timelineScale.pct(b.latestArrival)}% + 8px)`
              }"
            >
              {{ formatNptRange(b.earliestArrival, b.latestArrival) }}
            </span>
          </div>
        </template>
      </div>
    </div>
  </UiPanel>
</template>

<style scoped>
.timeline-container {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.headline-banner {
  font-size: var(--fs-md);
  color: var(--text-2);
  line-height: var(--lh-normal);
  padding: var(--sp-2) var(--sp-3);
  background: var(--surface-2);
  border-radius: var(--r-sm);
  border-left: 3px solid var(--accent);
}

.saved-duration {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--ok);
}

.chart-grid {
  display: grid;
  grid-template-columns: 15rem minmax(0, 1fr);
  width: 100%;
  box-sizing: border-box;
}

.row-label {
  padding-right: var(--sp-3);
  display: flex;
  align-items: center;
  min-height: 2rem;
  border-bottom: 1px solid var(--border);
  min-width: 0;
}

.label-text {
  font-size: var(--fs-sm);
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.row-track {
  position: relative;
  min-height: 2rem;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
}

.header-label {
  border-bottom: 1px solid var(--border);
}

.header-track {
  height: 1.5rem;
  min-height: 1.5rem;
  border-bottom: 1px solid var(--border);
}

.tick-label {
  position: absolute;
  transform: translateX(-50%);
  font-size: var(--fs-xs);
  color: var(--text-3);
  white-space: nowrap;
}

.now-header-label {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  font-size: var(--fs-xs);
  color: var(--accent);
  font-weight: 600;
  white-space: nowrap;
}

.group-header {
  grid-column: 1 / -1;
  font-size: var(--fs-xs);
  font-weight: 600;
  text-transform: uppercase;
  color: var(--text-3);
  padding-top: var(--sp-3);
  padding-bottom: var(--sp-1);
}

.group-title-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.no-record-row {
  grid-column: 1 / -1;
  font-size: var(--fs-sm);
  color: var(--text-3);
  padding: var(--sp-2) 0;
}

.grid-line {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  background: var(--border);
  pointer-events: none;
}

.now-line {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--accent);
  z-index: 2;
  pointer-events: none;
}

.data-point {
  position: absolute;
  width: 10px;
  height: 10px;
  border-radius: var(--r-pill);
  transform: translate(-50%, -50%);
  top: 50%;
  z-index: 3;
}

.dot-published {
  background: var(--text-2);
}

.dot-danger {
  background: var(--danger);
}

.dot-watch {
  background: var(--watch);
}

.dot-info {
  background: var(--info);
}

.point-time {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  font-size: var(--fs-xs);
  color: var(--text-2);
  white-space: nowrap;
  z-index: 4;
}

.range-bar {
  position: absolute;
  height: 10px;
  border-radius: var(--r-pill);
  background: var(--watch-soft);
  border: 1px solid var(--watch);
  top: 50%;
  transform: translateY(-50%);
  z-index: 3;
  box-sizing: border-box;
}
</style>
