<template>
  <div class="msgs">
    <header class="head">
      <div>
        <h1>Messages sent</h1>
        <p class="sub">
          <span class="ctx" :data-mode="store.mode">{{ store.mode === 'live' ? 'Live' : 'Training run' }}</span>
          Every message JalDrishti sent in this run: who got it, what it said, and what they did about it.
        </p>
      </div>
      <span v-if="store.messagesLoaded" class="updated">Refreshes every 3 seconds</span>
    </header>

    <p class="delivery">
      <strong>Where these went.</strong> {{ DELIVERY_NOTE }}
    </p>

    <!-- No run -->
    <div v-if="!store.runId" class="card empty">
      <Inbox :size="24" aria-hidden="true" />
      <p class="empty-title">No run yet</p>
      <p class="empty-body">
        Start a training scenario from the bar above, or switch to Live. Every message JalDrishti sends is listed here as it goes out.
      </p>
    </div>

    <!-- Error before anything loaded -->
    <div v-else-if="loadError && !store.messagesLoaded" class="card empty" role="alert">
      <TriangleAlert class="danger-icon" :size="24" aria-hidden="true" />
      <p class="empty-title">Could not load the message log</p>
      <p class="empty-body">{{ loadError }}</p>
      <button type="button" class="btn" @click="store.refreshMessages()">Retry</button>
    </div>

    <!-- Loading -->
    <div v-else-if="!store.messagesLoaded" class="loading" aria-busy="true" aria-label="Loading messages">
      <span class="skeleton sk-kpis" />
      <span class="skeleton sk-list" />
    </div>

    <!-- Nothing sent yet -->
    <div v-else-if="store.messages.length === 0 && store.capMessages.length === 0" class="card empty">
      <MessageSquareText :size="24" aria-hidden="true" />
      <p class="empty-title">No messages yet</p>
      <p class="empty-body">
        JalDrishti sends messages only when it raises a WATCH or a WARNING. In the 26 August replay the first ones go out at 08:53, with no one pressing a button.
      </p>
    </div>

    <template v-else>
      <p v-if="loadError" class="stale" role="status">Showing the last log that loaded. The latest refresh failed: {{ loadError }}</p>

      <section class="kpis" aria-label="Message totals">
        <div class="kpi">
          <span class="k-label">Messages sent</span>
          <span class="k-value">{{ store.messages.length }}</span>
          <span class="k-hint">to {{ recipientCount }} {{ recipientCount === 1 ? 'person' : 'people' }}</span>
        </div>
        <div class="kpi ok">
          <span class="k-label">Orders confirmed</span>
          <span class="k-value">{{ counts.confirmed }}</span>
          <span class="k-hint">{{ counts.waiting }} still waiting Â· {{ counts.declined }} declined Â· {{ counts.unresolved }} never confirmed</span>
        </div>
        <div class="kpi watch">
          <span class="k-label">Escalated</span>
          <span class="k-value">{{ counts.escalated }}</span>
          <span class="k-hint">{{ deputyCount }} {{ deputyCount === 1 ? 'message' : 'messages' }} sent to a deputy</span>
        </div>
        <div class="kpi danger">
          <span class="k-label">Public alerts (CAP)</span>
          <span class="k-value">{{ store.capMessages.length }}</span>
          <span class="k-hint">{{ store.capMessages.length ? 'prepared for NDRRMA, not sent' : 'none: prepared only at WARNING' }}</span>
        </div>
      </section>

      <div class="grid2">
        <section class="card flush" aria-labelledby="log-title">
          <div class="card-head">
            <div>
              <h2 id="log-title">Message log</h2>
              <p class="card-sub">Oldest first. The status shows what has happened to each order since it was sent.</p>
            </div>
            <div class="tools">
              <div class="seg" role="group" aria-label="Filter messages">
                <button v-for="f in filters" :key="f.value" type="button" :aria-pressed="filter === f.value" @click="filter = f.value">
                  {{ f.label }} Â· <span class="tabular">{{ f.count }}</span>
                </button>
              </div>
              <label class="search">
                <Search :size="16" aria-hidden="true" />
                <span class="sr-only">Find a person, role or site</span>
                <input v-model="query" type="search" placeholder="Find a person or site" />
              </label>
            </div>
          </div>

          <p v-if="shown.length === 0" class="none">No messages match this filter.</p>
          <ol v-else class="log">
            <li v-for="m in shown" :key="m.messageId" class="msg" :data-kind="m.kind">
              <div class="when">
                <span class="mono t">{{ formatNpt(m.sentAt, true) }}</span>
                <span v-if="afterWarning(m)" class="rel">{{ afterWarning(m) }}</span>
              </div>
              <div class="body">
                <div class="row1">
                  <div class="who">
                    <span class="name">{{ m.recipientName ?? 'Unknown recipient' }}</span>
                    <span class="role">{{ roleLine(m) }}</span>
                  </div>
                  <span class="chip" :data-tone="stateTone(m)">{{ ACTION_STATES[m.actionState]?.label ?? m.actionState }}</span>
                </div>
                <p class="what">
                  <template v-if="m.kind === 'DEPUTY_ESCALATION'">
                    Escalation: {{ m.principalName ?? 'the duty-holder' }} has not confirmed â€œ{{ describeAction(m.actionCode) }}â€
                  </template>
                  <template v-else>{{ describeAction(m.actionCode) }}</template>
                  <span v-if="m.deadlineAt && m.kind === 'ACTION'" class="due"> Â· do by <span class="mono">{{ formatNpt(m.deadlineAt) }}</span></span>
                </p>
                <blockquote class="text" :lang="m.language" :class="{ deva: m.language === 'ne' || m.language === 'hi' }">{{ m.text }}</blockquote>
                <div class="foot">
                  <span class="meta"><Smartphone :size="14" aria-hidden="true" />{{ channelLabel(m.channel) }}</span>
                  <span class="meta">{{ languageLabel(m.language) }}</span>
                  <span class="meta mono">{{ m.actionCode }}</span>
                  <a class="open" :href="`/phone/${encodeURIComponent(m.recipientId)}`" target="_blank" rel="noopener">
                    Open their phone <ExternalLink :size="14" aria-hidden="true" />
                  </a>
                </div>
                <p class="outcome" :data-tone="stateTone(m)">{{ outcome(m) }}</p>
              </div>
            </li>
          </ol>
        </section>

        <div class="side">
          <section class="card" aria-labelledby="cap-title">
            <h2 id="cap-title">Public alert for NDRRMA</h2>
            <p class="card-sub">CAP 1.2, the standard format for NDRRMA's mass-SMS gateway. Prepared at WARNING and again at stand-down.</p>
            <p v-if="capView.length === 0" class="none">No public alert yet. JalDrishti prepares one only at WARNING; a WATCH stays internal.</p>
            <ul v-else class="caps">
              <li v-for="c in capView" :key="c.identifier" class="cap">
                <div class="row1">
                  <span class="chip" :data-tone="c.msgType === 'Cancel' ? 'info' : 'danger'">{{ c.msgType === 'Cancel' ? 'All clear' : 'Alert' }}</span>
                  <span class="chip" :data-tone="c.status === 'Actual' ? 'danger' : 'info'">{{ c.status === 'Actual' ? 'Actual' : 'Exercise' }}</span>
                  <span class="mono t">{{ formatNpt(c.sentAt, true) }}</span>
                </div>
                <p class="cap-headline">{{ c.headline ?? 'No headline found in the XML' }}</p>
                <p v-if="c.areas.length" class="cap-areas">Areas: {{ c.areas.join(', ') }}</p>
                <p class="gateway" :data-sim="c.simulated ? 'yes' : 'no'">
                  {{ c.simulated ? 'Not sent. The NDRRMA gateway is simulated in this build.' : `Gateway replied: ${c.gatewayText}` }}
                </p>
                <details>
                  <summary>Show the CAP XML</summary>
                  <pre class="xml">{{ c.xml }}</pre>
                  <button type="button" class="btn sm" @click="copy(c)">
                    <Copy :size="14" aria-hidden="true" />{{ copied === c.identifier ? 'Copied' : 'Copy XML' }}
                  </button>
                </details>
                <p class="mono cap-id">{{ c.identifier }}</p>
              </li>
            </ul>
          </section>

          <section class="card" aria-labelledby="notsent-title">
            <h2 id="notsent-title">Steps that are not messages</h2>
            <ul class="plain">
              <li><PhoneCall :size="16" aria-hidden="true" /><span>{{ NOT_SENT.voice }}</span></li>
              <li><Megaphone :size="16" aria-hidden="true" /><span>{{ NOT_SENT.cdo }}</span></li>
              <li><Send :size="16" aria-hidden="true" /><span>{{ NOT_SENT.telegram }}</span></li>
            </ul>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  Copy, ExternalLink, Inbox, Megaphone, MessageSquareText, PhoneCall, Search, Send, Smartphone, TriangleAlert,
} from '@lucide/vue'
import { useConsoleStore } from '@/stores/console'
import { ACTION_STATES, ACTOR_TYPES, describeAction } from '@/lib/labels'
import { formatNpt, isPastSim, minutesBetween, untilLabel } from '@/lib/time'
import type { ActionState, CapMessageEntry, MessageLogEntry, Tone } from '@/types/api'

