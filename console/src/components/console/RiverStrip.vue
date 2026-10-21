<script setup lang="ts">
import { ref } from 'vue'
import { WifiOff, Waves, ChevronRight } from '@lucide/vue'
import { useConsoleStore } from '@/stores/console'
import { PROVENANCE } from '@/lib/labels'
import type { Tag } from '@/types/api'
import UiPanel from '@/components/ui/UiPanel.vue'
import UiBadge from '@/components/ui/UiBadge.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiEmptyState from '@/components/ui/UiEmptyState.vue'
import UiProvenance from '@/components/ui/UiProvenance.vue'
import UiSegmented from '@/components/ui/UiSegmented.vue'
import CorridorMap from '@/components/console/CorridorMap.vue'
import GaugeCard from '@/components/console/GaugeCard.vue'

const store = useConsoleStore()

const currentView = ref<'map' | 'diagram'>('map')

const viewOptions = [
  { value: 'map', label: 'Map' },
  { value: 'diagram', label: 'Diagram' },
]

const provenanceTags: Tag[] = ['P', 'D', 'A']
</script>

<template>
  <UiPanel
    title="Corridor"
    subtitle="Live river, gauges and the estimated flood wave, upstream → downstream"
  >
    <template #actions>
      <UiBadge
        v-if="store.silentCount > 0"
        tone="watch"
        :icon="WifiOff"
      >
        {{ store.silentCount }} silent
      </UiBadge>
      <UiSegmented
        v-model="currentView"
        label="Corridor view"
        :options="viewOptions"
      />
    </template>

    <!-- Map view: renders CorridorMap and nothing else -->
    <div v-if="currentView === 'map'" class="map-container">
      <CorridorMap />
    </div>

    <!-- Diagram view -->
    <div v-else class="diagram-container">
      <!-- Error state -->
      <UiEmptyState
        v-if="store.errors.state"
        tone="danger"
        title="Could not load the corridor"
        :body="store.errors.state"
      >
        <UiButton variant="secondary" @click="store.refreshState()">Retry</UiButton>
      </UiEmptyState>

      <!-- No run state -->
      <UiEmptyState
        v-else-if="!store.runId"
        :icon="Waves"
        title="No gauges yet"
        body="Start a run to load the corridor's gauges."
      />

      <!-- Gauges list -->
      <div v-else class="river-track">
        <template
          v-for="(station, index) in store.stationsSorted"
          :key="station.stationId"
        >
          <GaugeCard
            :station="station"
            :sim-now="store.simNow"
            :tag="store.readingTags[station.stationId] ?? null"
            class="station-card-item"
          />

          <div
            v-if="index < store.stationsSorted.length - 1"
            class="river-connector"
          >
            <div class="connector-line-row">
              <div class="connector-half-line" />
              <ChevronRight :size="14" aria-hidden="true" class="connector-icon" />
              <div class="connector-half-line" />
            </div>
            <span class="connector-dist tabular">
              {{ Math.round(store.stationsSorted[index + 1].riverKm - station.riverKm) }} km
            </span>
          </div>
        </template>
      </div>
    </div>

    <template #footer>
      <div class="provenance-legend">
        <div
          v-for="t in provenanceTags"
          :key="t"
          class="legend-item"
        >
          <UiProvenance :tag="t" />
          <span class="legend-label">{{ PROVENANCE[t].label }}</span>
        </div>
        <span class="legend-hint">Hover a letter for its meaning.</span>
      </div>
    </template>
  </UiPanel>
</template>

<style scoped>
.map-container {
  min-width: 0;
}

.diagram-container {
  min-width: 0;
}

.river-track {
  display: flex;
  flex-direction: row;
  align-items: stretch;
  overflow-x: auto;
  scroll-snap-type: x proximity;
  padding-bottom: var(--sp-2);
}

.station-card-item {
  flex: 1 1 0;
  min-width: 9.5rem;
  scroll-snap-align: start;
}

.river-connector {
  flex: 0 0 3.5rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 0 var(--sp-1);
}

.connector-line-row {
  width: 100%;
  display: flex;
  align-items: center;
}

.connector-half-line {
  flex: 1;
  height: 2px;
  background: var(--border-strong);
}

.connector-icon {
  color: var(--text-3);
  flex-shrink: 0;
}

.connector-dist {
  font-size: var(--fs-xs);
  color: var(--text-3);
  white-space: nowrap;
}

.provenance-legend {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  flex-wrap: wrap;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
}

.legend-label {
  font-size: var(--fs-xs);
  color: var(--text-2);
}

.legend-hint {
  font-size: var(--fs-xs);
  color: var(--text-3);
  margin-left: auto;
}
</style>
