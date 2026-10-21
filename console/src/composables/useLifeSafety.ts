import { computed, reactive, ref, shallowRef, watch } from 'vue'
import { useConsoleStore } from '@/stores/console'
import {
  DEFAULT_PARAMS, REFERENCE_PLANS, arrivalOverrideFromBands, delayCurve, estimateFrontMs, nptHourAndWeekday, runLifeSafety,
  type ExposureData, type ModelResult, type Quantiles, type RunOptions, type WarningPlan,
} from '@/lib/lifeSafety'

// Runs the life-safety model for the People-at-risk page.
// Incident mode: uses the live incident (arrival windows from the backend, the real warning time).
// Planning mode: no open incident with arrival windows — compares warning strategies for a 26 Aug-type outburst.

export const EXPOSURE_URL = '/data/exposure.json'

export interface WhatIf { sirens: boolean; volunteers: boolean; cellBroadcast: boolean; upstream: boolean }
export interface PlanRow { plan: WarningPlan; result: ModelResult; role: 'primary' | 'baseline' | 'other' }
export interface CurvePoint { decisionMin: number; deaths: Quantiles }
export interface CurveMarker { label: string; decisionMin: number }

const JALDRISHTI = REFERENCE_PLANS.find(p => p.id === 'jaldrishti') as WarningPlan
const AUG26 = REFERENCE_PLANS.find(p => p.id === 'aug26') as WarningPlan
const UPSTREAM = REFERENCE_PLANS.find(p => p.id === 'upstream') as WarningPlan

const nextFrame = () => new Promise<void>(resolve => window.setTimeout(resolve, 0))