// What the backend can and cannot deliver today. Update these lines when real gateways are connected.
const DELIVERY_NOTE =
  'No SMS, voice-call or Telegram gateway is connected yet, so nothing on this page reached a real phone. ' +
  'Orders to duty-holders are delivered to the in-app phone: use "Open their phone" to see a message exactly as they would. ' +
  'Public alerts are prepared as CAP for NDRRMA but not sent.'
const NOT_SENT = {
  voice: 'Escalation step 1 is a voice call. This build records the step on the board; no call is placed.',
  cdo: 'Escalation step 3 flags the Chief District Officer on the board. No message is sent for it.',
  telegram: 'Duty-holders with a Telegram chat id are written to the server log instead. Sending to Telegram is not built yet.',
}
const CHANNELS: Record<string, string> = {
  WEB_PHONE_SIM: 'In-app phone',
  TELEGRAM: 'In-app phone Â· Telegram not sent (logged only)',
}
const LANGUAGES: Record<string, string> = { ne: 'Nepali', hi: 'Hindi', en: 'English' }

const store = useConsoleStore()
let stopPolling: (() => void) | null = null
onMounted(() => { stopPolling = store.watchMessages() })
onUnmounted(() => { stopPolling?.() })

const loadError = computed(() => store.errors.messages ?? null)

