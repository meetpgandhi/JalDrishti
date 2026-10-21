// Small, dependency-free geometry for the corridor map. Coordinates are [lng, lat] (GeoJSON order).
// Distances are in kilometres along the drawn river line ("line km"). The rule engine uses its own
// modelled "river km"; riverKmToLineKm() maps one onto the other using the gauges as control points.

export type LngLat = [number, number]

const R = 6371.0088 // mean Earth radius, km
const rad = (d: number) => (d * Math.PI) / 180

export function haversineKm(a: LngLat, b: LngLat): number {
  const dLat = rad(b[1] - a[1])
  const dLng = rad(b[0] - a[0])
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a[1])) * Math.cos(rad(b[1])) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)))
}

/** Cumulative distance (km) at each vertex; first entry is 0. */
export function cumulativeKm(line: LngLat[]): number[] {
  const out = [0]
  for (let i = 1; i < line.length; i++) out.push(out[i - 1] + haversineKm(line[i - 1], line[i]))
  return out
}

function lerp(a: LngLat, b: LngLat, f: number): LngLat {
  return [a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f]
}

/** Point at `km` along the line (clamped to the line). */
export function pointAlong(line: LngLat[], cum: number[], km: number): LngLat {
  if (line.length === 0) return [0, 0]
  const total = cum[cum.length - 1]
  const k = Math.max(0, Math.min(total, km))
  for (let i = 1; i < line.length; i++) {
    if (cum[i] >= k) {
      const seg = cum[i] - cum[i - 1]
      return seg === 0 ? line[i] : lerp(line[i - 1], line[i], (k - cum[i - 1]) / seg)
    }
  }
  return line[line.length - 1]
}

/** The part of the line between two along-line distances (km). Empty if the range is empty. */
export function sliceAlong(line: LngLat[], cum: number[], startKm: number, endKm: number): LngLat[] {
  if (line.length < 2) return []
  const total = cum[cum.length - 1]
  const a = Math.max(0, Math.min(total, Math.min(startKm, endKm)))
  const b = Math.max(0, Math.min(total, Math.max(startKm, endKm)))
  if (b - a < 1e-6) return []
  const out: LngLat[] = [pointAlong(line, cum, a)]
  for (let i = 1; i < line.length - 1; i++) if (cum[i] > a && cum[i] < b) out.push(line[i])
  out.push(pointAlong(line, cum, b))
  return out
}

/** Nearest point on the line to `p`, its along-line distance and how far `p` is from the line. */
export function snapToLine(line: LngLat[], cum: number[], p: LngLat): { km: number; point: LngLat; offsetKm: number } {
  let best = { km: 0, point: line[0], offsetKm: Infinity }
  const kx = Math.cos(rad(p[1])) // local equirectangular scale, accurate enough over a few km
  for (let i = 1; i < line.length; i++) {
    const a = line[i - 1]
    const b = line[i]
    const dx = (b[0] - a[0]) * kx
    const dy = b[1] - a[1]
    const len2 = dx * dx + dy * dy
    let f = len2 === 0 ? 0 : (((p[0] - a[0]) * kx) * dx + (p[1] - a[1]) * dy) / len2
    f = Math.max(0, Math.min(1, f))
    const q = lerp(a, b, f)
    const d = haversineKm(p, q)
    if (d < best.offsetKm) best = { km: cum[i - 1] + (cum[i] - cum[i - 1]) * f, point: q, offsetKm: d }
  }
  return best
}

export interface Control { riverKm: number; lineKm: number }

/**
 * Map a modelled river km onto the drawn line using control points (gauges).
 * Piecewise linear between controls; beyond the ends it continues at slope 1. Clamped to the line.
 */
export function riverKmToLineKm(controls: Control[], riverKm: number, totalLineKm: number): number {
  const c = [...controls].sort((x, y) => x.riverKm - y.riverKm)
  let km: number
  if (c.length === 0) km = riverKm
  else if (riverKm <= c[0].riverKm) km = c[0].lineKm - (c[0].riverKm - riverKm)
  else if (riverKm >= c[c.length - 1].riverKm) km = c[c.length - 1].lineKm + (riverKm - c[c.length - 1].riverKm)
  else {
    km = riverKm
    for (let i = 1; i < c.length; i++) {
      if (riverKm <= c[i].riverKm) {
        const span = c[i].riverKm - c[i - 1].riverKm
        const f = span === 0 ? 0 : (riverKm - c[i - 1].riverKm) / span
        km = c[i - 1].lineKm + f * (c[i].lineKm - c[i - 1].lineKm)
        break
      }
    }
  }
  return Math.max(0, Math.min(totalLineKm, km))
}

export interface BandLike { riverKm: number; earliestArrival: string; latestArrival: string }

/**
 * How far down the river (modelled km) the wave can be at `nowMs`, according to the arrival windows.
 * key 'latestArrival'   → the wave has certainly passed everything above this km.
 * key 'earliestArrival' → the wave may already have reached this km.
 * Returns null when `nowMs` is before the first window (the front is still above the first site).
 */
export function frontRiverKm(bands: BandLike[], nowMs: number, key: 'earliestArrival' | 'latestArrival'): number | null {
  const pts = bands
    .map(b => ({ km: b.riverKm, t: Date.parse(b[key]) }))
    .filter(p => Number.isFinite(p.t))
    .sort((a, b) => a.km - b.km)
  if (pts.length === 0 || nowMs < pts[0].t) return null
  for (let i = 1; i < pts.length; i++) pts[i].t = Math.max(pts[i].t, pts[i - 1].t) // windows never move upstream
  for (let i = 1; i < pts.length; i++) {
    if (nowMs < pts[i].t) {
      const span = pts[i].t - pts[i - 1].t
      const f = span === 0 ? 1 : (nowMs - pts[i - 1].t) / span
      return pts[i - 1].km + f * (pts[i].km - pts[i - 1].km)
    }
  }
  return pts[pts.length - 1].km
}
