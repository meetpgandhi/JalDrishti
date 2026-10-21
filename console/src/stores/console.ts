import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { api, endpoints } from '@/lib/api'
import { ACTION_STATES, CORRIDOR_ID, DIRECT_WARNING_RULES } from '@/lib/labels'
import { isPastSim, minutesBetween, toMs } from '@/lib/time'
import type {
  ArrivalBand, AskResponse, CapMessageEntry, ClockUpdate, CorridorDetails, CorridorState, DispatchedAction, DutyHolder,
  FieldReport, ForecasterActionResponse, Incident, IncidentEvent, IncidentLevel, InjectionRequest,
  InjectionResponse, MessageLogEntry, ReadingUpdate, RunSnapshot, StationStateUpdate, StationTile, Tag, TimelineMark, Trust,
} from '@/types/api'

export type ForecasterAction = 'escalate' | 'cancel-watch' | 'stand-down'
/** live = real-time operations on runId 'live' (default). training = replaying a scenario. */
export type AppMode = 'live' | 'training'
export type ActionGroup = 'unresolved' | 'late' | 'waiting' | 'confirmed'

function errorText(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

function toTag(value: string | null | undefined): Tag | null {
  const c = value?.trim().charAt(0)
  return c === 'P' || c === 'D' || c === 'A' ? c : null
}

export const useConsoleStore = defineStore('console', () => {
  // ── Reference data (loaded once) ──
  const corridor = ref<CorridorDetails | null>(null)
  const dutyHolders = ref<DutyHolder[]>([])

  // ── Mode, clock, run ──
  const mode = ref<AppMode>('live')
  const nowMs = ref(Date.now())
  const run = ref<RunSnapshot | null>(null)
  const manualSpeed = ref<number | null>(null)
  const interpolatedSimMs = ref<number | null>(null)
  let lastAnchorWallMs = 0
  let lastAnchorSimMs = 0

  function syncSimClock(simIso: string | null | undefined) {
    if (!simIso) {
      interpolatedSimMs.value = null
      lastAnchorWallMs = 0
      lastAnchorSimMs = 0
      return
    }
    const ms = toMs(simIso)
    if (ms === null) return
    lastAnchorSimMs = ms
    lastAnchorWallMs = Date.now()
    interpolatedSimMs.value = ms
  }

  window.setInterval(() => {
    nowMs.value = Date.now()
    if (mode.value === 'training' && run.value?.status === 'RUNNING' && lastAnchorWallMs > 0) {
      const elapsedWall = Date.now() - lastAnchorWallMs
      const sp = manualSpeed.value ?? run.value.speed ?? 1
      interpolatedSimMs.value = lastAnchorSimMs + Math.round(elapsedWall * sp)
    }
  }, 100)

  const stations = ref<StationTile[]>([])
  const readingTags = ref<Record<string, Tag>>({})
  const incident = ref<Incident | null>(null)
  const bands = ref<ArrivalBand[]>([])
  const events = ref<IncidentEvent[]>([])
  const asHappened = ref<TimelineMark[]>([])
  const actions = ref<DispatchedAction[]>([])
  const reports = ref<FieldReport[]>([])
  const messages = ref<MessageLogEntry[]>([])
  const capMessages = ref<CapMessageEntry[]>([])
  const messagesLoaded = ref(false)

  // ── Status flags ──
  const starting = ref(false)
  const errors = ref<Record<string, string | null>>({})
  const setError = (area: string, e: unknown) => { errors.value = { ...errors.value, [area]: e === null ? null : errorText(e) } }

  // ── Derived ──
  /** 'live' in live mode; the scenario run id in training mode (null until a run starts). */
  const runId = computed(() => (mode.value === 'live' ? 'live' : run.value?.runId ?? null))
  /** The clock everything is measured against: real time in live mode, the simulation clock in training. */
  const simNow = computed(() => {
    if (mode.value === 'live') return new Date(nowMs.value).toISOString()
    if (!run.value) return null
    if (interpolatedSimMs.value !== null) return new Date(interpolatedSimMs.value).toISOString()
    return run.value.simTime ?? null
  })
  const level = computed<IncidentLevel>(() => incident.value?.level ?? 'NORMAL')
  const isPlaying = computed(() => mode.value === 'training' && run.value?.status === 'RUNNING')
  const corridorName = computed(() => {
    const rivers = [...new Set([...stations.value].sort((a, b) => a.riverKm - b.riverKm).map(s => s.river))]
    return rivers.length ? rivers.join(' → ') : ''
  })
  const stationsSorted = computed(() => [...stations.value].sort((a, b) => a.riverKm - b.riverKm))
  const silentCount = computed(() => stations.value.filter(s => s.silent).length)

  const siteName = (siteId: string | null | undefined): string => {
    if (!siteId) return '—'
    return corridor.value?.sites.find(s => s.id === siteId)?.nameEn ?? siteId
  }
  const holder = (id: string): DutyHolder | null => dutyHolders.value.find(h => h.id === id) ?? null

  /** Incident events in time order, without the internal BANDS_UPDATED entries. */
  const timelineEvents = computed(() =>
    events.value
      .filter(e => e.type !== 'BANDS_UPDATED')
      .sort((a, b) => (toMs(a.at) ?? 0) - (toMs(b.at) ?? 0) || (a.seq ?? 0) - (b.seq ?? 0)),
  )

  /** The event that put the incident at its current level (for "Raised 08:53 by R5"). */
  const currentLevelEvent = computed<IncidentEvent | null>(() => {
    const evs = timelineEvents.value.filter(e => !incident.value || e.incidentId === incident.value.id)
    for (let i = evs.length - 1; i >= 0; i--) {
      const e = evs[i]
      if (e.type === 'LEVEL_CHANGED' || e.type === 'OPENED' || e.type === 'STOOD_DOWN') return e
    }
    return null
  })

  const firstWarningEvent = computed<IncidentEvent | null>(() =>
    timelineEvents.value.find(e =>
      e.type === 'LEVEL_CHANGED' || (e.type === 'OPENED' && !!e.ruleId && DIRECT_WARNING_RULES.has(e.ruleId)),
    ) ?? null,
  )

  /** The next downstream site the wave can reach, measured against the sim clock. */
  const nextArrival = computed(() => {
    if (!simNow.value) return null
    const upcoming = bands.value
      .filter(b => (minutesBetween(simNow.value, b.latestArrival) ?? -1) >= 0)
      .sort((a, b) => (toMs(a.earliestArrival) ?? 0) - (toMs(b.earliestArrival) ?? 0))
    const band = upcoming[0]
    if (!band) return null
    return {
      band,
      minFromNow: minutesBetween(simNow.value, band.earliestArrival) ?? 0,
      maxFromNow: minutesBetween(simNow.value, band.latestArrival) ?? 0,
    }
  })

  const actionGroup = (a: DispatchedAction): ActionGroup => {
    if (a.state === 'ACKED') return 'confirmed'
    if (a.state === 'UNRESOLVED' || a.state === 'DECLINED' || a.state === 'ESCALATED_3') return 'unresolved'
    if (a.deadlineAt && isPastSim(a.deadlineAt, simNow.value)) return 'late'
    return 'waiting'
  }

  /** Working order for the forecaster: unresolved, late, waiting, confirmed; then by deadline. */
  const actionsSorted = computed(() => {
    const rank: Record<ActionGroup, number> = { unresolved: 0, late: 1, waiting: 2, confirmed: 3 }
    const deadline = (a: DispatchedAction) => (a.deadlineAt ? toMs(a.deadlineAt) ?? 0 : -Infinity)
    return [...actions.value].sort((a, b) =>
      rank[actionGroup(a)] - rank[actionGroup(b)]
      || deadline(a) - deadline(b)
      || (toMs(a.sentAt) ?? 0) - (toMs(b.sentAt) ?? 0),
    )
  })

  const actionCounts = computed(() => {
    const c = { total: actions.value.length, confirmed: 0, waiting: 0, escalating: 0, unresolved: 0, needsAttention: 0 }
    for (const a of actions.value) {
      if (ACTION_STATES[a.state].needsAttention) c.needsAttention++
      if (a.state === 'ACKED') c.confirmed++
      else if (a.state === 'SENT' || a.state === 'DELIVERED') c.waiting++
      else if (a.state === 'ESCALATED_1' || a.state === 'ESCALATED_2') c.escalating++
      else c.unresolved++
    }
    return c
  })

  // ── Loading ──
  async function loadReferenceData() {
    try {
      corridor.value = await api.get<CorridorDetails>(endpoints.corridor(CORRIDOR_ID))
      setError('corridor', null)
    } catch (e) { setError('corridor', e) }
    try {
      dutyHolders.value = await api.get<DutyHolder[]>(endpoints.dutyHolders())
      setError('holders', null)
    } catch (e) { setError('holders', e) }
  }

  async function refreshState() {
    if (!runId.value) return
    try {
      const s = await api.get<CorridorState>(endpoints.corridorState(CORRIDOR_ID, runId.value))
      stations.value = s.stations ?? []
      incident.value = s.openIncident ?? null
      setError('state', null)
    } catch (e) { setError('state', e) }
  }

  async function refreshEvents() {
    if (!runId.value) return
    try {
      events.value = await api.get<IncidentEvent[]>(endpoints.incidentEvents(runId.value))
      setError('events', null)
    } catch (e) { setError('events', e) }
  }

  async function refreshAsHappened() {
    if (!runId.value) return
    try {
      asHappened.value = await api.get<TimelineMark[]>(endpoints.asHappened(runId.value))
      setError('asHappened', null)
    } catch (e) { setError('asHappened', e) }
  }

  async function refreshActions() {
    if (!incident.value) { actions.value = []; return }
    try {
      actions.value = await api.get<DispatchedAction[]>(endpoints.actions(incident.value.id))
      setError('actions', null)
    } catch (e) { setError('actions', e) }
  }

  // Poll the action board every 3 s while an incident is open (the stream has no action events).
  let actionTimer: number | null = null
  watch(() => incident.value?.id ?? null, (id) => {
    if (actionTimer !== null) { window.clearInterval(actionTimer); actionTimer = null }
    if (id) {
      void refreshActions()
      actionTimer = window.setInterval(() => { void refreshActions() }, 3000)
    } else {
      actions.value = []
    }
  })

  // ── Message log (every message JalDrishti sent in this run, and the CAP alerts) ──
  async function refreshMessages() {
    const id = runId.value
    if (!id) { messages.value = []; capMessages.value = []; messagesLoaded.value = false; return }
    try {
      const [m, c] = await Promise.all([
        api.get<MessageLogEntry[]>(endpoints.messages(id)),
        api.get<CapMessageEntry[]>(endpoints.capMessages(id)),
      ])
      if (id !== runId.value) return // the run changed while this was loading
      messages.value = m
      capMessages.value = c
      messagesLoaded.value = true
      setError('messages', null)
    } catch (e) { setError('messages', e) }
  }

  // Poll every 3 s only while a view that shows the log is open. Each caller gets its own stop function.
  let messageTimer: number | null = null
  let messageWatchers = 0
  function watchMessages(): () => void {
    messageWatchers++
    if (messageTimer === null) {
      void refreshMessages()
      messageTimer = window.setInterval(() => { void refreshMessages() }, 3000)
    }
    let stopped = false
    return () => {
      if (stopped) return
      stopped = true
      messageWatchers = Math.max(0, messageWatchers - 1)
      if (messageWatchers === 0 && messageTimer !== null) { window.clearInterval(messageTimer); messageTimer = null }
    }
  }
  watch(runId, () => {
    messages.value = []
    capMessages.value = []
    messagesLoaded.value = false
    if (messageTimer !== null) void refreshMessages()
  })

  // ── Mode switching ──
  function clearLiveData() {
    manualSpeed.value = null
    syncSimClock(null)
    stations.value = []
    incident.value = null
    bands.value = []
    events.value = []
    actions.value = []
    reports.value = []
    asHappened.value = []
    readingTags.value = {}
    messages.value = []
    capMessages.value = []
    messagesLoaded.value = false
  }

  /** Load everything for the current mode. Call once when the operator console opens. */
  async function init() {
    await loadReferenceData()
    if (mode.value === 'live') await Promise.all([refreshState(), refreshEvents()])
  }

  async function goLive() {
    mode.value = 'live'
    run.value = null
    clearLiveData()
    setError('run', null)
    await Promise.all([refreshState(), refreshEvents()])
  }

  function enterTraining() {
    mode.value = 'training'
    run.value = null
    clearLiveData()
  }

  // ── Run control (training mode) ──
  async function startRun(scenario: string, variants: Record<string, unknown> | null) {
    starting.value = true
    try {
      const snap = await api.post<RunSnapshot>(endpoints.startRun(), { scenario, variants })
      mode.value = 'training'
      run.value = snap
      clearLiveData()
      syncSimClock(snap.simTime)
      setError('run', null)
      await Promise.all([refreshState(), refreshEvents(), refreshAsHappened()])
    } catch (e) {
      setError('run', e)
    } finally {
      starting.value = false
    }
  }

  async function control(kind: 'play' | 'pause') {
    if (!runId.value) return
    try {
      run.value = await api.post<RunSnapshot>(kind === 'play' ? endpoints.play(runId.value) : endpoints.pause(runId.value))
      if (run.value?.simTime) syncSimClock(run.value.simTime)
      if (kind === 'play' && manualSpeed.value !== null) {
        void api.post(endpoints.speed(runId.value), { speed: manualSpeed.value })
      }
      setError('run', null)
    } catch (e) { setError('run', e) }
  }

  async function setSpeed(speed: number) {
    if (!runId.value) return
    manualSpeed.value = speed
    if (run.value) {
      run.value = { ...run.value, speed }
      if (run.value.simTime) syncSimClock(run.value.simTime)
    }
    try {
      const snap = await api.post<RunSnapshot>(endpoints.speed(runId.value), { speed })
      run.value = {
        ...snap,
        speed,
      }
      if (snap.simTime) syncSimClock(snap.simTime)
      setError('run', null)
    } catch (e) { setError('run', e) }
  }

  // ── SSE handlers ──
  function onReading(data: unknown) {
    const r = data as ReadingUpdate
    const s = stations.value.find(x => x.stationId === r.stationId)
    if (s) {
      s.lastLevelM = r.levelM
      s.lastObservedAt = r.observedAt
    }
    const tag = toTag(r.tag)
    if (tag) readingTags.value = { ...readingTags.value, [r.stationId]: tag }
  }

  function onStationState(data: unknown) {
    const u = data as StationStateUpdate
    const s = stations.value.find(x => x.stationId === u.stationId)
    if (!s) return
    s.state = u.state
    s.silent = u.silent
    s.rateAlert = u.rateAlert
    if (u.lastLevelM !== null && u.lastLevelM !== undefined) s.lastLevelM = u.lastLevelM
    if (u.lastObservedAt) s.lastObservedAt = u.lastObservedAt
  }

  function onClock(data: unknown) {
    const c = data as ClockUpdate
    if (mode.value !== 'training' || !run.value) return
    if (c.status === 'COMPLETE') {
      manualSpeed.value = null
    }
    const currentSpeed = manualSpeed.value ?? c.speed ?? run.value.speed
    run.value = {
      ...run.value,
      simTime: c.simTime ?? run.value.simTime,
      speed: currentSpeed,
      status: c.status ?? run.value.status,
    }
    if (c.simTime) syncSimClock(c.simTime)
  }

  function onIncidentChanged() {
    void refreshState()
    void refreshEvents()
  }

  function onBands(data: unknown) {
    if (Array.isArray(data)) bands.value = data as ArrivalBand[]
    else void refreshEvents()
  }

  // ── Forecaster actions (a written reason is required) ──
  async function forecasterAction(kind: ForecasterAction, reason: string) {
    if (!incident.value) throw new Error('No open incident')
    const id = incident.value.id
    const url = kind === 'escalate' ? endpoints.escalate(id) : kind === 'cancel-watch' ? endpoints.cancelWatch(id) : endpoints.standDown(id)
    const res = await api.post<ForecasterActionResponse>(url, { reason })
    await Promise.all([refreshState(), refreshEvents()])
    return res
  }

  // ── Field reports (this console session only: the backend has no list endpoint yet) ──
  async function submitReport(text: string, role: Trust) {
    if (!runId.value) throw new Error('Start a run first')
    const saved = await api.post<FieldReport>(endpoints.reports(runId.value), { text, role })
    reports.value = [saved, ...reports.value]
    void refreshEvents()
    return saved
  }

  async function inject(req: InjectionRequest) {
    if (!runId.value) throw new Error('Start a run first')
    const res = await api.post<InjectionResponse>(endpoints.inject(runId.value), req)
    void refreshState()
    return res
  }

  async function ask(question: string) {
    return api.post<AskResponse>(endpoints.ask(), { question, runId: runId.value ?? 'live' })
  }

  return {
    mode, corridor, dutyHolders, run, manualSpeed, stations, readingTags, incident, bands, events, asHappened, actions, reports,
    messages, capMessages, messagesLoaded,
    starting, errors,
    runId, simNow, level, isPlaying, corridorName, stationsSorted, silentCount, timelineEvents,
    currentLevelEvent, firstWarningEvent, nextArrival, actionsSorted, actionCounts,
    siteName, holder, actionGroup,
    init, goLive, enterTraining,
    loadReferenceData, refreshState, refreshEvents, refreshAsHappened, refreshActions, refreshMessages, watchMessages,
    startRun, control, setSpeed,
    onReading, onStationState, onClock, onIncidentChanged, onBands,
    forecasterAction, submitReport, inject, ask,
  }
})