// â”€â”€ Filters â”€â”€
type Filter = 'all' | 'waiting' | 'escalations'
const filter = ref<Filter>('all')
const query = ref('')
const FINISHED: ActionState[] = ['ACKED', 'DECLINED', 'UNRESOLVED']
const isWaiting = (m: MessageLogEntry) => !FINISHED.includes(m.actionState)
const isEscalation = (m: MessageLogEntry) => m.kind === 'DEPUTY_ESCALATION' || m.escalationStep > 0

const filters = computed<Array<{ value: Filter; label: string; count: number }>>(() => [
  { value: 'all', label: 'All', count: store.messages.length },
  { value: 'waiting', label: 'Waiting', count: store.messages.filter(isWaiting).length },
  { value: 'escalations', label: 'Escalations', count: store.messages.filter(isEscalation).length },
])

const shown = computed(() => {
  const q = query.value.trim().toLowerCase()
  return store.messages.filter(m => {
    if (filter.value === 'waiting' && !isWaiting(m)) return false
    if (filter.value === 'escalations' && !isEscalation(m)) return false
    if (!q) return true
    const haystack = [
      m.recipientName ?? '',
      m.recipientActorType ? ACTOR_TYPES[m.recipientActorType] : '',
      store.siteName(m.recipientSiteId),
      m.actionCode,
      describeAction(m.actionCode),
    ].join(' ').toLowerCase()
    return haystack.includes(q)
  })
})

