<script setup lang="ts">
import { computed } from 'vue'
import { WifiOff, TrendingUp } from '@lucide/vue'
import { formatNpt, agoLabel } from '@/lib/time'
import type { StationTile, Tag } from '@/types/api'
import UiProvenance from '@/components/ui/UiProvenance.vue'
import UiBadge from '@/components/ui/UiBadge.vue'

const props = defineProps<{
  station: StationTile
  simNow: string | null
  tag: Tag | null
}>()

type CardKind = 'outage' | 'silent' | 'red' | 'amber' | 'ok'

const cardKind = computed<CardKind>(() => {
  if (props.station.state === 'TELEMETRY_OUTAGE') return 'outage'
  if (props.station.silent || props.station.state === 'SILENT') return 'silent'
  if (props.station.state === 'RED') return 'red'
  if (props.station.state === 'AMBER') return 'amber'
  return 'ok'
})

const isRising = computed(() => props.station.rateAlert || props.station.state === 'RATE_ALERT')

const statusLineText = computed(() => {
  switch (cardKind.value) {
    case 'ok':
      return props.station.lastObservedAt
        ? `Reporting · ${agoLabel(props.station.lastObservedAt, props.simNow)}`
        : 'Reporting'
    case 'amber':
      return `Above warning level (${props.station.warningM ?? '—'} m)`
    case 'red':
      return `Above danger level (${props.station.dangerM ?? '—'} m)`
    case 'silent':
      return props.station.lastObservedAt
        ? `Silent · last reading ${agoLabel(props.station.lastObservedAt, props.simNow)}`
        : 'Silent · no reading'
    case 'outage':
      return 'Shared network failure'
  }
})

const ariaLabelText = computed(() => {
  return `${props.station.stationId} ${props.station.name}: ${statusLineText.value}`
})

// Level bar calculations
const levelBar = computed(() => {
  const level = props.station.lastLevelM
  if (level === null || level === undefined) return null

  const warning = props.station.warningM
  const danger = props.station.dangerM
  const max = danger ?? (warning ? warning * 1.4 : Math.max(level * 1.5, 1))

  const fillPct = Math.min(100, Math.max(0, (level / max) * 100))
  const warningPct = warning ? Math.min(100, (warning / max) * 100) : null
  const dangerPct = danger ? Math.min(100, (danger / max) * 100) : null

  return { fillPct, warningPct, dangerPct }
})
</script>

<template>
  <article
    class="gauge-card"
    :class="[`kind-${cardKind}`]"
    :aria-label="ariaLabelText"
    :title="`Telemetry group: ${station.telemetryGroup ?? 'unknown'}`"
  >
    <div class="card-row-top">
      <div class="code-col">
        <span class="station-code mono">{{ station.stationId }}</span>
        <span class="station-km">km {{ station.riverKm }}</span>
      </div>
      <UiProvenance v-if="tag" :tag="tag" />
    </div>

    <div class="card-names">
      <h3 class="station-name">{{ station.name }}</h3>
      <span class="station-river">{{ station.river }}</span>
    </div>

    <div class="card-value-box">
      <!-- Outage -->
      <template v-if="cardKind === 'outage'">
        <span class="outage-title">Network outage</span>
        <span class="outage-desc">Not a flood signal (R4 · Network outage)</span>
      </template>

      <!-- Silent -->
      <template v-else-if="cardKind === 'silent'">
        <div class="silent-value">
          <WifiOff :size="18" aria-hidden="true" class="silent-icon" />
          <span class="silent-text">No data</span>
        </div>
        <span v-if="station.lastLevelM !== null" class="silent-last">
          Last {{ station.lastLevelM.toFixed(2) }} m at {{ formatNpt(station.lastObservedAt) }}
        </span>
      </template>

      <!-- Normal / Amber / Red with a reading -->
      <template v-else-if="station.lastLevelM !== null">
        <div class="reading-value">
          <span
            class="level-num mono"
            :class="[`val-${cardKind}`]"
          >
            {{ station.lastLevelM.toFixed(2) }}
          </span>
          <span class="level-unit">m</span>
        </div>
      </template>

      <!-- No reading yet -->
      <template v-else>
        <span class="no-reading">—</span>
      </template>
    </div>

    <!-- Level progress bar -->
    <div
      v-if="(cardKind === 'ok' || cardKind === 'amber' || cardKind === 'red') && levelBar"
      class="level-bar-track"
    >
      <div
        class="level-bar-fill"
        :class="[`bar-${cardKind}`]"
        :style="{ width: `${levelBar.fillPct}%` }"
      />
      <div
        v-if="levelBar.warningPct !== null"
        class="tick-mark tick-warning"
        :style="{ left: `${levelBar.warningPct}%` }"
      />
      <div
        v-if="levelBar.dangerPct !== null"
        class="tick-mark tick-danger"
        :style="{ left: `${levelBar.dangerPct}%` }"
      />
    </div>

    <!-- Status line -->
    <div class="card-status-line">
      <span
        class="status-text"
        :class="[`status-${cardKind}`]"
      >
        {{ statusLineText }}
      </span>
      <UiBadge
        v-if="isRising"
        tone="watch"
        :icon="TrendingUp"
      >
        Rising fast
      </UiBadge>
    </div>

    <!-- Footer -->
    <footer class="card-footer">
      <span v-if="station.warningM !== null || station.dangerM !== null">
        Warn {{ station.warningM ?? '—' }} m · Danger {{ station.dangerM ?? '—' }} m
      </span>
      <span v-else>
        Thresholds not published
      </span>
    </footer>
  </article>
