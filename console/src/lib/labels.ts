// Static, human-readable text. This is the ONLY file allowed to contain fixed domain wording.
// It must never contain incident facts (times, levels, which gauge failed) — those come from the API.
import type { ActionState, ActorType, HazardType, IncidentLevel, StationStateCode, Tag, Tone, Trust, EventType } from '@/types/api'

export const CORRIDOR_ID = 'bhote-trishuli-narayani'

export interface ScenarioInfo {
  id: string
  code: string
  title: string
  proves: string
  variants: Array<'syaphrubesiLastReading' | 'malekhuCrossing' | 'graceMinutes' | 'phoneCallAt0900'>
  hasPublishedRecord: boolean
}

export const SCENARIOS: ScenarioInfo[] = [
  { id: 'aug26-as-happened', code: 'S1', title: '26 Aug 2026 — as it happened', proves: 'Two gauges going silent in river order raise a warning before the 09:15 phone-triggered SMS.', variants: ['syaphrubesiLastReading', 'malekhuCrossing', 'graceMinutes', 'phoneCallAt0900'], hasPublishedRecord: true },
  { id: 'aug26-no-phone-call', code: 'S2', title: '26 Aug 2026 — no phone call', proves: 'The warning no longer depends on someone noticing and calling.', variants: ['syaphrubesiLastReading', 'malekhuCrossing', 'graceMinutes', 'phoneCallAt0900'], hasPublishedRecord: true },
  { id: 'gauge-power-failure', code: 'S3', title: 'Single gauge loses power', proves: 'One silent gauge never becomes a public alert.', variants: ['graceMinutes'], hasPublishedRecord: false },
  { id: 'telemetry-outage', code: 'S4', title: 'Telemetry network outage', proves: 'A carrier failure is not treated as a flood.', variants: ['graceMinutes'], hasPublishedRecord: false },
  { id: 'monsoon-rise', code: 'S5', title: 'Ordinary monsoon rise', proves: 'Rain floods follow the normal height path, with no false non-rain tag.', variants: ['graceMinutes'], hasPublishedRecord: false },
  { id: 'second-wave', code: 'S6', title: 'Second wave under heightened watch', proves: 'A known upstream hazard makes a single silence enough for a warning.', variants: ['graceMinutes'], hasPublishedRecord: false },
  { id: 'sandbox', code: 'S7', title: 'Open sandbox', proves: 'Anything you inject goes through the real rule engine.', variants: ['graceMinutes'], hasPublishedRecord: false },
]

export const VARIANT_OPTIONS = {
  syaphrubesiLastReading: { label: 'Syaphrubesi last reading', options: ['08:40', '08:50'], help: 'Published sources disagree.' },
  malekhuCrossing: { label: 'Malekhu threshold crossed', options: ['11:20', '11:43'], help: 'Published sources disagree.' },
  graceMinutes: { label: 'Silence grace period', options: [3, 10], help: 'Minutes a reading may be late before a gauge counts as silent.' },
  phoneCallAt0900: { label: 'Dhunche phone call at 09:00', options: [true, false], help: 'Turn off to see the warning without the call.' },
} as const

export interface RuleInfo { name: string; fires: string; result: string }

export const RULES: Record<string, RuleInfo> = {
  R1: { name: 'Height', fires: 'A reading reaches the warning level (or the danger level).', result: 'Watch (warning at danger level)' },
  R2: { name: 'Rate of rise', fires: 'The river rises faster than its threshold over 10, 30 or 60 minutes.', result: 'Watch' },
  R3: { name: 'Silence', fires: 'A reading is overdue by more than the grace period.', result: 'Watch, internal only' },
  R4: { name: 'Network outage', fires: 'More than half of one telemetry group goes silent in the same slot.', result: 'Silence ignored; outage ticket' },
  R5: { name: 'Cascade', fires: 'A silent gauge is followed downstream, in river order and at a plausible wave speed, by another silent gauge or a fast rise.', result: 'Warning' },
  R6: { name: 'Rise without rain', fires: 'A fast rise while the catchment has had under 5 mm of rain in 3 hours.', result: 'Warning, tagged non-rain' },
  R7: { name: 'Trusted report', fires: 'An official reports a flood or debris flow and the parse confidence is at least 0.7.', result: 'Warning' },
  R8: { name: 'Observer reports', fires: 'Two independent observer reports within 30 min and 20 km, or one plus gauge evidence.', result: 'Warning' },
  R9: { name: 'Seismic hint', fires: 'An earthquake of magnitude 3.5+ inside a watched headwater area.', result: 'Watch; warning if a gauge goes silent within 30 min' },
  R10: { name: 'Heightened watch', fires: 'The forecaster declares a known upstream hazard.', result: 'Rate thresholds halved; silence of the top gauge raises a warning' },
  R11: { name: 'Auto-cancel', fires: 'The silent gauge returns normal, an observer reports a normal river, or the arrival window passes quietly.', result: 'Closes a watch only' },
  FORECASTER_MANUAL: { name: 'Forecaster decision', fires: 'The duty forecaster changed the level by hand and gave a written reason.', result: 'As decided by the forecaster' },
}

