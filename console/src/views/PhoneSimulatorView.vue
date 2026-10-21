<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Waves, FlaskConical, ChevronRight, Siren, CircleCheck, Send } from '@lucide/vue'
import { api, endpoints } from '@/lib/api'
import { formatNpt } from '@/lib/time'
import { CORRIDOR_ID, ACTOR_TYPES, describeAction, ACTION_CONFIRM_LABELS } from '@/lib/labels'
import type { DutyHolder, CorridorDetails, PhoneMessage, FieldReport } from '@/types/api'
import UiBadge from '@/components/ui/UiBadge.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiPanel from '@/components/ui/UiPanel.vue'
import UiEmptyState from '@/components/ui/UiEmptyState.vue'

const route = useRoute()
const router = useRouter()

const dutyHolders = ref<DutyHolder[]>([])
const corridor = ref<CorridorDetails | null>(null)
const isLoadingData = ref(true)
const loadError = ref<string | null>(null)

const selectedHolderId = ref<string | null>(null)
const messages = ref<PhoneMessage[]>([])
const confirmedSet = ref<Set<string>>(new Set())
const declinedSet = ref<Set<string>>(new Set())
const actionErrors = ref<Record<string, string>>({})

// Decline sheet state
const declineActionId = ref<string | null>(null)
const declineReasonOption = ref<string | null>(null)
const declineDetails = ref('')
const isDeclining = ref(false)

const declineReasons = [
  'Not enough people',
  'Access is blocked',
  'Not my responsibility',
  'Other',
]

// Phone report state
const reportText = ref('')
const isSendingReport = ref(false)
const reportSentSuccess = ref(false)
const reportError = ref<string | null>(null)

let pollTimer: number | null = null

function getSavedHolder(): string | null {
  try {
    return window.localStorage.getItem('jaldrishti.phone.holder')
  } catch {
    return null
  }
}

function saveHolder(id: string) {
  try {
    window.localStorage.setItem('jaldrishti.phone.holder', id)
  } catch {
    /* localStorage access blocked */
  }
}

async function loadInitialData() {
  isLoadingData.value = true
  loadError.value = null
  try {
    const [holdersRes, corridorRes] = await Promise.allSettled([
      api.get<DutyHolder[]>(endpoints.dutyHolders()),
      api.get<CorridorDetails>(endpoints.corridor(CORRIDOR_ID)),
    ])

    if (holdersRes.status === 'fulfilled') {
      dutyHolders.value = holdersRes.value
    } else {
      throw holdersRes.reason
    }

    if (corridorRes.status === 'fulfilled') {
      corridor.value = corridorRes.value
    }
  } catch (e: unknown) {
    loadError.value = e instanceof Error ? e.message : String(e)
  } finally {
    isLoadingData.value = false
  }
}

const currentHolder = computed(() => {
  return dutyHolders.value.find((h) => h.id === selectedHolderId.value) ?? null
})

function siteName(siteId: string | null | undefined): string {
  if (!siteId) return '—'
  return corridor.value?.sites.find((s) => s.id === siteId)?.nameEn ?? siteId
}

function selectHolder(id: string) {
  selectedHolderId.value = id
  saveHolder(id)
  void router.replace(`/phone/${id}`)
}

function changeRole() {
  selectedHolderId.value = null
  try {
    window.localStorage.removeItem('jaldrishti.phone.holder')
  } catch {
    /* storage access error */
  }
  void router.replace('/phone')
}

async function fetchInbox(holderId: string) {
  try {
    const msgs = await api.get<PhoneMessage[]>(endpoints.inbox(holderId))
    messages.value = [...msgs].sort(
      (a, b) => Date.parse(b.sentAt) - Date.parse(a.sentAt),
    )
  } catch {
    /* keep existing messages on transient poll error */
  }
}

function startPolling(holderId: string) {
  stopPolling()
  void fetchInbox(holderId)
  pollTimer = window.setInterval(() => {
    void fetchInbox(holderId)
  }, 2500)
}

