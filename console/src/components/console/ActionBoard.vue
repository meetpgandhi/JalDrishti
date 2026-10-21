<script setup lang="ts">
import { ref, computed } from 'vue'
import { ListChecks, CircleCheck } from '@lucide/vue'
import { useConsoleStore } from '@/stores/console'
import { ACTION_STATES, ACTOR_TYPES, describeAction } from '@/lib/labels'
import { formatNpt, untilLabel, agoLabel } from '@/lib/time'
import type { DispatchedAction } from '@/types/api'
import UiPanel from '@/components/ui/UiPanel.vue'
import UiSegmented from '@/components/ui/UiSegmented.vue'
import UiBadge from '@/components/ui/UiBadge.vue'
import UiEmptyState from '@/components/ui/UiEmptyState.vue'
import UiButton from '@/components/ui/UiButton.vue'

const store = useConsoleStore()

const currentFilter = ref<'attention' | 'all'>('attention')

const filterOptions = computed(() => [
  { value: 'attention', label: 'Needs attention', count: store.actionCounts.needsAttention },
  { value: 'all', label: 'All', count: store.actionCounts.total },
])

const displayedActions = computed(() => {
  if (currentFilter.value === 'all') {
    return store.actionsSorted
  }
  return store.actionsSorted.filter((a) => ACTION_STATES[a.state].needsAttention)
})

function getRowBorderTone(action: DispatchedAction): string {
  const group = store.actionGroup(action)
  if (group === 'unresolved' || group === 'late') {
    return 'danger'
  }
  return ACTION_STATES[action.state].tone
}

function handleFilterChange(val: string | number | boolean) {
  currentFilter.value = val as 'attention' | 'all'
}
</script>

<template>
  <UiPanel
    title="Who must act"
    subtitle="One row per dispatched action, sorted by what needs you first."
    flush
  >
    <template #actions>
      <UiSegmented
        label="Filter actions"
        :model-value="currentFilter"
        :options="filterOptions"
        @update:model-value="handleFilterChange"
      />
    </template>

    <!-- Error state -->
    <UiEmptyState
      v-if="store.errors.actions"
      tone="danger"
      title="Could not load actions"
      :body="store.errors.actions"
    >
      <UiButton variant="secondary" @click="store.refreshActions()">Retry</UiButton>
    </UiEmptyState>

    <!-- No incident state -->
    <UiEmptyState
      v-else-if="!store.incident"
      :icon="ListChecks"
      title="No actions yet"
      body="When a watch or warning is raised, each duty-holder's action appears here with its deadline."
    />

    <!-- All confirmed empty state under attention filter -->
    <UiEmptyState
      v-else-if="currentFilter === 'attention' && displayedActions.length === 0 && store.actionCounts.total > 0"
      :icon="CircleCheck"
      title="Everyone has confirmed"
      body="All dispatched actions have been acknowledged."
    />

    <!-- Main table & summary -->
    <div v-else class="actions-content">
      <div class="summary-row">
        <UiBadge
          v-if="store.actionCounts.confirmed > 0"
          tone="ok"
        >
          {{ store.actionCounts.confirmed }} confirmed
        </UiBadge>
        <UiBadge
          v-if="store.actionCounts.waiting > 0"
          tone="info"
        >
          {{ store.actionCounts.waiting }} waiting
        </UiBadge>
        <UiBadge
          v-if="store.actionCounts.escalating > 0"
          tone="watch"
        >
          {{ store.actionCounts.escalating }} escalating
        </UiBadge>
        <UiBadge
          v-if="store.actionCounts.unresolved > 0"
          tone="danger"
        >
          {{ store.actionCounts.unresolved }} unresolved
        </UiBadge>
      </div>

      <div class="table-wrapper">
        <table class="actions-table">
          <thead>
            <tr>
              <th>Status</th>
              <th>Deadline</th>
              <th>Site</th>
              <th>Duty-holder</th>
              <th>Action</th>
              <th>Sent</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="a in displayedActions"
              :key="a.id"
              class="action-row"
            >
              <!-- Status -->
              <td
                class="cell-status"
                :style="{ boxShadow: `inset 3px 0 0 var(--${getRowBorderTone(a)})` }"
              >
                <div class="status-stack">
                  <UiBadge :tone="ACTION_STATES[a.state].tone">
                    {{ ACTION_STATES[a.state].label }}
                  </UiBadge>
                  <span
                    v-if="store.actionGroup(a) === 'late'"
                    class="late-tag"
                  >
                    Past deadline
                  </span>
                </div>
              </td>

              <!-- Deadline -->
              <td class="cell-deadline">
                <template v-if="a.deadlineAt">
                  <div class="time-primary">
                    <span class="mono">{{ formatNpt(a.deadlineAt) }}</span>
                  </div>
                  <span
                    class="time-secondary"
                    :class="{ 'is-late': store.actionGroup(a) === 'late' }"
                  >
                    {{ untilLabel(a.deadlineAt, store.simNow) }}
                  </span>
                </template>
                <template v-else>
                  <span class="immediate-badge">Immediate</span>
                </template>
              </td>

              <!-- Site -->
              <td class="cell-site">
                <span class="site-name">{{ store.siteName(store.holder(a.dutyHolderId)?.siteId) }}</span>
              </td>

              <!-- Duty-holder -->
              <td class="cell-holder">
                <template v-if="store.holder(a.dutyHolderId)">
                  <span class="holder-name">{{ store.holder(a.dutyHolderId)!.displayName }}</span>
                  <span class="holder-role">{{ ACTOR_TYPES[store.holder(a.dutyHolderId)!.actorType] }}</span>
                </template>
                <template v-else>
                  <span class="holder-name mono">{{ a.dutyHolderId.slice(0, 8) }} (unknown)</span>
                </template>
              </td>

              <!-- Action -->
              <td class="cell-action">
                <span class="action-desc">{{ describeAction(a.actionCode) }}</span>
                <span class="action-code mono">{{ a.actionCode }}</span>
              </td>

              <!-- Sent -->
              <td class="cell-sent">
                <div class="time-primary">
                  <span class="mono">{{ formatNpt(a.sentAt) }}</span>
                </div>
                <span class="time-secondary">
                  {{ agoLabel(a.sentAt, store.simNow) }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </UiPanel>
</template>

<style scoped>
.actions-content {
  display: flex;
  flex-direction: column;
}

.summary-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--border);
}

.table-wrapper {
  overflow-x: auto;
}

.actions-table {
  min-width: 760px;
}

.action-row:hover {
  background: var(--surface-2);
}

.cell-status {
  padding-left: var(--sp-3);
}

.status-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-start;
}

.late-tag {
  font-size: var(--fs-xs);
  color: var(--danger);
  font-weight: 600;
}

.cell-deadline,
.cell-sent {
  white-space: nowrap;
}

.time-primary {
  font-weight: 500;
  color: var(--text);
}

.time-secondary {
  font-size: var(--fs-xs);
  color: var(--text-2);
}

.time-secondary.is-late {
  color: var(--danger);
  font-weight: 600;
}

.immediate-badge {
  font-weight: 600;
  color: var(--danger);
}

.cell-site {
  white-space: nowrap;
}

.site-name {
  font-weight: 500;
  color: var(--text);
}

.cell-holder {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.holder-name {
  font-weight: 500;
  color: var(--text);
}

.holder-role {
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.cell-action {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.action-desc {
  color: var(--text);
}

.action-code {
  font-size: var(--fs-xs);
  color: var(--text-3);
}
</style>