/** Rules that raise WARNING directly when they open an incident. */
export const DIRECT_WARNING_RULES = new Set(['R5', 'R6', 'R7', 'R8', 'R10'])

/** Short label for an incident event in timelines and the evidence trail. */
export function eventLabel(type: EventType, ruleId: string | null): string {
  if (type === 'OPENED') return ruleId && DIRECT_WARNING_RULES.has(ruleId) ? 'Warning raised' : 'Watch opened'
  if (type === 'LEVEL_CHANGED') return 'Raised to warning' // the backend only emits this for WATCH → WARNING
  return EVENT_TYPES[type]
}

export function ruleLabel(ruleId: string | null | undefined): string {
  if (!ruleId) return 'Rule engine'
  const r = RULES[ruleId]
  return r ? `${ruleId} · ${r.name}` : ruleId
}

export const LEVELS: Record<IncidentLevel, { label: string; headline: string; detail: string; tone: Tone }> = {
  NORMAL: { label: 'Normal', headline: 'Corridor normal', detail: 'No open incident.', tone: 'ok' },
  WATCH: { label: 'Watch', headline: 'Watch — possible sudden flood, not yet corroborated', detail: 'Internal only. The public is not alerted at this level.', tone: 'watch' },
  WARNING: { label: 'Warning', headline: 'Warning — flood wave corroborated. Act now.', detail: 'Duty-holders downstream have been sent actions.', tone: 'danger' },
  STAND_DOWN: { label: 'Stood down', headline: 'Incident stood down', detail: 'Everyone who was alerted has been told to stand down.', tone: 'info' },
}

export const STATION_STATES: Record<StationStateCode, { label: string; tone: Tone }> = {
  OK: { label: 'Normal', tone: 'ok' },
  AMBER: { label: 'Above warning level', tone: 'watch' },
  RED: { label: 'Above danger level', tone: 'danger' },
  RATE_ALERT: { label: 'Rising fast', tone: 'watch' },
  SILENT: { label: 'No data', tone: 'watch' },
  TELEMETRY_OUTAGE: { label: 'Network outage', tone: 'outage' },
}

export const EVENT_TYPES: Record<EventType, string> = {
  OPENED: 'Incident opened',
  LEVEL_CHANGED: 'Level changed',
  EVIDENCE_ADDED: 'Evidence added',
  BANDS_UPDATED: 'Arrival windows updated',
  STOOD_DOWN: 'Stood down',
}

export const ACTION_STATES: Record<ActionState, { label: string; tone: Tone; needsAttention: boolean }> = {
  SENT: { label: 'Sent', tone: 'info', needsAttention: true },
  DELIVERED: { label: 'Delivered', tone: 'info', needsAttention: true },
  ACKED: { label: 'Confirmed', tone: 'ok', needsAttention: false },
  DECLINED: { label: 'Declined', tone: 'danger', needsAttention: true },
  ESCALATED_1: { label: 'Escalating · voice call', tone: 'watch', needsAttention: true },
  ESCALATED_2: { label: 'Escalating · deputy', tone: 'watch', needsAttention: true },
  ESCALATED_3: { label: 'Escalating · CDO', tone: 'danger', needsAttention: true },
  UNRESOLVED: { label: 'Unresolved', tone: 'danger', needsAttention: true },
}

export const ACTION_DESCRIPTIONS: Record<string, string> = {
  STANDBY_CREW: 'Put the intake-closure crew on standby and test the siren',
  ALERT_VOLUNTEERS: 'Alert volunteers and check the evacuation route',
  INSPECT_RIVER: 'Look at the river and send a field report',
  BROADCAST_CAP: 'Broadcast the alert to the affected wards',
  CLOSE_INTAKE: 'Close the headworks intake gates and stop river work',
  EVACUATE_CREWS: 'Move river-level and tunnel crews to the assembly point',
  HOLD_TRAFFIC: 'Stop vehicles entering the corridor and close the bridge',
  EVACUATE_RIVERSIDE: 'Move riverside households to the evacuation point',
  MOVE_STUDENTS: 'Move students to the assembly point and confirm the headcount',
  CONVENE_COMMITTEE: 'Convene the district disaster committee',
  STAND_DOWN: 'Stand down and report any damage you see',
}

