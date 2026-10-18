<template>
  <div class="station-strip-container">
    <div class="strip-header">
      <div class="header-title">
        <span class="icon-flow">⚡</span>
        <h2>Telemetry Corridor Gauges</h2>
        <span class="badge-corridor text-mono">{{ corridorName }}</span>
      </div>
      <div class="strip-summary text-mono">
        <span class="count-tag">{{ stations.length }} Gauges Monitored</span>
        <span v-if="silentCount > 0" class="silent-tag pulse-rapid">{{ silentCount }} SILENT</span>
      </div>
    </div>

    <!-- Horizontal scrollable strip of gauge tiles -->
    <div class="strip-scroll">
      <div class="strip-flow">
        <template v-for="(station, index) in sortedStations" :key="station.stationId">
          <StationTileComponent :station="station" />

          <!-- River flow connection between stations -->
          <div v-if="index < sortedStations.length - 1" class="reach-connector">
            <div class="connector-line"></div>
            <div class="connector-arrow">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <polyline points="9 18 15 12 9 6"></polyline>
              </svg>
            </div>
            <div class="reach-km text-mono">
              +{{ (sortedStations[index + 1].riverKm - station.riverKm).toFixed(0) }}km
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import StationTileComponent from '@/components/StationTile.vue'
import type { StationTile } from '@/stores/stationStore'

const props = defineProps<{
  stations: StationTile[]
  corridorName?: string
}>()

const corridorName = computed(() => props.corridorName || 'Bhote Koshi – Trishuli – Narayani')

const sortedStations = computed(() => {
  return [...props.stations].sort((a, b) => a.riverKm - b.riverKm)
})

const silentCount = computed(() => {
  return props.stations.filter(s => s.silent).length
})
</script>

<style scoped>
.station-strip-container {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  background: rgba(13, 17, 23, 0.6);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.35);
}

.strip-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.icon-flow {
  font-size: 16px;
  color: #38bdf8;
}

.header-title h2 {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: #e2e8f0;
}

.badge-corridor {
  font-size: 11px;
  background: rgba(56, 189, 248, 0.12);
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.25);
  padding: 1px 7px;
  border-radius: var(--radius-sm);
}

.strip-summary {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 11px;
}

.count-tag {
  color: var(--color-text-muted);
}

.silent-tag {
  background: rgba(239, 68, 68, 0.2);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.4);
  padding: 2px 7px;
  border-radius: var(--radius-sm);
  font-weight: 700;
}

.strip-scroll {
  overflow-x: auto;
  padding-bottom: 6px;
}

.strip-flow {
  display: inline-flex;
  align-items: center;
  min-width: 100%;
}

.reach-connector {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 var(--space-2);
  position: relative;
  min-width: 48px;
}

.connector-line {
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, rgba(56, 189, 248, 0.2), rgba(56, 189, 248, 0.6), rgba(56, 189, 248, 0.2));
  transform: translateY(-50%);
  z-index: 1;
}

.connector-arrow {
  position: relative;
  z-index: 2;
  background: var(--color-surface);
  color: #38bdf8;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  padding: 2px;
  border: 1px solid rgba(56, 189, 248, 0.3);
}

.reach-km {
  font-size: 9px;
  color: var(--color-text-muted);
  margin-top: 4px;
}
</style>
