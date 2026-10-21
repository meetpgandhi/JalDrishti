// Every time in the UI is Nepal time (NPT, UTC+05:45), whatever the viewer's own time zone.
// Every "late", "in 6 min" or "15 min ago" is measured against the SIMULATION clock, never Date.now().

const NPT_ZONE = 'Asia/Kathmandu'

const fmtHm = new Intl.DateTimeFormat('en-GB', { timeZone: NPT_ZONE, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
const fmtHms = new Intl.DateTimeFormat('en-GB', { timeZone: NPT_ZONE, hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' })
const fmtDate = new Intl.DateTimeFormat('en-GB', { timeZone: NPT_ZONE, day: 'numeric', month: 'short', year: 'numeric' })

export function toMs(iso: string | null | undefined): number | null {
  if (!iso) return null
  const ms = Date.parse(iso)
  return Number.isNaN(ms) ? null : ms
}

/** "08:53" (or "08:53:07" with seconds). Returns "—" for missing values. */
export function formatNpt(iso: string | null | undefined, seconds = false): string {
  const ms = toMs(iso)
  if (ms === null) return '—'
  return (seconds ? fmtHms : fmtHm).format(ms)
}

/** "26 Aug 2026" */
export function formatNptDate(iso: string | null | undefined): string {
  const ms = toMs(iso)
  return ms === null ? '—' : fmtDate.format(ms)
}

/** "09:16–09:36" */
export function formatNptRange(fromIso: string | null | undefined, toIso: string | null | undefined): string {
  return `${formatNpt(fromIso)}–${formatNpt(toIso)}`
}

/** Whole minutes from `fromIso` to `toIso` (negative if `toIso` is earlier). */
export function minutesBetween(fromIso: string | null | undefined, toIso: string | null | undefined): number | null {
  const a = toMs(fromIso)
  const b = toMs(toIso)
  if (a === null || b === null) return null
  return Math.round((b - a) / 60000)
}

/** "in 6 min", "8 min late", "now" — target compared with the sim clock. */
export function untilLabel(targetIso: string | null | undefined, simNowIso: string | null | undefined): string {
  const m = minutesBetween(simNowIso, targetIso)
  if (m === null) return ''
  if (m === 0) return 'now'
  return m > 0 ? `in ${formatDuration(m)}` : `${formatDuration(-m)} late`
}

/** "2 min ago", "1 h 05 min ago" — how long before the sim clock something happened. */
export function agoLabel(pastIso: string | null | undefined, simNowIso: string | null | undefined): string {
  const m = minutesBetween(pastIso, simNowIso)
  if (m === null) return ''
  if (m <= 0) return 'just now'
  return `${formatDuration(m)} ago`
}

/** True when the target time is before the sim clock. False if either is missing. */
export function isPastSim(targetIso: string | null | undefined, simNowIso: string | null | undefined): boolean {
  const m = minutesBetween(simNowIso, targetIso)
  return m !== null && m < 0
}

/** 7 → "7 min", 65 → "1 h 05 min" */
export function formatDuration(totalMinutes: number): string {
  const m = Math.abs(Math.round(totalMinutes))
  if (m < 60) return `${m} min`
  const h = Math.floor(m / 60)
  const rest = m % 60
  return rest === 0 ? `${h} h` : `${h} h ${String(rest).padStart(2, '0')} min`
}