export const ACTION_CONFIRM_LABELS: Record<string, string> = {
  STANDBY_CREW: 'Confirm: crew on standby',
  ALERT_VOLUNTEERS: 'Confirm: volunteers alerted',
  INSPECT_RIVER: 'Confirm: river checked',
  BROADCAST_CAP: 'Confirm: alert broadcast',
  CLOSE_INTAKE: 'Confirm: gates closed',
  EVACUATE_CREWS: 'Confirm: crews moved',
  HOLD_TRAFFIC: 'Confirm: traffic stopped',
  EVACUATE_RIVERSIDE: 'Confirm: evacuation started',
  MOVE_STUDENTS: 'Confirm: students moved',
  CONVENE_COMMITTEE: 'Confirm: committee convened',
  STAND_DOWN: 'Confirm: stood down',
}

export function describeAction(code: string): string {
  return ACTION_DESCRIPTIONS[code] ?? humanizeKey(code)
}

export const ACTOR_TYPES: Record<ActorType, string> = {
  HYDRO_CONTROL: 'Hydropower control room',
  HYDRO_SAFETY: 'Hydropower safety officer',
  WARD_CHAIR: 'Ward chair',
  CDO: 'Chief District Officer',
  TRAFFIC_POLICE: 'Highway traffic police',
  SCHOOL_PRINCIPAL: 'School principal',
  NDRRMA: 'NDRRMA duty officer',
  GAUGE_READER: 'Gauge reader',
}

export const TRUST: Record<Trust, { label: string; effect: string; tone: Tone }> = {
  OFFICIAL: { label: 'Official', effect: 'One credible flood report from an official raises a warning.', tone: 'info' },
  OBSERVER: { label: 'Observer', effect: 'One report raises a watch; two, or one plus gauge evidence, raise a warning.', tone: 'neutral' },
  PUBLIC: { label: 'Public', effect: 'Shown here, but only counts as support for other evidence.', tone: 'outage' },
}

export const HAZARDS: Record<HazardType, { label: string; tone: Tone }> = {
  DEBRIS_FLOOD: { label: 'Debris flood', tone: 'danger' },
  PEOPLE_TRAPPED: { label: 'People trapped', tone: 'danger' },
  BRIDGE_DAMAGE: { label: 'Bridge damage', tone: 'danger' },
  RAPID_RISE: { label: 'Rapid rise', tone: 'watch' },
  LANDSLIDE: { label: 'Landslide', tone: 'watch' },
  NO_HAZARD: { label: 'No hazard', tone: 'ok' },
  UNCLEAR: { label: 'Unclear', tone: 'neutral' },
}

export const EVIDENCE: Record<string, string> = {
  ROARING_SOUND: 'Roaring sound',
  MUDDY_WATER: 'Muddy water',
  BOULDERS: 'Boulders',
  FAST_RISE: 'Fast rise',
  BRIDGE_GONE: 'Bridge gone',
  HOUSES_HIT: 'Houses hit',
  PEOPLE_FLEEING: 'People fleeing',
}

export const PROVENANCE: Record<Tag, { label: string; meaning: string }> = {
  P: { label: 'Published', meaning: 'Taken from a published source about the real event.' },
  D: { label: 'Derived', meaning: 'Calculated by JalDrishti from published or simulated inputs.' },
  A: { label: 'Assumed', meaning: 'A stated assumption that should be checked with DHM.' },
}

export const SAMPLE_REPORTS = [
  { label: 'Nepali · official · flood', role: 'OFFICIAL' as Trust, text: 'म स्याफ्रुबेसीबाट बोल्दैछु। भोटेकोसीमा ठूलो गेग्रान सहितको बाढी आयो, पुल बग्यो, मानिसहरू माथि भाग्दैछन्।' },
  { label: 'Hindi · observer · rising', role: 'OBSERVER' as Trust, text: 'गल्छी के पास त्रिशूली नदी बहुत तेज़ी से बढ़ रही है, पानी मटमैला है और पत्थरों की आवाज़ आ रही है।' },
  { label: 'Nepali · official · calm', role: 'OFFICIAL' as Trust, text: 'नदी सामान्य छ, पानीको बहाव शान्त छ।' },
]

/** "lastGoodAt" → "Last good at", "D_ij" → "D ij" */
export function humanizeKey(key: string): string {
  const spaced = key
    .replace(/_/g, ' ')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .trim()
    .toLowerCase()
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}
