// Probabilistic life-safety model for a flood wave travelling down the corridor.
//
// For every downstream site and group of people (residents, students, workers, travellers) it estimates,
// with uncertainty, how many people are in the path, how many get a warning before the water arrives,
// how many reach safety, and how many may die. Structure follows life-loss models such as LifeSim:
//   warning issued → warning reaches people (per channel) → people decide to move → they walk to safety.
// People still in the flood path when it arrives die at the rates Graham (1999, US Bureau of Reclamation,
// DSO-99-06) gives for floods with no warning, by flood severity.
//
// Everything is a Monte Carlo over the uncertain inputs (fixed seed, so results are repeatable).
// These are planning estimates, not predictions of what will happen to any person.

import type { Tag } from '@/types/api'

export type Severity = 'high' | 'medium' | 'low'
export type GroupKind = 'residents' | 'students' | 'workers' | 'travellers'
export type Presence = 'always' | 'schoolHours' | 'dayShift' | 'daytime'
export type ChannelId = 'dutyCall' | 'sms' | 'siren' | 'volunteers' | 'cellBroadcast' | 'informalCall'
export type Tri = [number, number, number] // [low, most likely, high]

export interface ExposureGroup {
  kind: GroupKind
  low: number
  best: number
  high: number
  presence: Presence
  tag: Tag
  source: string
}

export interface ExposureSite {
  id: string
  name: string
  riverKm: number
  riverKmTag: Tag
  severity: Severity
  severityNote: string
  minutesToSafety: [number, number]
  channels: { siren: boolean; volunteers: boolean; dutyCall: boolean }
  groups: ExposureGroup[]
}

export interface ExposureData {
  version: number
  corridorId: string
  /** [riverKm, earliest, latest] minutes after the wave front passes km 0 */
  travelTime: Array<[number, number, number]>
  travelTimeTag: Tag
  travelTimeSource: string
  sites: ExposureSite[]
  sources: Array<{ id: string; title: string; url: string }>
}

export interface ChannelParams {
  label: string
  /** share of each group the channel reaches if it is used */
  reach: Record<GroupKind, Tri>
  /** mean minutes from the channel starting to a person receiving it */
  delayMin: Tri
  note: string
}

export interface ModelParams {
  channels: Record<ChannelId, ChannelParams>
  /** share of warned people who act on the warning */
  compliance: { precise: Tri; vague: Tri }
  /** mean minutes between receiving a warning and starting to move */
  responseMin: { precise: Tri; vague: Tri }
  /** death rate for people still in the flood path when it arrives (Graham 1999, "no warning") */
  fatality: Record<Severity, Tri>
  draws: number
  seed: number
}

export interface PlanChannel {
  id: ChannelId
  /** minutes after the warning decision that this channel starts */
  offsetMin: number
  /** limit to these sites (default: every site where the channel exists) */
  sites?: string[]
  /** limit to these groups */
  groups?: GroupKind[]
}

export interface WarningPlan {
  id: string
  label: string
  /** minutes after the wave passes km 0 when the warning decision is made (negative = before) */
  decisionMin: number | null
  message: 'precise' | 'vague'
  channels: PlanChannel[]
  /** what-if: treat these channels as present at every site */
  addEverywhere?: Array<'siren' | 'volunteers'>
}

export interface Quantiles { mean: number; p05: number; p50: number; p95: number }

export interface SiteResult {
  id: string
  name: string
  riverKm: number
  severity: Severity
  arrivalMin: [number, number]
  /** minutes between the warning decision and the earliest possible arrival (null = no warning) */
  warningMarginMin: number | null
  /** true when the warning comes later than the time most people need to get it, react and walk to safety */
  beyondReach: boolean
  peopleAtRisk: Quantiles
  warnedBeforeArrival: number
  reachedSafety: number
  deaths: Quantiles
  survivalRate: number
}

export interface ModelResult {
  plan: WarningPlan
  sites: SiteResult[]
  totals: { peopleAtRisk: Quantiles; deaths: Quantiles; warnedBeforeArrival: number; reachedSafety: number; survivalRate: number }
}

export interface RunOptions {
  /** hour of day in Nepal time (0–23) and day of week (0 = Sunday) — decides who is present */
  hourNpt: number
  weekday: number
  /** use these arrival windows (minutes after the front passes km 0) instead of the travel-time curve */
  arrivalOverride?: Record<string, [number, number]>
}

// ── random numbers (deterministic) ──
function mulberry32(seed: number) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function tri(rand: () => number, [lo, mode, hi]: Tri): number {
  if (hi <= lo) return mode
  const u = rand()
  const c = (mode - lo) / (hi - lo)
  return u < c ? lo + Math.sqrt(u * (hi - lo) * (mode - lo)) : hi - Math.sqrt((1 - u) * (hi - lo) * (hi - mode))
}

