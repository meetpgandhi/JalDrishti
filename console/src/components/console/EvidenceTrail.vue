<script setup lang="ts">
import { computed } from 'vue'
import { ChevronRight } from '@lucide/vue'
import { useConsoleStore } from '@/stores/console'
import { eventLabel, ruleLabel } from '@/lib/labels'
import { formatNpt } from '@/lib/time'
import { eventDescription } from '@/lib/payload'
import UiPanel from '@/components/ui/UiPanel.vue'
import UiBadge from '@/components/ui/UiBadge.vue'
import UiProvenance from '@/components/ui/UiProvenance.vue'
import UiEmptyState from '@/components/ui/UiEmptyState.vue'
import UiButton from '@/components/ui/UiButton.vue'

const emit = defineEmits<{
  (e: 'open', payload: { incidentId: string; seq: number | null }): void
}>()

const store = useConsoleStore()

const reversedEvents = computed(() => {
  return [...store.timelineEvents].reverse()
})
</script>

<template>
  <UiPanel
    title="Evidence trail"
    subtitle="Every decision the rule engine made. Select one to see why."
    flush
  >
    <!-- Error state -->
    <UiEmptyState
      v-if="store.errors.events"
      tone="danger"
      title="Could not load decisions"
      :body="store.errors.events"
    >
      <UiButton variant="secondary" @click="store.refreshEvents()">Retry</UiButton>
    </UiEmptyState>

    <!-- No run state -->
    <UiEmptyState
      v-else-if="!store.runId"
      title="No decisions yet"
      body="Rule decisions appear here as the simulation runs."
    />

    <!-- No events state -->
    <UiEmptyState
      v-else-if="reversedEvents.length === 0"
      title="No rules have fired"
      body="The corridor is quiet. Silence, height, rate and field reports are checked continuously."
    />

    <!-- Events list -->
    <ul v-else class="evidence-list">
      <li
        v-for="e in reversedEvents"
        :key="e.seq ?? e.at"
      >
        <button
          type="button"
          class="evidence-item-btn"
          @click="emit('open', { incidentId: e.incidentId, seq: e.seq })"
        >
          <span class="evidence-time mono">{{ formatNpt(e.at) }}</span>

          <div class="evidence-main">
            <div class="evidence-heading">
              <span class="evidence-label">{{ eventLabel(e.type, e.ruleId) }}</span>
              <UiBadge v-if="e.ruleId" tone="neutral">
                {{ ruleLabel(e.ruleId) }}
              </UiBadge>
            </div>
            <p v-if="eventDescription(e.payloadJson)" class="evidence-desc">
              {{ eventDescription(e.payloadJson) }}
            </p>
          </div>

          <div class="evidence-meta">
            <UiProvenance :tag="e.tag" />
            <ChevronRight :size="16" aria-hidden="true" class="chevron-icon" />
          </div>
        </button>
      </li>
    </ul>
  </UiPanel>
</template>

<style scoped>
.evidence-list {
  list-style: none;
  max-height: 22rem;
  overflow-y: auto;
  margin: 0;
  padding: 0;
}

.evidence-item-btn {
  width: 100%;
  display: grid;
  grid-template-columns: 3.5rem 1fr auto;
  gap: var(--sp-2);
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--border);
  text-align: left;
  background: transparent;
  border-left: none;
  border-right: none;
  border-top: none;
  cursor: pointer;
  align-items: start;
  transition: background-color 120ms;
}

.evidence-item-btn:hover {
  background: var(--surface-2);
}

.evidence-time {
  font-size: var(--fs-sm);
  color: var(--text-2);
  line-height: var(--lh-tight);
  margin-top: 2px;
}

.evidence-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.evidence-heading {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.evidence-label {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text);
  line-height: var(--lh-tight);
}

.evidence-desc {
  font-size: var(--fs-sm);
  color: var(--text-2);
  line-height: var(--lh-normal);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.evidence-meta {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
  margin-top: 2px;
}

.chevron-icon {
  color: var(--text-3);
  flex-shrink: 0;
}
</style>
