<template>
  <div class="phone-container">
    <!-- Phone Mockup Frame -->
    <div class="phone-frame">
      <!-- Status bar -->
      <div class="status-bar text-mono">
        <span>08:53</span>
        <span>JALDRISHTI · 5G</span>
        <span>100% 🔋</span>
      </div>

      <!-- App Header & Role Selection -->
      <div class="phone-header">
        <div class="header-main">
          <span class="app-logo">🌊 JalDrishti</span>
          <span class="badge badge-live">SIM LIVE</span>
        </div>

        <div class="role-selector">
          <label class="text-mono muted">Select Duty Role:</label>
          <select v-model="selectedHolderId" @change="onHolderChanged" class="role-dropdown">
            <option v-for="h in dutyHolders" :key="h.id" :value="h.id">
              {{ h.displayName }} ({{ h.actorType }})
            </option>
          </select>
        </div>
      </div>

      <!-- Active Directives Feed -->
      <div class="inbox-section">
        <div class="section-title text-mono">
          <span>EMERGENCY DIRECTIVES</span>
          <span class="count-badge" v-if="messages.length > 0">{{ messages.length }}</span>
        </div>

        <div v-if="loading" class="empty-inbox text-mono muted">
          Loading alerts…
        </div>

        <div v-else-if="messages.length === 0" class="empty-inbox">
          <div class="pulse-dot"></div>
          <p class="text-bold">Monitoring Corridor</p>
          <p class="text-mono muted">No active emergency directives assigned to this post.</p>
        </div>

        <div v-else class="message-cards">
          <div
            v-for="msg in messages"
            :key="msg.actionId"
            :class="['alert-card', { confirmed: isConfirmed(msg.actionId) }]"
          >
            <div class="alert-top">
              <span class="directive-code text-mono">{{ msg.actionCode }}</span>
              <span class="alert-time text-mono">{{ formatTime(msg.sentAt) }}</span>
            </div>

            <p class="alert-text text-devanagari">{{ msg.text }}</p>

            <div class="action-footer">
              <div v-if="isConfirmed(msg.actionId)" class="confirmed-badge text-mono">
                ✓ CONFIRMED & LOGGED
              </div>
              <div v-else class="action-buttons">
                <button
                  class="btn-ack"
                  :disabled="acking === msg.actionId"
                  @click="acknowledgeAction(msg.actionId)"
                >
                  {{ acking === msg.actionId ? 'Confirming…' : getButtonText(msg.actionCode) }}
                </button>
                <button
                  class="btn-decline"
                  @click="declineAction(msg.actionId)"
                >
                  ✕
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Quick Field Report Submitter -->
      <div class="report-box card">
        <div class="report-box-title text-mono">
          <span>FIELD EYE-WITNESS REPORT</span>
        </div>
        <textarea
          v-model="reportText"
          class="report-textarea text-devanagari"
          placeholder="Speak or type observation: e.g. नदीमा ठूलो बाढी आयो, पुल बगायो..."
          rows="2"
        ></textarea>
        <button class="btn-send-report" :disabled="!reportText.trim()" @click="submitReport">
          Submit Observation to DHM
        </button>
        <p v-if="reportSentNotice" class="sent-notice text-mono">✓ Report submitted & parsed by Gemini</p>
      </div>

      <!-- Footer navigation -->
      <div class="phone-nav text-mono">
        <router-link to="/">← Operator Console</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

interface DutyHolder {
  id: string
  actorType: string
  siteId: string
  displayName: string
  language: string
}

interface PhoneMessage {
  actionId: string
  dutyHolderId: string
  actorType: string
  actionCode: string
  text: string
  ackUrl: string
  sentAt: string
}

const dutyHolders = ref<DutyHolder[]>([])
const selectedHolderId = ref<string>('')
const messages = ref<PhoneMessage[]>([])
const confirmedActionIds = ref<Set<string>>(new Set())
const loading = ref(false)
const acking = ref<string | null>(null)
const reportText = ref('')
const reportSentNotice = ref(false)
let pollInterval: number | null = null

async function loadDutyHolders() {
  try {
    const res = await fetch('/api/v1/phone/duty-holders')
    if (res.ok) {
      dutyHolders.value = await res.json()
      if (dutyHolders.value.length > 0 && !selectedHolderId.value) {
        selectedHolderId.value = dutyHolders.value[0].id
        loadInbox()
      }
    }
  } catch (err) {
    console.error('Failed to load duty holders:', err)
  }
}

async function loadInbox() {
  if (!selectedHolderId.value) return
  try {
    const res = await fetch(`/api/v1/phone/inbox/${selectedHolderId.value}`)
    if (res.ok) {
      messages.value = await res.json()
    }
  } catch (err) {
    console.error('Failed to load inbox:', err)
  }
}

function onHolderChanged() {
  loadInbox()
}

function isConfirmed(actionId: string): boolean {
  return confirmedActionIds.value.has(actionId)
}

function getButtonText(code: string): string {
  if (code.includes('INTAKE')) return '✓ Confirm: Gates Closed'
  if (code.includes('CREW')) return '✓ Confirm: Crews Evacuated'
  if (code.includes('TRAFFIC')) return '✓ Confirm: Traffic Halted'
  if (code.includes('VOLUNTEER') || code.includes('RIVERSIDE')) return '✓ Confirm: Evacuation Started'
  if (code.includes('STUDENTS')) return '✓ Confirm: Students Moved'
  if (code.includes('COMMITTEE')) return '✓ Confirm: Committee Convened'
  return '✓ Confirm Duty Done'
}