// â”€â”€ Totals (per order, not per message: a deputy message belongs to the same order) â”€â”€
const orders = computed(() => {
  const byAction = new Map<string, MessageLogEntry>()
  for (const m of store.messages) if (m.kind === 'ACTION') byAction.set(m.actionId, m)
  return [...byAction.values()]
})
const counts = computed(() => {
  const c = { confirmed: 0, waiting: 0, declined: 0, unresolved: 0, escalated: 0 }
  for (const o of orders.value) {
    if (o.actionState === 'ACKED') c.confirmed++
    else if (o.actionState === 'DECLINED') c.declined++
    else if (o.actionState === 'UNRESOLVED') c.unresolved++
    else c.waiting++
    if (o.escalationStep > 0) c.escalated++
  }
  return c
})
const recipientCount = computed(() => new Set(store.messages.map(m => m.recipientId)).size)
const deputyCount = computed(() => store.messages.filter(m => m.kind === 'DEPUTY_ESCALATION').length)

// â”€â”€ Row helpers â”€â”€
const channelLabel = (c: string) => CHANNELS[c] ?? c
const languageLabel = (l: string) => LANGUAGES[l] ?? l

function roleLine(m: MessageLogEntry): string {
  const parts = [
    m.kind === 'DEPUTY_ESCALATION' ? 'Deputy' : '',
    m.recipientActorType ? ACTOR_TYPES[m.recipientActorType] : '',
    m.recipientSiteId ? store.siteName(m.recipientSiteId) : '',
  ]
  return parts.filter(Boolean).join(' Â· ')
}

function isLate(m: MessageLogEntry): boolean {
  return isWaiting(m) && isPastSim(m.deadlineAt, store.simNow)
}

function stateTone(m: MessageLogEntry): Tone {
  if (isLate(m)) return 'danger'
  return ACTION_STATES[m.actionState]?.tone ?? 'neutral'
}

/** "+2 min after WARNING" â€” shows that dispatch is automatic and immediate. */
function afterWarning(m: MessageLogEntry): string {
  const warnAt = store.firstWarningEvent?.at ?? null
  const mins = minutesBetween(warnAt, m.sentAt)
  if (mins === null) return ''
  if (mins === 0) return 'at WARNING'
  return mins > 0 ? `+${mins} min after WARNING` : `${-mins} min before WARNING`
}

function outcome(m: MessageLogEntry): string {
  switch (m.actionState) {
    case 'ACKED': {
      const mins = m.kind === 'ACTION' ? minutesBetween(m.sentAt, m.ackedAt) : null
      return `Confirmed at ${formatNpt(m.ackedAt)}${mins !== null ? `, ${mins} min after this message` : ''}.`
    }
    case 'DECLINED':
      return 'Declined: they said they cannot do this. The forecaster has to find someone else.'
    case 'ESCALATED_1':
      return 'Not confirmed. Step 1 of escalation: voice call (recorded only, no call placed).'
    case 'ESCALATED_2':
      return 'Not confirmed. Step 2 of escalation: their deputy was sent a message, if one is registered.'
    case 'ESCALATED_3':
    case 'UNRESOLVED':
      return 'Never confirmed. Flagged to the Chief District Officer (step 3).'
    default: {
      if (!m.deadlineAt) return 'Waiting for confirmation.'
      return isLate(m)
        ? `Waiting for confirmation. Past its deadline by ${untilLabel(m.deadlineAt, store.simNow).replace(' late', '')}.`
        : `Waiting for confirmation. Deadline ${untilLabel(m.deadlineAt, store.simNow)}.`
    }
  }
}