function stopPolling() {
  if (pollTimer !== null) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

watch(
  () => route.params.recipientId,
  (recipId) => {
    if (typeof recipId === 'string' && recipId) {
      selectedHolderId.value = recipId
      startPolling(recipId)
    } else {
      const saved = getSavedHolder()
      if (saved) {
        selectedHolderId.value = saved
        startPolling(saved)
      } else {
        selectedHolderId.value = null
        stopPolling()
      }
    }
  },
  { immediate: true },
)

onMounted(() => {
  void loadInitialData()
})

onUnmounted(() => {
  stopPolling()
})

const pendingCount = computed(() => {
  return messages.value.filter(
    (m) => !confirmedSet.value.has(m.actionId) && !declinedSet.value.has(m.actionId),
  ).length
})

async function confirmAction(msg: PhoneMessage) {
  if (!selectedHolderId.value) return
  actionErrors.value[msg.actionId] = ''
  try {
    await api.post(endpoints.phoneAck(msg.actionId), {
      status: 'CONFIRMED',
      by: selectedHolderId.value,
    })
    confirmedSet.value.add(msg.actionId)
  } catch {
    actionErrors.value[msg.actionId] =
      'Could not confirm. Check your connection and try again.'
  }
}

function openDecline(actionId: string) {
  declineActionId.value = actionId
  declineReasonOption.value = null
  declineDetails.value = ''
}

function closeDecline() {
  declineActionId.value = null
}

async function sendDecline() {
  if (!declineActionId.value || !declineReasonOption.value) return
  isDeclining.value = true
  try {
    let finalReason = declineReasonOption.value
    if (declineDetails.value.trim()) {
      finalReason += `: ${declineDetails.value.trim()}`
    }
    await api.post(endpoints.phoneDecline(declineActionId.value), {
      reason: finalReason,
    })
    declinedSet.value.add(declineActionId.value)
    const id = selectedHolderId.value
    closeDecline()
    if (id) {
      void fetchInbox(id)
    }
  } catch {
    /* decline request failed */
  } finally {
    isDeclining.value = false
  }
}

async function submitPhoneReport() {
  if (!reportText.value.trim()) return
  isSendingReport.value = true
  reportError.value = null
  try {
    await api.post<FieldReport>(endpoints.phoneReport(), {
      text: reportText.value.trim(),
      role: 'OFFICIAL',
    })
    reportText.value = ''
    reportSentSuccess.value = true
    setTimeout(() => {
      reportSentSuccess.value = false
    }, 4000)
  } catch (e: unknown) {
    reportError.value = e instanceof Error ? e.message : String(e)
  } finally {
    isSendingReport.value = false
  }
}
</script>

<template>
  <div class="phone-sim-page">
    <!-- Header -->
    <header class="phone-header">
      <div class="header-main-row">
        <div class="header-logo-row">
          <Waves :size="18" aria-hidden="true" class="logo-icon" />
          <span class="brand-text">JalDrishti</span>
        </div>
        <UiBadge tone="neutral" :icon="FlaskConical">Simulation</UiBadge>
      </div>

      <div v-if="currentHolder" class="header-holder-row">
        <div class="holder-meta">
          <span class="holder-name">{{ currentHolder.displayName }}</span>
          <span class="holder-desc">
            {{ ACTOR_TYPES[currentHolder.actorType] }} · {{ siteName(currentHolder.siteId) }}
          </span>
        </div>
        <UiButton
          variant="ghost"
          size="sm"
          @click="changeRole"
        >
          Change role
        </UiButton>
      </div>
    </header>

    <!-- Role picker -->
    <div v-if="!selectedHolderId" class="role-picker-container">
      <h1 class="role-title">Who are you?</h1>
      <p class="role-subtitle">
        Pick your duty role to receive the actions JalDrishti sends to you.
      </p>

      <div v-if="isLoadingData" class="skeletons-list">
        <div class="skeleton role-skeleton" />
        <div class="skeleton role-skeleton" />
        <div class="skeleton role-skeleton" />
      </div>

      <UiEmptyState
        v-else-if="loadError"
        tone="danger"
        title="Could not load duty holders"
        :body="loadError"
      >
        <UiButton variant="secondary" @click="loadInitialData">Retry</UiButton>
      </UiEmptyState>

      <div v-else class="holders-list">
        <button
          v-for="h in dutyHolders"
          :key="h.id"
          type="button"
          class="holder-card-btn"
          @click="selectHolder(h.id)"
        >
          <div class="holder-info">
            <span class="holder-item-name">{{ h.displayName }}</span>
            <span class="holder-item-desc">
              {{ ACTOR_TYPES[h.actorType] }} · {{ siteName(h.siteId) }}
            </span>
          </div>
          <ChevronRight :size="18" aria-hidden="true" class="holder-chevron" />
        </button>
      </div>
    </div>

    <!-- Holder inbox -->
    <div v-else class="inbox-container">
      <!-- Pending Alert Banner -->
      <div v-if="pendingCount > 0" class="pending-alert-banner">
        <Siren :size="20" aria-hidden="true" class="siren-icon" />
        <span class="alert-text">
          {{ pendingCount }} action{{ pendingCount > 1 ? 's' : '' }} need{{ pendingCount > 1 ? '' : 's' }} your confirmation
        </span>
      </div>

      <!-- Messages list -->
      <div v-if="messages.length === 0" class="inbox-empty">
        <UiEmptyState
          :icon="CircleCheck"
          title="Nothing for you right now"
          body="Keep this page open. New actions appear here automatically."
        />
      </div>

      <div v-else class="messages-list">
        <article
          v-for="m in messages"
          :key="m.actionId"
          class="message-card"
          :class="[
            confirmedSet.has(m.actionId)
              ? 'border-confirmed'
              : declinedSet.has(m.actionId)
                ? 'border-declined'
                : 'border-pending'
          ]"
        >
          <div class="msg-header">
            <h2 class="msg-title">{{ describeAction(m.actionCode) }}</h2>
            <span class="msg-time">Sent {{ formatNpt(m.sentAt) }} NPT</span>
          </div>

          <p class="deva msg-body">{{ m.text }}</p>

          <!-- Confirmed state -->
          <div v-if="confirmedSet.has(m.actionId)" class="state-chip">
            <UiBadge
              tone="ok"
              size="md"
              :icon="CircleCheck"
            >
              Confirmed — logged
            </UiBadge>
          </div>

          <!-- Declined state -->
          <div v-else-if="declinedSet.has(m.actionId)" class="state-chip">
            <UiBadge tone="watch" size="md">
              Declined — the forecaster has been told
            </UiBadge>
          </div>

          <!-- Action buttons -->
          <div v-else class="msg-buttons">
            <UiButton
              variant="primary"
              block
              @click="confirmAction(m)"
            >
              {{ ACTION_CONFIRM_LABELS[m.actionCode] ?? 'Confirm — done' }}
            </UiButton>
            <div v-if="actionErrors[m.actionId]" class="action-err">
              {{ actionErrors[m.actionId] }}
            </div>
            <UiButton
              variant="secondary"
              block
              @click="openDecline(m.actionId)"
            >
              I can't do this
            </UiButton>
          </div>
        </article>
      </div>

      <p class="inbox-note">Confirmations are shown here until this page reloads.</p>

      <!-- Report Box -->
      <UiPanel title="Report what you see" class="report-box-panel">
        <div class="phone-report-form">
          <textarea
            v-model="reportText"
            rows="3"
            class="deva"
            placeholder="Type what you see at your location"
          />
          <div v-if="reportSentSuccess" class="report-success-badge">
            <UiBadge tone="ok">Report sent</UiBadge>
          </div>
          <div v-if="reportError" class="phone-report-err">
            {{ reportError }}
          </div>
          <UiButton
            variant="primary"
            block
            :icon="Send"
            :disabled="!reportText.trim()"
            :busy="isSendingReport"
            @click="submitPhoneReport"
          >
            Send report
          </UiButton>
        </div>
      </UiPanel>
    </div>

    <!-- Decline Sheet -->
    <Teleport to="body">
      <div
        v-if="declineActionId !== null"
        class="decline-scrim"
        aria-hidden="true"
        @click="closeDecline"
      />
      <div
        v-if="declineActionId !== null"
        class="decline-panel"
        role="dialog"
        aria-label="Why can't you do this?"
      >
        <h3 class="decline-title">Why can't you do this?</h3>
        <div class="reasons-list">
          <button
            v-for="r in declineReasons"
            :key="r"
            type="button"
            class="reason-btn"
            :class="{ 'is-selected': declineReasonOption === r }"
            :aria-pressed="declineReasonOption === r ? 'true' : 'false'"
            @click="declineReasonOption = r"
          >
            {{ r }}
          </button>
        </div>

        <div class="details-field">
          <label for="decline-details">Details</label>
          <textarea
            id="decline-details"
            v-model="declineDetails"
            rows="2"
            placeholder="Optional additional context"
          />
        </div>

        <div class="decline-footer">
          <UiButton variant="secondary" @click="closeDecline">Cancel</UiButton>
          <UiButton
            variant="danger"
            :disabled="!declineReasonOption"
            :busy="isDeclining"
            @click="sendDecline"
          >
            Send to forecaster
          </UiButton>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.phone-sim-page {
  min-height: 100vh;
  background: var(--bg);
  width: 100%;
  max-width: 30rem;
  margin: 0 auto;
  padding: var(--sp-4) var(--sp-4) var(--sp-8);
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  box-sizing: border-box;
}