async function acknowledgeAction(actionId: string) {
  acking.value = actionId
  try {
    const res = await fetch(`/api/v1/phone/ack/${actionId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: 'CONFIRMED', by: selectedHolderId.value })
    })
    if (res.ok) {
      confirmedActionIds.value.add(actionId)
    }
  } catch (err) {
    console.error('Ack failed:', err)
  } finally {
    acking.value = null
  }
}

async function declineAction(actionId: string) {
  const reason = prompt('Reason for declining directive:', 'Insufficient crew / Access blocked')
  if (!reason) return
  try {
    await fetch(`/api/v1/phone/decline/${actionId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason })
    })
    loadInbox()
  } catch (err) {
    console.error('Decline failed:', err)
  }
}

async function submitReport() {
  if (!reportText.value.trim()) return
  try {
    const res = await fetch('/api/v1/phone/report', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        text: reportText.value,
        role: 'OFFICIAL'
      })
    })
    if (res.ok) {
      reportText.value = ''
      reportSentNotice.value = true
      setTimeout(() => { reportSentNotice.value = false }, 3500)
    }
  } catch (err) {
    console.error('Report submission failed:', err)
  }
}

function formatTime(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

onMounted(() => {
  loadDutyHolders()
  pollInterval = window.setInterval(loadInbox, 2500)
})

onUnmounted(() => {
  if (pollInterval) clearInterval(pollInterval)
})
</script>

<style scoped>
.phone-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #090d16;
  padding: var(--space-4);
}

.phone-frame {
  width: 100%;
  max-width: 410px;
  background: #0f172a;
  border: 4px solid #334155;
  border-radius: 36px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7);
  padding: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.status-bar {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--color-text-muted);
  padding: 0 var(--space-2) var(--space-2);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.phone-header {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--color-border);
}

.header-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.app-logo {
  font-weight: 800;
  font-size: 18px;
  color: #38bdf8;
}

.badge-live {
  background: rgba(34, 197, 94, 0.2);
  color: #22c55e;
  border: 1px solid #22c55e;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 700;
}

.role-selector {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.role-dropdown {
  background: #1e293b;
  color: #f8fafc;
  border: 1px solid var(--color-border);
  padding: 6px var(--space-2);
  border-radius: var(--radius-md);
  font-size: 12px;
}

.inbox-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: var(--color-text-muted);
}

.count-badge {
  background: #ef4444;
  color: #fff;
  border-radius: 10px;
  padding: 1px 6px;
  font-size: 10px;
  font-weight: 700;
}

.empty-inbox {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: var(--space-6) var(--space-2);
  color: var(--color-text-muted);
  gap: var(--space-2);
}

.pulse-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--color-ok);
  animation: pulse-dot 2s infinite;
}

.alert-card {
  background: #1e293b;
  border-left: 4px solid #ef4444;
  border-radius: var(--radius-md);
  padding: var(--space-3);
  margin-bottom: var(--space-2);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  transition: all 0.2s ease;
}

.alert-card.confirmed {
  border-left-color: #22c55e;
  background: rgba(34, 197, 94, 0.08);
}

.alert-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.directive-code {
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
}

.alert-card.confirmed .directive-code {
  background: rgba(34, 197, 94, 0.2);
  color: #22c55e;
}

.alert-time {
  font-size: 11px;
  color: var(--color-text-muted);
}

.alert-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
  color: #f1f5f9;
}

.action-buttons {
  display: flex;
  gap: var(--space-2);
}

.btn-ack {
  flex: 1;
  background: #22c55e;
  color: #000;
  font-weight: 700;
  padding: 10px;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 13px;
  transition: background 0.15s;
}

.btn-ack:hover:not(:disabled) {
  background: #16a34a;
}

.btn-decline {
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
  border: 1px solid #ef4444;
  border-radius: var(--radius-md);
  padding: 0 var(--space-3);
  cursor: pointer;
  font-size: 14px;
}

.confirmed-badge {
  color: #22c55e;
  font-weight: 700;
  font-size: 12px;
  text-align: center;
  padding: 6px;
}

.report-box {
  background: #1e293b;
  padding: var(--space-3);
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.report-box-title {
  font-size: 11px;
  color: var(--color-text-muted);
}

.report-textarea {
  background: #0f172a;
  border: 1px solid var(--color-border);
  color: #fff;
  padding: var(--space-2);
  border-radius: var(--radius-sm);
  font-size: 12px;
  resize: none;
}

.btn-send-report {
  background: #38bdf8;
  color: #000;
  font-weight: 700;
  border: none;
  padding: 8px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  cursor: pointer;
}

.btn-send-report:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.sent-notice {
  color: #22c55e;
  font-size: 11px;
  margin: 0;
  text-align: center;
}

.phone-nav {
  text-align: center;
  font-size: 12px;
  padding-top: var(--space-2);
}

.phone-nav a {
  color: var(--color-text-muted);
  text-decoration: none;
}

.phone-nav a:hover {
  color: #38bdf8;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.9); }
}
</style>
