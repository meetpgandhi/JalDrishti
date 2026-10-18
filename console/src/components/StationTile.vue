<template>
  <div class="station-tile" :class="[tileStateClass, { 'is-silent': station.silent, 'is-rate-alert': station.rateAlert }]">
    <!-- Header: Code, River km, Provenance Badge -->
    <div class="tile-header">
      <div class="station-identity">
        <span class="station-code">{{ station.stationId }}</span>
        <span class="station-km text-mono">km {{ station.riverKm.toFixed(0) }}</span>
      </div>
      <div class="tile-badges">
        <span v-if="station.tag" :class="['badge-provenance', `badge-${station.tag.toLowerCase()}`]">
          [{{ station.tag }}]
        </span>
        <span class="heartbeat-dot" :class="heartbeatClass"></span>
      </div>
    </div>

    <!-- Name & River -->
    <div class="station-meta">
      <div class="station-name">{{ station.name }}</div>
      <div class="station-river text-muted">{{ station.river }}</div>
    </div>

    <!-- Water Level Metric -->
    <div class="level-display">
      <div class="level-value text-mono">
        <span v-if="station.lastLevelM !== null && station.lastLevelM !== undefined" class="metric-num">
          {{ station.lastLevelM.toFixed(2) }}
        </span>
        <span v-else class="metric-empty">--</span>
        <span class="metric-unit">m</span>
      </div>
      <div class="state-pill" :class="pillStateClass">
        {{ stateLabel }}
      </div>
    </div>

    <!-- Threshold Progress / Indicators -->
    <div class="thresholds-row text-mono">
      <div v-if="station.warningM" class="threshold-item">
        <span class="th-label">WARN</span>
        <span class="th-val">{{ station.warningM.toFixed(1) }}m</span>
      </div>
      <div v-if="station.dangerM" class="threshold-item danger">
        <span class="th-label">DANGER</span>
        <span class="th-val">{{ station.dangerM.toFixed(1) }}m</span>
      </div>
      <div v-if="station.telemetryGroup" class="telemetry-info text-muted">
        {{ station.telemetryGroup }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { StationTile } from '@/stores/stationStore'

const props = defineProps<{
  station: StationTile
}>()

const tileStateClass = computed(() => {
  if (props.station.state === 'TELEMETRY_OUTAGE') return 'state-outage'
  if (props.station.silent) return 'state-silent'
  if (props.station.rateAlert) return 'state-rate-alert'
  if (props.station.state === 'RED') return 'state-red'
  if (props.station.state === 'AMBER') return 'state-amber'
  return 'state-ok'
})

const pillStateClass = computed(() => {
  if (props.station.state === 'TELEMETRY_OUTAGE') return 'pill-outage'
  if (props.station.silent) return 'pill-silent'
  if (props.station.rateAlert) return 'pill-rate-alert'
  if (props.station.state === 'RED') return 'pill-red'
  if (props.station.state === 'AMBER') return 'pill-amber'
  return 'pill-ok'
})

const heartbeatClass = computed(() => {
  if (props.station.silent) return 'pulse-dead'
  if (props.station.rateAlert) return 'pulse-rapid'
  return 'pulse-live'
})

const stateLabel = computed(() => {
  if (props.station.state === 'TELEMETRY_OUTAGE') return 'OUTAGE'
  if (props.station.silent) return 'SILENT'
  if (props.station.rateAlert) return 'RATE ALERT'
  if (props.station.state === 'RED') return 'DANGER'
  if (props.station.state === 'AMBER') return 'WARNING'
  return 'OK'
})
</script>

<style scoped>
.station-tile {
  background: rgba(22, 27, 34, 0.85);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(48, 54, 61, 0.7);
  border-radius: var(--radius-md);
  padding: var(--space-3) var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-width: 175px;
  position: relative;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
}

.station-tile:hover {
  transform: translateY(-2px);
  border-color: rgba(110, 118, 129, 0.5);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.4);
}

.tile-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.station-identity {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
}

.station-code {
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: 0.04em;
}

.station-km {
  font-size: 11px;
  color: var(--color-text-muted);
}

.tile-badges {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.heartbeat-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}

.pulse-live {
  background: #10b981;
  box-shadow: 0 0 8px #10b981;
}

.pulse-rapid {
  background: #f59e0b;
  box-shadow: 0 0 10px #f59e0b;
  animation: pulse-fast 0.6s infinite alternate;
}

.pulse-dead {
  background: #ef4444;
  box-shadow: 0 0 8px #ef4444;
  animation: pulse-slow 2s infinite ease-in-out;
}

@keyframes pulse-fast {
  from { opacity: 0.3; transform: scale(0.85); }
  to { opacity: 1; transform: scale(1.2); }
}

@keyframes pulse-slow {
  0%, 100% { opacity: 0.2; }
  50% { opacity: 1; }
}

.station-meta {
  display: flex;
  flex-direction: column;
}

.station-name {
  font-size: 13px;
  font-weight: 600;
  color: #f0f6fc;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.station-river {
  font-size: 11px;
}

.level-display {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: var(--space-1) 0;
}

.level-value {
  display: flex;
  align-items: baseline;
}

.metric-num {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #ffffff;
}

.metric-empty {
  font-size: 20px;
  color: var(--color-text-muted);
}

.metric-unit {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-left: 2px;
}

.state-pill {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 9999px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.pill-ok {
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.pill-amber {
  background: rgba(245, 158, 11, 0.15);
  color: #fbbf24;
  border: 1px solid rgba(245, 158, 11, 0.35);
}

.pill-red {
  background: rgba(239, 68, 68, 0.18);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.4);
}

.pill-rate-alert {
  background: rgba(245, 158, 11, 0.2);
  color: #f59e0b;
  border: 1px solid #f59e0b;
  animation: pulse-fast 1s infinite alternate;
}

.pill-silent {
  background: rgba(239, 68, 68, 0.2);
  color: #fca5a5;
  border: 1px solid rgba(239, 68, 68, 0.6);
  animation: pulse-slow 2s infinite ease-in-out;
}

.pill-outage {
  background: rgba(148, 163, 184, 0.15);
  color: #94a3b8;
  border: 1px solid rgba(148, 163, 184, 0.3);
}

/* State border accents */
.state-ok { border-left: 3px solid #10b981; }
.state-amber { border-left: 3px solid #f59e0b; }
.state-red { border-left: 3px solid #ef4444; }
.state-rate-alert { border-left: 3px solid #f59e0b; box-shadow: 0 0 14px rgba(245, 158, 11, 0.25); }
.state-silent {
  border: 1px solid rgba(239, 68, 68, 0.65);
  border-left: 4px solid #ef4444;
  background: rgba(30, 20, 25, 0.9);
  animation: pulse-tile-silent 2.5s infinite ease-in-out;
}

@keyframes pulse-tile-silent {
  0%, 100% { border-color: rgba(239, 68, 68, 0.7); box-shadow: 0 0 10px rgba(239, 68, 68, 0.2); }
  50% { border-color: rgba(239, 68, 68, 0.2); box-shadow: none; }
}

.state-outage {
  border-left: 3px solid #94a3b8;
  opacity: 0.75;
}

.thresholds-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 10px;
  padding-top: var(--space-1);
  border-top: 1px solid rgba(48, 54, 61, 0.4);
}

.threshold-item {
  display: flex;
  gap: 3px;
  color: #94a3b8;
}

.threshold-item.danger {
  color: #f87171;
}

.th-label {
  opacity: 0.7;
}

.telemetry-info {
  font-size: 9px;
}
</style>