function quantiles(values: number[]): Quantiles {
  const s = [...values].sort((a, b) => a - b)
  const q = (p: number) => s[Math.min(s.length - 1, Math.max(0, Math.round(p * (s.length - 1))))]
  return { mean: s.reduce((a, b) => a + b, 0) / Math.max(1, s.length), p05: q(0.05), p50: q(0.5), p95: q(0.95) }
}

// ── travel time ──
export function travelTimeMin(curve: ExposureData['travelTime'], km: number): [number, number] {
  const c = [...curve].sort((a, b) => a[0] - b[0])
  if (km <= c[0][0]) return [c[0][1], c[0][2]]
  for (let i = 1; i < c.length; i++) {
    if (km <= c[i][0]) {
      const f = (km - c[i - 1][0]) / (c[i][0] - c[i - 1][0])
      return [c[i - 1][1] + f * (c[i][1] - c[i - 1][1]), c[i - 1][2] + f * (c[i][2] - c[i - 1][2])]
    }
  }
  const last = c[c.length - 1]
  return [last[1], last[2]]
}

/** Is this group in the flood path at this hour? Nepal's school week is Sunday–Friday. */
export function presenceFactor(p: Presence, hourNpt: number, weekday: number): number {
  const day = hourNpt >= 6 && hourNpt < 21
  if (p === 'always') return 1
  if (p === 'schoolHours') return weekday !== 6 && hourNpt >= 10 && hourNpt < 16 ? 1 : 0
  if (p === 'dayShift') return hourNpt >= 7 && hourNpt < 19 ? 1 : 0.25
  return day ? 1 : 0.2
}

/** P(response delay + walk to safety ≤ x), response ~ Exp(mean tau), walk ~ Uniform(a, b). */
function reachSafetyBy(x: number, tau: number, a: number, b: number): number {
  if (x <= a) return 0
  if (b - a < 1e-9) return 1 - Math.exp(-(x - a) / tau)
  const u = Math.min(b, x)
  const val = (u - a) - tau * (Math.exp(-(x - u) / tau) - Math.exp(-(x - a) / tau))
  return Math.max(0, Math.min(1, val / (b - a)))
}

function channelAvailable(site: ExposureSite, id: ChannelId, plan: WarningPlan): boolean {
  if (id === 'siren') return site.channels.siren || !!plan.addEverywhere?.includes('siren')
  if (id === 'volunteers') return site.channels.volunteers || !!plan.addEverywhere?.includes('volunteers')
  if (id === 'dutyCall') return site.channels.dutyCall
  return true
}