// â”€â”€ CAP alerts: pull the plain-language parts out of the XML â”€â”€
interface CapView extends CapMessageEntry {
  headline: string | null
  areas: string[]
  simulated: boolean
  gatewayText: string
}
const capView = computed<CapView[]>(() =>
  store.capMessages.map(c => {
    let headline: string | null = null
    let areas: string[] = []
    try {
      const doc = new DOMParser().parseFromString(c.xml, 'application/xml')
      if (doc.getElementsByTagName('parsererror').length === 0) {
        headline = doc.getElementsByTagName('headline')[0]?.textContent?.trim() || null
        areas = Array.from(doc.getElementsByTagName('areaDesc'))
          .map(n => n.textContent?.trim() ?? '')
          .filter(Boolean)
      }
    } catch {
      // Unreadable XML: the raw text is still shown below.
    }
    const gw = c.gatewayResponse ?? ''
    return { ...c, headline, areas, simulated: gw.includes('SIMULATED'), gatewayText: gw || 'no reply recorded' }
  }),
)

const copied = ref<string | null>(null)
async function copy(c: CapMessageEntry) {
  try {
    await navigator.clipboard.writeText(c.xml)
    copied.value = c.identifier
    window.setTimeout(() => { if (copied.value === c.identifier) copied.value = null }, 2000)
  } catch {
    copied.value = null
  }
}
</script>

<style scoped>
.msgs { display: grid; gap: var(--sp-4); }
.head { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: var(--sp-2) var(--sp-4); }
.head h1 { font-size: var(--fs-xl); }
.sub { color: var(--text-2); margin-top: var(--sp-1); }
.ctx {
  display: inline-block; margin-right: var(--sp-2); padding: 0 var(--sp-2); border-radius: var(--r-pill); font-size: var(--fs-xs);
  font-weight: 600; border: 1px solid var(--info-line); background: var(--info-soft); color: var(--info);
}
.ctx[data-mode='live'] { border-color: var(--ok-line); background: var(--ok-soft); color: var(--ok); }
.updated { font-size: var(--fs-sm); color: var(--text-3); white-space: nowrap; }
.delivery {
  font-size: var(--fs-sm); color: var(--text-2); background: var(--surface-1); border: 1px solid var(--border);
  border-left: 4px solid var(--watch-solid); border-radius: var(--r-sm); padding: var(--sp-2) var(--sp-3);
}
.delivery strong { color: var(--text); }
.stale { font-size: var(--fs-sm); color: var(--watch); background: var(--watch-soft); border: 1px solid var(--watch-line); border-radius: var(--r-sm); padding: var(--sp-2) var(--sp-3); }

.card { background: var(--surface-1); border: 1px solid var(--border); border-radius: var(--r-md); box-shadow: var(--shadow-card); padding: var(--sp-4); min-width: 0; }
.card.flush { padding: 0; }
.card h2 { font-size: var(--fs-lg); font-weight: 600; }
.card-sub { font-size: var(--fs-sm); color: var(--text-2); margin: 2px 0 var(--sp-3); }
.card-head { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: var(--sp-3); padding: var(--sp-4) var(--sp-4) var(--sp-3); border-bottom: 1px solid var(--border); }
.card-head .card-sub { margin-bottom: 0; }
.none { font-size: var(--fs-sm); color: var(--text-2); padding: var(--sp-4); }
.card .none { padding: 0; }
.card.flush .none { padding: var(--sp-4); }

