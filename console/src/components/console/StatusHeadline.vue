<script setup lang="ts">
import { computed } from 'vue'
import { Search, TrendingUp } from '@lucide/vue'
import { useConsoleStore, type ForecasterAction } from '@/stores/console'
import { LEVELS, ruleLabel } from '@/lib/labels'
import { formatNpt, formatNptRange } from '@/lib/time'
import { eventDescription } from '@/lib/payload'
import UiBadge from '@/components/ui/UiBadge.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiStat from '@/components/ui/UiStat.vue'

defineProps<{
  flash?: boolean
}>()

const emit = defineEmits<{
  (e: 'why'): void
  (e: 'act', action: ForecasterAction): void
}>()

const store = useConsoleStore()

const currentLevelConfig = computed(() => LEVELS[store.level])

const containerToneClass = computed(() => {
  if (!store.runId) return 'tone-no-run'
  return `tone-${store.level.toLowerCase()}`
})

const detailText = computed(() => {
  if (!store.runId) {
    return 'Choose a scenario in the simulation bar to replay it.'
  }
  if (store.level === 'WATCH' || store.level === 'WARNING') {
    const ev = store.currentLevelEvent
    let text = ev
      ? `Raised ${formatNpt(ev.at)} NPT by ${ruleLabel(ev.ruleId)}.`
      : store.incident
        ? `Opened ${formatNpt(store.incident.openedAt)} NPT.`
        : ''
    if (store.incident?.anchorStation) {
      text += ` Anchor gauge ${store.incident.anchorStation}.`
    }
    return text
  }
  if (store.level === 'STAND_DOWN') {
    return store.incident?.closeReason || LEVELS.STAND_DOWN.detail
  }
  // NORMAL with run
  const silentText =
    store.silentCount > 0
      ? ` ${store.silentCount} gauge${store.silentCount > 1 ? 's' : ''} silent — being watched.`
      : ' All gauges reporting.'
  return `No open incident.${silentText}`
})

const eventDescriptionText = computed(() => {
  if (store.level === 'WATCH' || store.level === 'WARNING') {
    const ev = store.currentLevelEvent
    return ev ? eventDescription(ev.payloadJson) : null
  }
  return null
})

const nextArrivalData = computed(() => {
  const n = store.nextArrival
  if (!n) {
    return {
      value: '—',
      hint: 'Waiting for arrival windows',
      tone: undefined,
    }
  }
  const val = n.minFromNow <= 0 ? 'Arriving now' : `in ${n.minFromNow}–${n.maxFromNow} min`
  const hint = `${n.band.siteName} · ${formatNptRange(n.band.earliestArrival, n.band.latestArrival)}`
  const tone = n.minFromNow <= 15 ? ('danger' as const) : ('watch' as const)
  return { value: val, hint, tone }
})

const confirmedStatData = computed(() => {
  const c = store.actionCounts
  const val = `${c.confirmed} / ${c.total}`
  const tone = c.total > 0 && c.confirmed === c.total ? ('ok' as const) : undefined
  const hint = `${c.needsAttention} need attention`
  return { value: val, hint, tone }
})

const unresolvedStatData = computed(() => {
  const c = store.actionCounts
  const val = String(c.unresolved)
  const tone = c.unresolved > 0 ? ('danger' as const) : undefined
  const hint = c.unresolved > 0 ? 'Escalation ladder exhausted' : 'None'
  return { value: val, hint, tone }
})

const isIncidentActive = computed(() => {
  return store.incident && (store.level === 'WATCH' || store.level === 'WARNING')
})
</script>