export function runLifeSafety(data: ExposureData, plan: WarningPlan, params: ModelParams, opts: RunOptions): ModelResult {
  const rand = mulberry32(params.seed)
  const n = params.draws
  const totalPar = new Array<number>(n).fill(0)
  const totalDeaths = new Array<number>(n).fill(0)
  let totWarned = 0
  let totSafe = 0
  let totParMean = 0
  const sites: SiteResult[] = []

  for (const site of data.sites) {
    const arrival = opts.arrivalOverride?.[site.id] ?? travelTimeMin(data.travelTime, site.riverKm)
    const decision = plan.decisionMin
    const margin = decision === null ? null : arrival[0] - decision
    // Beyond reach: less time than it takes most people to get the warning (~3 min), react and walk to safety.
    const typicalEscape = (site.minutesToSafety[0] + site.minutesToSafety[1]) / 2 + 3
    const beyondReach = margin !== null && margin < typicalEscape
    const parDraws: number[] = []
    const deathDraws: number[] = []
    let warnedSum = 0
    let safeSum = 0
    let parSum = 0

    for (let d = 0; d < n; d++) {
      const A = arrival[0] + rand() * (arrival[1] - arrival[0])
      const fat = tri(rand, params.fatality[site.severity])
      const comply = tri(rand, params.compliance[plan.message])
      const tau = Math.max(0.1, tri(rand, params.responseMin[plan.message]))
      let par = 0
      let deaths = 0
      let warnedPeople = 0
      let safePeople = 0
      for (const g of site.groups) {
        const people = tri(rand, [g.low, g.best, g.high]) * presenceFactor(g.presence, opts.hourNpt, opts.weekday)
        if (people <= 0) continue
        par += people
        // Channels that reach this group at this site
        const active: Array<{ start: number; reach: number; mean: number }> = []
        if (decision !== null) {
          for (const pc of plan.channels) {
            if (pc.sites && !pc.sites.includes(site.id)) continue
            if (pc.groups && !pc.groups.includes(g.kind)) continue
            if (!channelAvailable(site, pc.id, plan)) continue
            const cp = params.channels[pc.id]
            active.push({ start: decision + pc.offsetMin, reach: tri(rand, cp.reach[g.kind]), mean: Math.max(0.1, tri(rand, cp.delayMin)) })
          }
        }
        // Warned share over time W(t) = 1 − Π(1 − reach·(1 − e^(−(t−start)/mean)))
        const W = (t: number) => {
          let notWarned = 1
          for (const c of active) if (t > c.start) notWarned *= 1 - c.reach * (1 - Math.exp(-(t - c.start) / c.mean))
          return 1 - notWarned
        }
        let warned = 0
        let safe = 0
        if (active.length) {
          const t0 = Math.min(...active.map(c => c.start))
          const horizon = A - t0
          if (horizon > 0) {
            const steps = Math.min(240, Math.max(20, Math.ceil(horizon)))
            const dt = horizon / steps
            let prev = W(t0)
            for (let i = 1; i <= steps; i++) {
              const t = t0 + i * dt
              const cur = W(t)
              safe += (cur - prev) * reachSafetyBy(A - (t - dt / 2), tau, site.minutesToSafety[0], site.minutesToSafety[1])
              prev = cur
            }
            warned = prev
            safe *= comply
          }
        }
        warnedPeople += people * warned
        safePeople += people * safe
        deaths += people * (1 - safe) * fat
      }
      parDraws.push(par)
      deathDraws.push(deaths)
      totalPar[d] += par
      totalDeaths[d] += deaths
      parSum += par
      warnedSum += warnedPeople
      safeSum += safePeople
    }
    const parQ = quantiles(parDraws)
    const deathQ = quantiles(deathDraws)
    totParMean += parSum / n
    totWarned += warnedSum / n
    totSafe += safeSum / n
    sites.push({
      id: site.id,
      name: site.name,
      riverKm: site.riverKm,
      severity: site.severity,
      arrivalMin: [Math.round(arrival[0]), Math.round(arrival[1])],
      warningMarginMin: margin === null ? null : Math.round(margin),
      beyondReach,
      peopleAtRisk: parQ,
      warnedBeforeArrival: parSum > 0 ? warnedSum / parSum : 0,
      reachedSafety: parSum > 0 ? safeSum / parSum : 0,
      deaths: deathQ,
      survivalRate: parQ.mean > 0 ? 1 - deathQ.mean / parQ.mean : 1,
    })
  }

  const parT = quantiles(totalPar)
  const deathT = quantiles(totalDeaths)
  return {
    plan,
    sites,
    totals: {
      peopleAtRisk: parT,
      deaths: deathT,
      warnedBeforeArrival: totParMean > 0 ? totWarned / totParMean : 0,
      reachedSafety: totParMean > 0 ? totSafe / totParMean : 0,
      survivalRate: parT.mean > 0 ? 1 - deathT.mean / parT.mean : 1,
    },
  }
}

/** Expected deaths if the same plan's decision came at each of these minutes after the front passed km 0. */
export function delayCurve(data: ExposureData, plan: WarningPlan, params: ModelParams, opts: RunOptions, decisionMins: number[]) {
  const light = { ...params, draws: Math.min(params.draws, 300) }
  return decisionMins.map(m => ({ decisionMin: m, deaths: runLifeSafety(data, { ...plan, decisionMin: m }, light, opts).totals.deaths }))
}

/**
 * Live mode: when the wave front passed km 0, estimated from the backend's arrival windows
 * (median over sites of earliest arrival minus the earliest travel time to that site).
 */
export function estimateFrontMs(data: ExposureData, bands: Array<{ riverKm: number; earliestArrival: string }>): number | null {
  const vals = bands
    .map(b => Date.parse(b.earliestArrival) - travelTimeMin(data.travelTime, b.riverKm)[0] * 60000)
    .filter(v => Number.isFinite(v))
    .sort((a, b) => a - b)
  return vals.length ? vals[Math.floor(vals.length / 2)] : null
}

/** Live mode: the backend's arrival windows for known sites, as minutes after the front passed km 0. */
export function arrivalOverrideFromBands(
  data: ExposureData,
  bands: Array<{ siteId: string; earliestArrival: string; latestArrival: string }>,
  frontMs: number,
): Record<string, [number, number]> {
  const out: Record<string, [number, number]> = {}
  for (const b of bands) {
    if (!data.sites.some(s => s.id === b.siteId)) continue
    const e = (Date.parse(b.earliestArrival) - frontMs) / 60000
    const l = (Date.parse(b.latestArrival) - frontMs) / 60000
    if (Number.isFinite(e) && Number.isFinite(l)) out[b.siteId] = [e, Math.max(e, l)]
  }
  return out
}

/** Hour (0–23) and weekday (0 = Sunday) in Nepal time for a timestamp. */
export function nptHourAndWeekday(ms: number): { hourNpt: number; weekday: number } {
  const d = new Date(ms + (5 * 60 + 45) * 60000)
  return { hourNpt: d.getUTCHours(), weekday: d.getUTCDay() }
}