.empty { display: flex; flex-direction: column; align-items: center; text-align: center; gap: var(--sp-2); padding: var(--sp-8) var(--sp-4); color: var(--text-3); }
.empty-title { font-size: var(--fs-md); font-weight: 600; color: var(--text); }
.empty-body { font-size: var(--fs-sm); color: var(--text-2); max-width: 56ch; }
.danger-icon { color: var(--danger); }
.loading { display: grid; gap: var(--sp-4); }
.loading .skeleton { display: block; }
.sk-kpis { height: 6rem; }
.sk-list { height: 24rem; }

.btn {
  display: inline-flex; align-items: center; gap: var(--sp-2); height: 2.25rem; padding: 0 var(--sp-3); border-radius: var(--r-sm);
  background: var(--surface-1); border: 1px solid var(--border-strong); color: var(--text); font-size: var(--fs-md); font-weight: 500; cursor: pointer;
}
.btn:hover { background: var(--surface-3); }
.btn.sm { height: 1.875rem; padding: 0 var(--sp-2); font-size: var(--fs-sm); margin-top: var(--sp-2); }

.kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--sp-4); }
.kpi { background: var(--surface-1); border: 1px solid var(--border); border-radius: var(--r-md); box-shadow: var(--shadow-card); padding: var(--sp-4); display: grid; gap: 2px; border-top: 4px solid var(--info); }
.kpi.ok { border-top-color: var(--ok); }
.kpi.watch { border-top-color: var(--watch-solid); }
.kpi.danger { border-top-color: var(--danger-solid); }
.k-label { font-size: var(--fs-xs); font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; color: var(--text-3); }
.k-value { font-size: var(--fs-2xl); font-weight: 600; font-variant-numeric: tabular-nums; line-height: var(--lh-tight); }
.kpi.ok .k-value { color: var(--ok); }
.kpi.watch .k-value { color: var(--watch); }
.kpi.danger .k-value { color: var(--danger); }
.k-hint { font-size: var(--fs-sm); color: var(--text-2); }

.grid2 { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); gap: var(--sp-4); align-items: start; }
.side { display: grid; gap: var(--sp-4); min-width: 0; }

.tools { display: flex; flex-wrap: wrap; gap: var(--sp-2); align-items: center; }
.seg { display: inline-flex; padding: 2px; gap: 2px; background: var(--surface-2); border: 1px solid var(--border); border-radius: var(--r-sm); }
.seg button { height: 1.75rem; padding: 0 var(--sp-3); border-radius: 4px; border: 0; background: transparent; font-size: var(--fs-sm); font-weight: 500; color: var(--text-2); cursor: pointer; }
.seg button[aria-pressed='true'] { background: var(--accent-soft); color: var(--accent); }
.search { display: inline-flex; align-items: center; gap: var(--sp-2); height: 2rem; padding: 0 var(--sp-2); border: 1px solid var(--border-strong); border-radius: var(--r-sm); background: var(--surface-1); color: var(--text-3); }
.search input { border: 0; outline: none; background: transparent; font-size: var(--fs-sm); color: var(--text); width: 12rem; height: 100%; padding: 0; }
.search:focus-within { border-color: var(--accent-solid); }