.phone-header {
  position: sticky;
  top: 0;
  background: var(--surface-1);
  border-bottom: 1px solid var(--border);
  padding: var(--sp-3) var(--sp-4);
  margin-left: calc(-1 * var(--sp-4));
  margin-right: calc(-1 * var(--sp-4));
  margin-top: calc(-1 * var(--sp-4));
  z-index: 20;
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.header-main-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-logo-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.logo-icon {
  color: var(--brand-mark);
}

.brand-text {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--text);
}

.header-holder-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: var(--sp-2);
  border-top: 1px solid var(--border);
}

.holder-meta {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.holder-name {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text);
}

.holder-desc {
  font-size: var(--fs-sm);
  color: var(--text-2);
}

.role-picker-container {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  padding: var(--sp-2) 0;
}

.role-title {
  font-size: var(--fs-xl);
  font-weight: 600;
  color: var(--text);
}

.role-subtitle {
  font-size: var(--fs-md);
  color: var(--text-2);
}

.skeletons-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.role-skeleton {
  height: 4rem;
  width: 100%;
}

.holders-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.holder-card-btn {
  min-height: 4rem;
  padding: var(--sp-3) var(--sp-4);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--surface-1);
  text-align: left;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  transition: background-color 120ms;
}

.holder-card-btn:hover {
  background: var(--surface-2);
}