// ── Default assumptions (all A unless stated). Shown on screen with their reasons. ──
export const DEFAULT_PARAMS: ModelParams = {
  channels: {
    dutyCall: {
      label: 'Duty-holder actions (JalDrishti)',
      reach: { residents: [0.2, 0.4, 0.6], students: [0.85, 0.95, 0.99], workers: [0.85, 0.95, 0.99], travellers: [0.1, 0.3, 0.5] },
      delayMin: [1, 3, 8],
      note: 'Principals, plant control rooms, ward chairs and traffic police get a site-specific action and move their own people. Students and workers are reached through them; residents only partly (ward loudspeaker); travellers through traffic holds.',
    },
    sms: {
      label: 'Mass SMS (NDRRMA / telecoms)',
      reach: { residents: [0.6, 0.8, 0.9], students: [0.2, 0.4, 0.6], workers: [0.6, 0.8, 0.9], travellers: [0.2, 0.4, 0.6] },
      delayMin: [2, 5, 20],
      note: 'Needs a domestic SIM and signal. On 26 Aug one message took 19 minutes to arrive and visitors with foreign SIMs got nothing.',
    },
    siren: {
      label: 'Sirens / loudspeakers at the site',
      reach: { residents: [0.7, 0.85, 0.95], students: [0.7, 0.85, 0.95], workers: [0.6, 0.8, 0.9], travellers: [0.6, 0.8, 0.9] },
      delayMin: [0.5, 1, 3],
      note: 'Reaches everyone in earshot, including people without phones. None were working in the upper gorge on 26 Aug.',
    },
    volunteers: {
      label: 'Community volunteers (door to door)',
      reach: { residents: [0.4, 0.6, 0.8], students: [0.2, 0.3, 0.5], workers: [0.1, 0.2, 0.4], travellers: [0.1, 0.2, 0.4] },
      delayMin: [5, 15, 30],
      note: 'Slow but trusted; reaches elderly and households without phones.',
    },
    cellBroadcast: {
      label: 'Cell broadcast (all phones in the area)',
      reach: { residents: [0.75, 0.9, 0.97], students: [0.3, 0.5, 0.7], workers: [0.75, 0.9, 0.97], travellers: [0.75, 0.9, 0.97] },
      delayMin: [0.5, 1, 3],
      note: 'Reaches every phone on the local towers, including foreign SIMs. Not in use in Nepal today; shown as a what-if.',
    },
    informalCall: {
      label: 'Informal phone call',
      reach: { residents: [0.05, 0.1, 0.2], students: [0.9, 0.95, 0.99], workers: [0.3, 0.5, 0.7], travellers: [0.0, 0.05, 0.1] },
      delayMin: [0.5, 1, 3],
      note: 'On 26 Aug a call to the Trishuli school head teacher at 09:10 got about 900 students uphill ten minutes before the flood.',
    },
  },
  compliance: { precise: [0.8, 0.9, 0.97], vague: [0.4, 0.6, 0.8] },
  responseMin: { precise: [1, 2, 5], vague: [3, 6, 12] },
  fatality: { high: [0.3, 0.75, 1.0], medium: [0.03, 0.15, 0.35], low: [0.0, 0.01, 0.02] },
  draws: 800,
  seed: 20260826,
}

/** Reference plans. Times are minutes after the wave front passed Rasuwagadhi (08:44 NPT on 26 Aug). */
export const REFERENCE_PLANS: WarningPlan[] = [
  {
    id: 'aug26',
    label: 'As on 26 Aug 2026',
    decisionMin: 31, // first mass SMS 09:15 NPT
    message: 'vague',
    channels: [
      { id: 'sms', offsetMin: 0 },
      { id: 'informalCall', offsetMin: -5, sites: ['BID'], groups: ['students'] }, // 09:10 call to the head teacher
    ],
  },
  {
    id: 'jaldrishti',
    label: 'With JalDrishti',
    decisionMin: 9, // R5 cascade WARNING 08:53 NPT
    message: 'precise',
    channels: [
      { id: 'dutyCall', offsetMin: 0 },
      { id: 'sms', offsetMin: 2 },
      { id: 'siren', offsetMin: 1 },
      { id: 'volunteers', offsetMin: 2 },
    ],
  },
  {
    id: 'upstream',
    label: 'JalDrishti + upstream data from Tibet',
    decisionMin: -15,
    message: 'precise',
    channels: [
      { id: 'dutyCall', offsetMin: 0 },
      { id: 'sms', offsetMin: 2 },
      { id: 'siren', offsetMin: 1 },
      { id: 'volunteers', offsetMin: 2 },
    ],
  },
]
