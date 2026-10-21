<script setup lang="ts">
import { computed } from 'vue'
import { FlaskConical, Play, Pause, Zap, FileText, CircleAlert } from '@lucide/vue'
import { useConsoleStore } from '@/stores/console'
import { SCENARIOS, humanizeKey } from '@/lib/labels'
import { formatNpt, formatNptDate } from '@/lib/time'
import UiBadge from '@/components/ui/UiBadge.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiSegmented from '@/components/ui/UiSegmented.vue'

defineEmits<{
  (e: 'open-scenario'): void
  (e: 'open-inject'): void
  (e: 'open-report'): void
}>()

const store = useConsoleStore()

const scenarioTitle = computed(() => {
  if (!store.run) return ''
  const info = SCENARIOS.find((s) => s.id === store.run?.scenario)
  return info ? `${info.code} · ${info.title}` : store.run.scenario
})

const baseSpeedOptions = [
  { value: 1, label: '1×' },
  { value: 2, label: '2×' },
  { value: 10, label: '10×' },
  { value: 60, label: '60×' },
]

const speedOptions = computed(() => {
  const current = Math.round(store.run?.speed ?? 0)
  if (current > 0 && !baseSpeedOptions.some((o) => o.value === current)) {
    return [...baseSpeedOptions, { value: current, label: `${current}×` }].sort(
      (a, b) => a.value - b.value,
    )
  }
  return baseSpeedOptions
})

function handleSpeedChange(speed: string | number | boolean) {
  void store.setSpeed(Number(speed))
}
</script>

<template>
  <div class="sim-bar sim-stripes">
    <UiBadge tone="neutral" :icon="FlaskConical">Training — not live</UiBadge>

    <template v-if="!store.run">
      <span class="no-run-text">No run in progress.</span>
      <UiButton
        variant="primary"
        :icon="Play"
        @click="$emit('open-scenario')"
      >
        Choose scenario
      </UiButton>
    </template>

    <template v-else>
      <div class="scenario-info">
        <span class="scenario-name">{{ scenarioTitle }}</span>
        <UiButton
          variant="ghost"
          size="sm"
          @click="$emit('open-scenario')"
        >
          Change
        </UiButton>
      </div>

      <div class="bar-divider" />

      <UiButton
        variant="secondary"
        icon-only
        :icon="store.isPlaying ? Pause : Play"
        :label="store.isPlaying ? 'Pause' : 'Play'"
        @click="store.control(store.isPlaying ? 'pause' : 'play')"
      />

      <UiSegmented
        label="Playback speed"
        :model-value="Math.round(store.run.speed)"
        :options="speedOptions"
        @update:model-value="handleSpeedChange"
      />

      <div class="clock-display">
        <time class="clock-time mono">{{ formatNpt(store.simNow, true) }}</time>
        <span class="clock-tz">NPT</span>
        <span class="clock-date">{{ formatNptDate(store.simNow) }}</span>
        <UiBadge
          v-if="store.run.status !== 'RUNNING'"
          tone="neutral"
        >
          {{ humanizeKey(store.run.status) }}
        </UiBadge>
        <UiBadge
          v-else-if="Math.round(store.run.speed) === 2 && !store.manualSpeed"
          tone="neutral"
          class="speed-hint"
        >
          Scenario speed: 2× (event window) · click 10× or 60× to fast-forward
        </UiBadge>
      </div>

      <div class="spacer" />

      <UiButton
        variant="secondary"
        :icon="Zap"
        @click="$emit('open-inject')"
      >
        Inject event
      </UiButton>

      <UiButton
        variant="secondary"
        :icon="FileText"
        @click="$emit('open-report')"
      >
        Replay report
      </UiButton>
    </template>

    <UiBadge
      v-if="store.errors.run"
      tone="danger"
      :icon="CircleAlert"
      :title="store.errors.run"
    >
      Run control failed
    </UiBadge>

    <UiButton
      variant="ghost"
      size="sm"
      class="back-to-live-btn"
      @click="store.goLive()"
    >
      Back to live
    </UiButton>
  </div>
</template>

<style scoped>
.sim-bar {
  position: sticky;
  top: var(--topbar-h);
  z-index: 29;
  min-height: var(--simbar-h);
  padding: var(--sp-2) var(--sp-6);
  border-bottom: 1px solid var(--border);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-3) var(--sp-4);
  box-sizing: border-box;
}

.no-run-text {
  font-size: var(--fs-md);
  color: var(--text-2);
}

.scenario-info {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.scenario-name {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--text);
}

.bar-divider {
  width: 1px;
  height: 1.5rem;
  background: var(--border-strong);
}

.clock-display {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
}

.clock-time {
  font-size: var(--fs-xl);
  font-weight: 600;
  color: var(--text);
}

.clock-tz {
  font-size: var(--fs-xs);
  color: var(--text-3);
  font-weight: 600;
}

.clock-date {
  font-size: var(--fs-sm);
  color: var(--text-2);
}

.spacer {
  flex: 1;
}

.back-to-live-btn {
  margin-left: auto;
}
</style>