export function useLifeSafety() {
  const store = useConsoleStore()
  const data = shallowRef<ExposureData | null>(null)
  const loadError = ref<string | null>(null)
  const whatIf = reactive<WhatIf>({ sirens: false, volunteers: false, cellBroadcast: false, upstream: false })
  /** Planning mode only: hour of day in Nepal time (the 26 Aug wave passed Rasuwagadhi at 08:44). */
  const hourNpt = ref(9)
  const rows = shallowRef<PlanRow[]>([])
  const curve = shallowRef<CurvePoint[]>([])
  const markers = shallowRef<CurveMarker[]>([])
  const computing = ref(false)

  async function load() {
    try {
      const res = await fetch(EXPOSURE_URL)
      if (!res.ok) throw new Error(`${EXPOSURE_URL} returned ${res.status}`)
      data.value = (await res.json()) as ExposureData
      loadError.value = null
    } catch (e) {
      loadError.value = e instanceof Error ? e.message : String(e)
    }
  }

  const frontMs = computed(() => (data.value && store.bands.length ? estimateFrontMs(data.value, store.bands) : null))
  const context = computed<'incident' | 'planning'>(() =>
    store.incident && (store.incident.level === 'WATCH' || store.incident.level === 'WARNING') && frontMs.value !== null ? 'incident' : 'planning',
  )
  /** Minutes since the front passed km 0, rounded to the minute so the model is not re-run every second. */
  const nowMin = computed(() => {
    const now = Date.parse(store.simNow ?? '')
    return frontMs.value === null || !Number.isFinite(now) ? null : Math.floor((now - frontMs.value) / 60000)
  })
  const warningMin = computed(() => {
    const at = store.firstWarningEvent?.at
    return frontMs.value === null || !at ? null : Math.round((Date.parse(at) - frontMs.value) / 60000)
  })

  function withWhatIf(plan: WarningPlan): WarningPlan {
    const addEverywhere: Array<'siren' | 'volunteers'> = []
    if (whatIf.sirens) addEverywhere.push('siren')
    if (whatIf.volunteers) addEverywhere.push('volunteers')
    const channels = whatIf.cellBroadcast ? [...plan.channels, { id: 'cellBroadcast' as const, offsetMin: 1 }] : plan.channels
    return { ...plan, channels, addEverywhere }
  }

  function buildPlans(): { plans: Array<{ plan: WarningPlan; role: PlanRow['role'] }>; opts: RunOptions; curveBase: WarningPlan; curveMins: number[]; marks: CurveMarker[] } {
    if (context.value === 'incident' && data.value && frontMs.value !== null) {
      const opts: RunOptions = { ...nptHourAndWeekday(frontMs.value), arrivalOverride: arrivalOverrideFromBands(data.value, store.bands, frontMs.value) }
      const now = nowMin.value ?? 0
      const issued = warningMin.value
      const plans: Array<{ plan: WarningPlan; role: PlanRow['role'] }> = []
      if (issued !== null) plans.push({ plan: withWhatIf({ ...JALDRISHTI, id: 'issued', label: 'Warning as issued', decisionMin: issued }), role: 'primary' })
      else plans.push({ plan: withWhatIf({ ...JALDRISHTI, id: 'now', label: 'If we warn now', decisionMin: now }), role: 'primary' })
      plans.push({ plan: withWhatIf({ ...JALDRISHTI, id: 'wait15', label: issued !== null ? 'If it had come 15 min later' : 'If we wait 15 more minutes', decisionMin: (issued ?? now) + 15 }), role: 'other' })
      plans.push({ plan: { ...JALDRISHTI, id: 'none', label: 'No warning', decisionMin: null, channels: [] }, role: 'baseline' })
      const start = Math.max(0, Math.min(issued ?? now, now) - 15)
      const curveMins = Array.from({ length: 13 }, (_, i) => start + i * 5)
      const marks: CurveMarker[] = [{ label: 'Now', decisionMin: now }]
      if (issued !== null) marks.push({ label: 'Warning issued', decisionMin: issued })
      return { plans, opts, curveBase: withWhatIf(JALDRISHTI), curveMins, marks }
    }
    const opts: RunOptions = { hourNpt: hourNpt.value, weekday: 3 }
    const primary = withWhatIf(whatIf.upstream ? UPSTREAM : JALDRISHTI)
    return {
      plans: [
        { plan: primary, role: 'primary' },
        { plan: AUG26, role: 'baseline' },
        { plan: whatIf.upstream ? withWhatIf(JALDRISHTI) : withWhatIf(UPSTREAM), role: 'other' },
      ],
      opts,
      curveBase: withWhatIf(JALDRISHTI),
      curveMins: [-30, -20, -10, 0, 5, 10, 15, 20, 25, 30, 40, 50, 60],
      marks: [
        { label: 'Upstream data', decisionMin: UPSTREAM.decisionMin ?? -15 },
        { label: 'JalDrishti', decisionMin: JALDRISHTI.decisionMin ?? 9 },
        { label: '26 Aug SMS', decisionMin: AUG26.decisionMin ?? 31 },
      ],
    }
  }

  let generation = 0
  async function recompute() {
    if (!data.value) return
    const myGen = ++generation
    computing.value = true
    const { plans, opts, curveBase, curveMins, marks } = buildPlans()
    const out: PlanRow[] = []
    for (const p of plans) {
      await nextFrame()
      if (myGen !== generation) return
      out.push({ plan: p.plan, role: p.role, result: runLifeSafety(data.value, p.plan, DEFAULT_PARAMS, opts) })
    }
    rows.value = out
    const pts: CurvePoint[] = []
    for (const m of curveMins) {
      await nextFrame()
      if (myGen !== generation) return
      pts.push(delayCurve(data.value, curveBase, DEFAULT_PARAMS, opts, [m])[0])
    }
    curve.value = pts
    markers.value = marks
    computing.value = false
  }

  watch(
    () => [data.value, context.value, frontMs.value, warningMin.value, nowMin.value, { ...whatIf }, hourNpt.value, store.bands.length] as const,
    () => { void recompute() },
    { deep: true },
  )

  const primary = computed(() => rows.value.find(r => r.role === 'primary') ?? null)
  const baseline = computed(() => rows.value.find(r => r.role === 'baseline') ?? null)
  /** Expected lives saved by the primary plan compared with the baseline (median of each). */
  const livesSaved = computed(() =>
    primary.value && baseline.value ? Math.max(0, baseline.value.result.totals.deaths.p50 - primary.value.result.totals.deaths.p50) : null,
  )

  return { data, loadError, load, context, frontMs, whatIf, hourNpt, rows, curve, markers, computing, primary, baseline, livesSaved, params: DEFAULT_PARAMS }
}