.holder-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.holder-item-name {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text);
}

.holder-item-desc {
  font-size: var(--fs-sm);
  color: var(--text-2);
}

.holder-chevron {
  color: var(--text-3);
  flex-shrink: 0;
}

.inbox-container {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.pending-alert-banner {
  background: var(--danger-soft);
  border: 1px solid var(--danger-line);
  border-radius: var(--r-md);
  padding: var(--sp-3);
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.siren-icon {
  color: var(--danger);
  flex-shrink: 0;
}

.alert-text {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--danger);
}

.messages-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.message-card {
  background: var(--surface-1);
  border: 1px solid var(--border);
  border-left-width: 4px;
  border-radius: var(--r-md);
  padding: var(--sp-4);
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.border-pending {
  border-left-color: var(--danger);
}

.border-confirmed {
  border-left-color: var(--ok);
}

.border-declined {
  border-left-color: var(--watch);
}

.msg-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.msg-title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--text);
  line-height: var(--lh-tight);
}

.msg-time {
  font-size: var(--fs-sm);
  color: var(--text-2);
}

.msg-body {
  color: var(--text);
}

.msg-buttons {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.action-err {
  font-size: var(--fs-sm);
  color: var(--danger);
}

.inbox-note {
  font-size: var(--fs-xs);
  color: var(--text-3);
  text-align: center;
}

.phone-report-form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.phone-report-err {
  font-size: var(--fs-sm);
  color: var(--danger);
}

.report-success-badge {
  display: flex;
  align-items: center;
}

.decline-scrim {
  position: fixed;
  inset: 0;
  background: var(--scrim);
  z-index: 45;
}

.decline-panel {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 30rem;
  background: var(--surface-1);
  border-top-left-radius: var(--r-md);
  border-top-right-radius: var(--r-md);
  box-shadow: var(--shadow-overlay);
  padding: var(--sp-4);
  z-index: 50;
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  box-sizing: border-box;
}

.decline-title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--text);
}

.reasons-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.reason-btn {
  width: 100%;
  padding: var(--sp-2) var(--sp-3);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  background: var(--surface-2);
  color: var(--text);
  font-size: var(--fs-md);
  text-align: left;
  cursor: pointer;
  transition: background-color 120ms, border-color 120ms;
}

.reason-btn:hover {
  background: var(--surface-3);
}

.reason-btn.is-selected {
  background: var(--accent-soft);
  border-color: var(--accent);
  color: var(--accent);
  font-weight: 600;
}

.details-field {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}

.decline-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
}
</style>