<template>
  <div
    class="status-headline"
    :class="[containerToneClass, { 'is-flashing': flash }]"
  >
    <div class="headline-left">
      <div class="chips-row">
        <UiBadge
          solid
          size="md"
          :tone="currentLevelConfig.tone"
        >
          {{ currentLevelConfig.label.toUpperCase() }}
        </UiBadge>
        <UiBadge
          v-if="store.incident?.nonRain"
          tone="watch"
        >
          Non-rain flood
        </UiBadge>
        <UiBadge
          v-if="store.level === 'WATCH'"
          tone="neutral"
        >
          Internal only
        </UiBadge>
      </div>

      <h1 class="headline-title">
        {{ !store.runId ? 'No run in progress' : currentLevelConfig.headline }}
      </h1>

      <div class="headline-details">
        <p class="detail-primary">{{ detailText }}</p>
        <p v-if="eventDescriptionText" class="detail-secondary">{{ eventDescriptionText }}</p>
      </div>

      <div v-if="isIncidentActive" class="headline-actions">
        <UiButton
          variant="secondary"
          :icon="Search"
          @click="emit('why')"
        >
          Why this level?
        </UiButton>

        <template v-if="store.level === 'WATCH'">
          <UiButton
            variant="secondary"
            :icon="TrendingUp"
            @click="emit('act', 'escalate')"
          >
            Escalate to warning…
          </UiButton>
          <UiButton
            variant="ghost"
            @click="emit('act', 'cancel-watch')"
          >
            Cancel watch…
          </UiButton>
        </template>

        <UiButton
          variant="danger"
          @click="emit('act', 'stand-down')"
        >
          Stand down…
        </UiButton>
      </div>
    </div>

    <div v-if="isIncidentActive" class="headline-right">
      <UiStat
        label="Next arrival"
        :value="nextArrivalData.value"
        :hint="nextArrivalData.hint"
        :tone="nextArrivalData.tone"
      />
      <UiStat
        label="Confirmed"
        :value="confirmedStatData.value"
        :hint="confirmedStatData.hint"
        :tone="confirmedStatData.tone"
      />
      <UiStat
        label="Unresolved"
        :value="unresolvedStatData.value"
        :hint="unresolvedStatData.hint"
        :tone="unresolvedStatData.tone"
      />
    </div>
  </div>
</template>

<style scoped>
.status-headline {
  border-radius: var(--r-md);
  box-shadow: var(--shadow-card);
  padding: var(--sp-4) var(--sp-5);
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--sp-4) var(--sp-6);
  align-items: center;
  box-sizing: border-box;
}

.tone-no-run {
  background: var(--surface-1);
  border: 1px solid var(--border);
  border-left: 6px solid var(--border-strong);
}

.tone-warning {
  background: var(--danger-soft);
  border: 1px solid var(--danger-line);
  border-left: 6px solid var(--danger-solid);
}

.tone-watch {
  background: var(--watch-soft);
  border: 1px solid var(--watch-line);
  border-left: 6px solid var(--watch-solid);
}

.tone-normal {
  background: var(--ok-soft);
  border: 1px solid var(--ok-line);
  border-left: 6px solid var(--ok);
}

.tone-stand_down {
  background: var(--info-soft);
  border: 1px solid var(--info-line);
  border-left: 6px solid var(--info);
}

.is-flashing {
  outline: 3px solid transparent;
  animation: level-flash 0.6s 3;
}

.headline-left {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  min-width: 0;
}

.chips-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.headline-title {
  font-size: var(--fs-xl);
  font-weight: 600;
  color: var(--text);
  line-height: var(--lh-tight);
}

.headline-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-primary {
  font-size: var(--fs-md);
  color: var(--text-2);
  line-height: var(--lh-normal);
}

.detail-secondary {
  font-size: var(--fs-sm);
  color: var(--text-3);
  line-height: var(--lh-normal);
}

.headline-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
  flex-wrap: wrap;
}

.headline-right {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  padding-left: var(--sp-6);
  border-left: 1px solid currentColor;
}

.tone-warning .headline-right {
  border-left-color: var(--danger-line);
}

.tone-watch .headline-right {
  border-left-color: var(--watch-line);
}

.tone-normal .headline-right {
  border-left-color: var(--ok-line);
}

.tone-stand_down .headline-right {
  border-left-color: var(--info-line);
}

@media (max-width: 1023px) {
  .status-headline {
    grid-template-columns: minmax(0, 1fr);
  }
  .headline-right {
    border-left: none;
    border-top: 1px solid currentColor;
    padding-left: 0;
    padding-top: var(--sp-4);
    flex-wrap: wrap;
  }
}
</style>