.log { list-style: none; margin: 0; padding: 0; }
.msg { display: grid; grid-template-columns: 7rem minmax(0, 1fr); gap: var(--sp-3); padding: var(--sp-3) var(--sp-4); border-bottom: 1px solid var(--border); }
.msg:last-child { border-bottom: 0; }
.msg[data-kind='DEPUTY_ESCALATION'] { background: var(--watch-soft); box-shadow: inset 3px 0 0 var(--watch-solid); }
.when { display: grid; align-content: start; gap: 2px; }
.when .t { font-size: var(--fs-md); font-weight: 600; color: var(--text); }
.rel { font-size: var(--fs-xs); color: var(--text-3); }
.body { display: grid; gap: var(--sp-2); min-width: 0; }
.row1 { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--sp-3); flex-wrap: wrap; }
.who { display: grid; gap: 1px; min-width: 0; }
.name { font-weight: 600; font-size: var(--fs-md); }
.role { font-size: var(--fs-xs); color: var(--text-3); }
.what { font-size: var(--fs-md); color: var(--text); }
.due { color: var(--text-2); }
.text {
  margin: 0; padding: var(--sp-2) var(--sp-3); background: var(--surface-2); border: 1px solid var(--border);
  border-radius: var(--r-sm); color: var(--text); white-space: pre-wrap; overflow-wrap: anywhere;
}
.foot { display: flex; flex-wrap: wrap; align-items: center; gap: var(--sp-2) var(--sp-3); }
.meta { display: inline-flex; align-items: center; gap: 4px; font-size: var(--fs-xs); color: var(--text-3); }
.open { display: inline-flex; align-items: center; gap: 4px; font-size: var(--fs-sm); font-weight: 500; color: var(--accent); margin-left: auto; }
.outcome { font-size: var(--fs-sm); color: var(--text-2); }
.outcome[data-tone='ok'] { color: var(--ok); }
.outcome[data-tone='watch'] { color: var(--watch); }
.outcome[data-tone='danger'] { color: var(--danger); }

.chip { display: inline-flex; align-items: center; height: 1.375rem; padding: 0 var(--sp-2); border-radius: var(--r-pill); font-size: var(--fs-xs); font-weight: 600; white-space: nowrap; border: 1px solid var(--border); background: var(--surface-3); color: var(--text-2); }
.chip[data-tone='ok'] { background: var(--ok-soft); color: var(--ok); border-color: var(--ok-line); }
.chip[data-tone='watch'] { background: var(--watch-soft); color: var(--watch); border-color: var(--watch-line); }
.chip[data-tone='danger'] { background: var(--danger-soft); color: var(--danger); border-color: var(--danger-line); }
.chip[data-tone='info'] { background: var(--info-soft); color: var(--info); border-color: var(--info-line); }

.caps { list-style: none; display: grid; gap: var(--sp-3); margin: 0; padding: 0; min-width: 0; }
.cap { border: 1px solid var(--border); border-radius: var(--r-sm); padding: var(--sp-3); display: grid; gap: var(--sp-2); min-width: 0; }
.cap > * { min-width: 0; }
.cap .row1 { justify-content: flex-start; align-items: center; gap: var(--sp-2); }
.cap .t { margin-left: auto; font-size: var(--fs-sm); color: var(--text-2); }
.cap-headline { font-weight: 600; }
.cap-areas { font-size: var(--fs-sm); color: var(--text-2); }
.gateway { font-size: var(--fs-sm); color: var(--text-2); }
.gateway[data-sim='yes'] { color: var(--watch); font-weight: 500; }
.cap summary { cursor: pointer; font-size: var(--fs-sm); color: var(--accent); }
.xml { margin: var(--sp-2) 0 0; max-width: 100%; max-height: 18rem; overflow: auto; padding: var(--sp-2); background: var(--surface-2); border: 1px solid var(--border); border-radius: var(--r-sm); font-family: var(--font-mono); font-size: var(--fs-xs); white-space: pre; }
.cap-id { font-size: var(--fs-xs); color: var(--text-3); overflow-wrap: anywhere; }

.plain { list-style: none; display: grid; gap: var(--sp-2); margin: var(--sp-2) 0 0; padding: 0; }
.plain li { display: grid; grid-template-columns: 1rem minmax(0, 1fr); gap: var(--sp-2); font-size: var(--fs-sm); color: var(--text-2); }
.plain li svg { margin-top: 2px; color: var(--text-3); }

@media (max-width: 1023px) {
  .kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .grid2 { grid-template-columns: minmax(0, 1fr); }
  .msg { grid-template-columns: minmax(0, 1fr); }
  .when { grid-auto-flow: column; justify-content: start; gap: var(--sp-2); align-items: baseline; }
  .open { margin-left: 0; }
}
</style>
