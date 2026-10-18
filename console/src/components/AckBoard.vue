<template>
  <div class="ack-board card">
    <div class="board-header">
      <div class="header-title">
        <h3>Duty Acknowledgement Board</h3>
        <span class="badge badge-d">[D]</span>
      </div>
      <div class="board-filters">
        <label class="filter-toggle">
          <input type="checkbox" v-model="pendingOnly" />
          <span>Pending Confirmation Only ({{ pendingCount }})</span>
        </label>
        <button class="refresh-btn" @click="fetchActions" title="Refresh actions">↻</button>
      </div>
    </div>

    <div v-if="loading" class="empty-state text-mono muted">
      Loading corridor duty actions…
    </div>

    <div v-else-if="filteredActions.length === 0" class="empty-state text-mono muted">
      No duty actions dispatched for current run.
    </div>

    <div v-else class="table-container">
      <table class="ack-table">
        <thead>
          <tr>
            <th>Status</th>
            <th>Site / Station</th>
            <th>Duty Role</th>
            <th>Action Directive</th>
            <th>Deadline</th>
            <th>Escalation</th>
            <th>Dispatched</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="act in filteredActions"
            :key="act.id"
            :class="['action-row', `state-${act.state.toLowerCase()}`, { late: isLate(act) }]"
          >
            <td>
              <span class="status-pill" :class="`pill-${act.state.toLowerCase()}`">
                {{ formatState(act.state) }}
              </span>
            </td>
            <td class="site-cell">
              <strong>{{ getSiteName(act) }}</strong>
            </td>
            <td class="actor-cell text-mono">
              {{ formatActor(act.dutyHolderId) }}
            </td>
            <td class="directive-cell">
              <span class="code-tag text-mono">{{ act.actionCode }}</span>
              <p class="action-desc">{{ getActionDescription(act.actionCode) }}</p>
            </td>
            <td class="deadline-cell text-mono" :class="{ 'text-danger': isLate(act) }">
              {{ formatDeadline(act.deadlineAt) }}
            </td>
            <td class="escalation-cell text-mono">
              <span v-if="act.escalationStep > 0" class="escalation-badge" :class="`step-${act.escalationStep}`">
                Step {{ act.escalationStep }}: {{ getEscalationLabel(act.escalationStep) }}
              </span>
              <span v-else class="muted">—</span>
            </td>
            <td class="sent-cell text-mono muted">
              {{ formatTime(act.sentAt) }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

export interface DispatchedAction {
  id: string
  runId: string
  incidentId: string
  dutyHolderId: string
  actionCode: string
  matrixVersion: number
  deadlineAt: string | null
  state: 'SENT' | 'DELIVERED' | 'ACKED' | 'DECLINED' | 'ESCALATED_1' | 'ESCALATED_2' | 'ESCALATED_3' | 'UNRESOLVED'
  sentAt: string
  ackedAt: string | null
  ackDataJson: string | null
  escalationStep: number
}

const props = defineProps<{
  incidentId?: string | null
}>()

const actions = ref<DispatchedAction[]>([])
const loading = ref(false)
const pendingOnly = ref(false)
let pollTimer: number | null = null

const pendingCount = computed(() => {
  return actions.value.filter(a => a.state !== 'ACKED' && a.state !== 'DECLINED').length
})

const filteredActions = computed(() => {
  if (pendingOnly.value) {
    return actions.value.filter(a => a.state !== 'ACKED' && a.state !== 'DECLINED')
  }
  return actions.value
})

const actorNames: Record<string, string> = {
  '11111111-1111-1111-1111-111111111101': 'Chilime Hydropower Control',
  '11111111-1111-1111-1111-111111111102': 'Mailung Safety Officer',
  '11111111-1111-1111-1111-111111111103': 'Betrawati Ward Chair',
  '11111111-1111-1111-1111-111111111104': 'Galchhi Highway Police',
  '11111111-1111-1111-1111-111111111105': 'Chief District Officer (Rasuwa)',
  '11111111-1111-1111-1111-111111111106': 'Syaphrubesi Gauge Reader'
}

const actionDescriptions: Record<string, string> = {
  'STANDBY_CREW': 'Put intake-closure crew on standby; test siren',
  'ALERT_VOLUNTEERS': 'Alert volunteers; check the evacuation route',
  'INSPECT_RIVER': 'Look at the river and send a field report',
  'BROADCAST_CAP': 'Broadcast the CAP message to affected wards',
  'CLOSE_INTAKE': 'Close headworks intake gates and stop river work',
  'EVACUATE_CREWS': 'Move river level and tunnel crews to assembly point',
  'HOLD_TRAFFIC': 'Stop vehicles entering corridor; close bridge',
  'EVACUATE_RIVERSIDE': 'Move riverside households to evacuation point',
  'MOVE_STUDENTS': 'Move students to assembly point; enter headcount',
  'CONVENE_COMMITTEE': 'Convene the district disaster committee',
  'STAND_DOWN': 'Stand down and report any damage observed'
}

async function fetchActions() {
  if (!props.incidentId) {
    // If no specific incident id, fetch latest run actions from phone simulator endpoint
    return
  }
  try {
    const res = await fetch(`/api/v1/incidents/${props.incidentId}/actions`)
    if (res.ok) {
      actions.value = await res.json()
    }
  } catch (err) {
    console.error('Failed to load actions:', err)
  }
}

function formatActor(holderId: string): string {
  return actorNames[holderId] ?? holderId.substring(0, 8)
}

function getSiteName(act: DispatchedAction): string {
  if (act.actionCode.includes('INTAKE')) return 'Syaphrubesi (Chilime)'
  if (act.actionCode.includes('CREW')) return 'Mailung Reach'
  if (act.actionCode.includes('TRAFFIC')) return 'Galchhi Checkpoint'
  if (act.actionCode.includes('RIVERSIDE')) return 'Betrawati'
  if (act.actionCode.includes('COMMITTEE')) return 'Dhunche HQ'
  return 'Corridor'
}

function getActionDescription(code: string): string {
  return actionDescriptions[code] ?? code
}

function formatState(state: string): string {
  switch (state) {
    case 'ACKED': return '✓ CONFIRMED'
    case 'SENT': return 'SENT'
    case 'DELIVERED': return 'DELIVERED'
    case 'DECLINED': return 'DECLINED'
    case 'ESCALATED_1': return 'ESC: VOICE'
    case 'ESCALATED_2': return 'ESC: DEPUTY'
    case 'UNRESOLVED': return '⚠ UNRESOLVED'
    default: return state
  }
}

function getEscalationLabel(step: number): string {
  if (step === 1) return '+5m Voice Call'
  if (step === 2) return '+10m Deputy Alerted'
  if (step >= 3) return '+15m CDO Flag'
  return ''
}

function formatDeadline(iso: string | null): string {
  if (!iso) return 'NOW'
  const d = new Date(iso)
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function formatTime(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function isLate(act: DispatchedAction): boolean {
  if (act.state === 'ACKED' || !act.deadlineAt) return false
  return new Date() > new Date(act.deadlineAt)
}

onMounted(() => {
  fetchActions()
  pollTimer = window.setInterval(fetchActions, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.ack-board {
  padding: var(--space-4);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  margin-top: var(--space-4);
}

.board-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-3);
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
  font-size: 16px;
  font-weight: 700;
}

.board-filters {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.filter-toggle {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 12px;
  cursor: pointer;
  color: var(--color-text-muted);
}

.refresh-btn {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  color: var(--color-text);
  border-radius: 4px;
  padding: 2px 8px;
  cursor: pointer;
  font-size: 14px;
}

.refresh-btn:hover {
  background: var(--color-bg);
}

.table-container {
  overflow-x: auto;
}

.ack-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 13px;
}

.ack-table th {
  padding: var(--space-2) var(--space-3);
  font-size: 11px;
  text-transform: uppercase;
  color: var(--color-text-muted);
  border-bottom: 1px solid var(--color-border);
}

.ack-table td {
  padding: var(--space-3);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  vertical-align: middle;
}

.action-row.late {
  background: rgba(239, 68, 68, 0.08);
}

.status-pill {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 12px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.pill-acked {
  background: rgba(34, 197, 94, 0.2);
  color: #22c55e;
  border: 1px solid #22c55e;
}

.pill-sent, .pill-delivered {
  background: rgba(59, 130, 246, 0.15);
  color: #60a5fa;
  border: 1px solid #3b82f6;
}

.pill-escalated_1, .pill-escalated_2 {
  background: rgba(245, 158, 11, 0.2);
  color: #f59e0b;
  border: 1px solid #f59e0b;
  animation: pulse-amber 2s infinite;
}

.pill-unresolved {
  background: rgba(239, 68, 68, 0.3);
  color: #ef4444;
  border: 1px solid #ef4444;
  animation: pulse-red 1.2s infinite;
}

.code-tag {
  background: rgba(255, 255, 255, 0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  color: #38bdf8;
}

.action-desc {
  margin: 4px 0 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}

.escalation-badge {
  display: inline-block;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
}

.escalation-badge.step-1 {
  background: rgba(245, 158, 11, 0.2);
  color: #f59e0b;
}

.escalation-badge.step-2 {
  background: rgba(249, 115, 22, 0.2);
  color: #f97316;
}

.escalation-badge.step-3 {
  background: rgba(239, 68, 68, 0.3);
  color: #ef4444;
}

.text-danger {
  color: #ef4444 !important;
  font-weight: 700;
}

.empty-state {
  padding: var(--space-6);
  text-align: center;
}

@keyframes pulse-amber {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

@keyframes pulse-red {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.7; transform: scale(0.98); }
}
</style>