</template>

<style scoped>
.gauge-card {
  background: var(--surface-1);
  border: 1px solid var(--border);
  border-top-width: 3px;
  border-radius: var(--r-sm);
  padding: var(--sp-3);
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  box-sizing: border-box;
}

.kind-ok {
  border-top-color: var(--ok);
}

.kind-amber {
  border-top-color: var(--watch);
}

.kind-red {
  border-top-color: var(--danger);
}

.kind-silent {
  background: var(--watch-soft);
  border: 1px dashed var(--watch-line);
  border-top: 3px solid var(--watch);
}

.kind-outage {
  border-top-color: var(--outage);
}

.card-row-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.code-col {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
}

.station-code {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text);
}

.station-km {
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.card-names {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.station-name {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: var(--lh-tight);
}

.station-river {
  font-size: var(--fs-xs);
  color: var(--text-3);
  line-height: var(--lh-normal);
}

.card-value-box {
  display: flex;
  flex-direction: column;
  min-height: 2.5rem;
  justify-content: center;
}

.outage-title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--outage);
  line-height: var(--lh-tight);
}

.outage-desc {
  font-size: var(--fs-xs);
  color: var(--text-2);
  line-height: var(--lh-normal);
}

.silent-value {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
}

.silent-icon {
  color: var(--watch);
  flex-shrink: 0;
}

.silent-text {
  font-size: var(--fs-xl);
  font-weight: 600;
  color: var(--watch);
  line-height: var(--lh-tight);
}

.silent-last {
  font-size: var(--fs-xs);
  color: var(--text-2);
  line-height: var(--lh-normal);
}

.reading-value {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.level-num {
  font-size: var(--fs-2xl);
  font-weight: 600;
  line-height: var(--lh-tight);
}

.level-unit {
  font-size: var(--fs-sm);
  color: var(--text-3);
}

.val-ok {
  color: var(--text);
}

.val-amber {
  color: var(--watch);
}

.val-red {
  color: var(--danger);
}

.no-reading {
  font-size: var(--fs-2xl);
  color: var(--text-3);
}

.level-bar-track {
  height: 6px;
  border-radius: var(--r-pill);
  background: var(--surface-3);
  position: relative;
  overflow: visible;
  margin: var(--sp-1) 0;
}

.level-bar-fill {
  height: 100%;
  border-radius: var(--r-pill);
  transition: width 200ms ease;
}

.bar-ok {
  background: var(--ok);
}

.bar-amber {
  background: var(--watch);
}

.bar-red {
  background: var(--danger);
}

.tick-mark {
  position: absolute;
  width: 2px;
  height: 12px;
  top: -3px;
  transform: translateX(-50%);
  pointer-events: none;
}

.tick-warning {
  background: var(--watch);
}

.tick-danger {
  background: var(--danger);
}

.card-status-line {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}

.status-text {
  font-size: var(--fs-xs);
  line-height: var(--lh-normal);
}

.status-ok {
  color: var(--text-2);
}

.status-amber {
  color: var(--watch);
}

.status-red {
  color: var(--danger);
}

.status-silent {
  color: var(--watch);
}

.status-outage {
  color: var(--outage);
}

.card-footer {
  font-size: var(--fs-xs);
  color: var(--text-3);
  margin-top: auto;
  padding-top: var(--sp-1);
  border-top: 1px solid var(--border);
  line-height: var(--lh-normal);
}
</style>
