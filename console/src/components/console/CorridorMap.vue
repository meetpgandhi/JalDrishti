<template>
  <div class="corridor-map">
  <div class="map-wrap">
    <div ref="container" class="map" role="region" aria-label="Corridor map" />

    <div class="tools">
      <button type="button" class="tool" :aria-pressed="terrainOn" @click="toggleTerrain">Terrain</button>
      <button type="button" class="tool" @click="fitCorridor">Fit corridor</button>
    </div>

    <div class="legend" aria-label="Map legend">
      <div class="legend-row"><span class="swatch line reached" />Wave has passed</div>
      <div class="legend-row"><span class="swatch line possible" />Wave may be here</div>
      <div class="legend-row"><span class="swatch line river" />River</div>
      <div class="legend-row"><span class="swatch dot ok" />Gauge reporting</div>
      <div class="legend-row"><span class="swatch dot silent" />Gauge silent</div>
      <div class="legend-note">Wave position is derived from the arrival windows (D).</div>
    </div>

    <div v-if="fatal" class="fatal" role="alert">
      <strong>The map cannot be shown in this browser.</strong>
      <span>{{ fatal }}</span>
    </div>
  </div>
  <ul v-if="notes.length" class="notes">
    <li v-for="n in notes" :key="n">{{ n }}</li>
  </ul>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import {
  Map as MlMap, Marker, Popup, NavigationControl, ScaleControl, AttributionControl, setWorkerUrl,
  type StyleSpecification, type GeoJSONSource, type LngLatBoundsLike,
} from 'maplibre-gl'
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url'
import 'maplibre-gl/dist/maplibre-gl.css'
import { useConsoleStore } from '@/stores/console'
import { cumulativeKm, frontRiverKm, pointAlong, riverKmToLineKm, sliceAlong, snapToLine, type Control, type LngLat } from '@/lib/geo'
import {
  CORRIDOR_BOUNDS, GAUGE_LOCATIONS, MAP_STYLE_URL, RIVER_ATTRIBUTION, RIVER_GEOJSON_URL,
  TERRAIN_ATTRIBUTION, TERRAIN_TILES_URL,
} from '@/lib/corridorGeo'
import { STATION_STATES, PROVENANCE } from '@/lib/labels'
import { agoLabel, formatNpt, formatNptRange, minutesBetween, toMs } from '@/lib/time'
import type { StationTile } from '@/types/api'

setWorkerUrl(workerUrl)

const store = useConsoleStore()
const container = ref<HTMLDivElement | null>(null)
const map = shallowRef<MlMap | null>(null)
const ready = ref(false)
const terrainOn = ref(true)
const fatal = ref<string | null>(null)
const basemapOffline = ref(false)
const riverFromOsm = ref(false)

const notes = computed(() => {
  const n: string[] = []
  if (basemapOffline.value) n.push('Basemap unavailable (offline?). River, gauges and the wave are still live.')
  if (!riverFromOsm.value && ready.value) n.push('Schematic river line: straight segments between gauges. Run scripts/build_corridor_river.py for the real channel.')
  return n
})

// Colours come from the design tokens, read once at runtime, so no hex values live in this file.
const css = (name: string) => getComputedStyle(document.documentElement).getPropertyValue(name).trim()

// River geometry (line km) and the gauges used to calibrate river km → line km
let line: LngLat[] = []
let cum: number[] = [0]
const gaugeMarkers = new Map<string, Marker>()
const arrivalMarkers = new Map<string, Marker>()

function gaugeLngLat(id: string): { lngLat: LngLat; tag: 'P' | 'D' | 'A'; source: string } | null {
  const api = store.corridor?.stations.find(s => s.id === id)
  if (api && (api.lat !== 0 || api.lon !== 0)) return { lngLat: [api.lon, api.lat], tag: 'P', source: 'Corridor configuration' }
  const f = GAUGE_LOCATIONS.find(g => g.id === id)
  return f ? { lngLat: f.lngLat, tag: f.tag, source: f.source } : null
}

const controls = computed<Control[]>(() => {
  if (line.length < 2) return []
  const list = store.stations.length ? store.stations.map(s => ({ id: s.stationId, riverKm: s.riverKm }))
    : (store.corridor?.stations ?? []).map(s => ({ id: s.id, riverKm: s.riverKm }))
  return list.flatMap(s => {
    const g = gaugeLngLat(s.id)
    return g ? [{ riverKm: s.riverKm, lineKm: snapToLine(line, cum, g.lngLat).km }] : []
  })
})

async function loadStyle(): Promise<StyleSpecification> {
  const fallback: StyleSpecification = {
    version: 8,
    sources: {},
    layers: [{ id: 'background', type: 'background', paint: { 'background-color': css('--surface-2') } }],
  }
  try {
    const ctrl = new AbortController()
    const timer = window.setTimeout(() => ctrl.abort(), 5000)
    const res = await fetch(MAP_STYLE_URL, { signal: ctrl.signal })
    window.clearTimeout(timer)
    if (!res.ok) throw new Error(String(res.status))
    return (await res.json()) as StyleSpecification
  } catch {
    basemapOffline.value = true
    return fallback
  }
}

async function loadRiver(): Promise<void> {
  try {
    const res = await fetch(RIVER_GEOJSON_URL)
    if (!res.ok) throw new Error(String(res.status))
    const fc = (await res.json()) as GeoJSON.FeatureCollection
    const f = fc.features.find(x => x.geometry?.type === 'LineString')
    if (!f || f.geometry.type !== 'LineString') throw new Error('no LineString')
    line = f.geometry.coordinates.map(c => [c[0], c[1]] as LngLat)
    riverFromOsm.value = true
  } catch {
    // Schematic fallback: straight segments between the gauges, upstream to downstream.
    line = GAUGE_LOCATIONS.map(g => g.lngLat)
    riverFromOsm.value = false
  }
  cum = cumulativeKm(line)
}

function firstSymbolLayerId(m: MlMap): string | undefined {
  return m.getStyle().layers.find(l => l.type === 'symbol')?.id
}

function addLayers(m: MlMap) {
  const before = firstSymbolLayerId(m)
  m.addSource('terrain-dem', {
    type: 'raster-dem', tiles: [TERRAIN_TILES_URL], tileSize: 256, maxzoom: 15, encoding: 'terrarium',
    attribution: TERRAIN_ATTRIBUTION,
  })
  m.addLayer({
    id: 'hillshade', type: 'hillshade', source: 'terrain-dem',
    paint: {
      'hillshade-exaggeration': 0.35,
      'hillshade-shadow-color': css('--text-2'),
      'hillshade-highlight-color': css('--surface-1'),
      'hillshade-accent-color': css('--text-3'),
    },
  }, before)
  m.addSource('river', {
    type: 'geojson',
    data: { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: line } },
    attribution: riverFromOsm.value ? RIVER_ATTRIBUTION : undefined,
  })
  m.addLayer({ id: 'river-casing', type: 'line', source: 'river', layout: { 'line-cap': 'round', 'line-join': 'round' },
    paint: { 'line-color': css('--surface-1'), 'line-width': 7 } }, before)
  m.addLayer({ id: 'river-line', type: 'line', source: 'river', layout: { 'line-cap': 'round', 'line-join': 'round' },
    paint: { 'line-color': css('--info'), 'line-width': 3, ...(riverFromOsm.value ? {} : { 'line-dasharray': [2, 1.5] }) } }, before)
  m.addSource('flood', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } })
  m.addLayer({ id: 'flood-possible', type: 'line', source: 'flood', filter: ['==', ['get', 'kind'], 'possible'],
    layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': css('--watch-solid'), 'line-width': 8, 'line-opacity': 0.9 } }, before)
  m.addLayer({ id: 'flood-reached', type: 'line', source: 'flood', filter: ['==', ['get', 'kind'], 'reached'],
    layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': css('--danger-solid'), 'line-width': 8 } }, before)
}

// ── Real-time overlays ──
function updateFlood() {
  const m = map.value
  if (!m || !ready.value) return
  const src = m.getSource('flood') as GeoJSONSource | undefined
  if (!src) return
  const now = toMs(store.simNow)
  const open = store.incident && (store.incident.level === 'WATCH' || store.incident.level === 'WARNING')
  const features: GeoJSON.Feature[] = []
  if (open && now !== null && store.bands.length && line.length > 1) {
    const total = cum[cum.length - 1]
    const toLine = (riverKm: number) => riverKmToLineKm(controls.value, riverKm, total)
    const reachedKm = frontRiverKm(store.bands, now, 'latestArrival')
    const possibleKm = frontRiverKm(store.bands, now, 'earliestArrival')
    const reachedLine = reachedKm === null ? 0 : toLine(reachedKm)
    const possibleLine = possibleKm === null ? 0 : toLine(possibleKm)
    const reached = sliceAlong(line, cum, 0, reachedLine)
    const possible = sliceAlong(line, cum, reachedLine, possibleLine)
    if (reached.length > 1) features.push({ type: 'Feature', properties: { kind: 'reached' }, geometry: { type: 'LineString', coordinates: reached } })
    if (possible.length > 1) features.push({ type: 'Feature', properties: { kind: 'possible' }, geometry: { type: 'LineString', coordinates: possible } })
  }
  src.setData({ type: 'FeatureCollection', features })
}

function gaugeKind(s: StationTile | undefined): 'ok' | 'amber' | 'red' | 'silent' | 'outage' | 'none' {
  if (!s) return 'none'
  if (s.state === 'TELEMETRY_OUTAGE') return 'outage'
  if (s.silent || s.state === 'SILENT') return 'silent'
  if (s.state === 'RED') return 'red'
  if (s.state === 'AMBER') return 'amber'
  return 'ok'
}

function esc(text: string): string {
  return text.replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch] ?? ch)
}

function gaugeLabel(s: StationTile | undefined, kind: ReturnType<typeof gaugeKind>): string {
  if (!s) return ''
  if (kind === 'silent') return 'No data'
  if (kind === 'outage') return 'Outage'
  return s.lastLevelM !== null ? `${s.lastLevelM.toFixed(2)} m` : '—'
}

function gaugePopupHtml(id: string, s: StationTile | undefined): string {
  const loc = gaugeLngLat(id)
  const name = s?.name ?? store.corridor?.stations.find(x => x.id === id)?.name ?? id
  const rows: string[] = []
  if (s) {
    const state = STATION_STATES[s.state]?.label ?? s.state
    rows.push(`<div><b>Status</b> ${esc(s.silent ? 'No data' : state)}</div>`)
    if (s.lastLevelM !== null) rows.push(`<div><b>Last reading</b> ${s.lastLevelM.toFixed(2)} m at ${formatNpt(s.lastObservedAt)} NPT (${esc(agoLabel(s.lastObservedAt, store.simNow))})</div>`)
    rows.push(`<div><b>Thresholds</b> warn ${s.warningM ?? '—'} m · danger ${s.dangerM ?? '—'} m</div>`)
  }
  if (loc) rows.push(`<div class="src">Location ${loc.tag} (${esc(PROVENANCE[loc.tag].label)}): ${esc(loc.source)}</div>`)
  return `<div class="jd-popup"><h4>${esc(id)} · ${esc(name)}</h4>${rows.join('')}</div>`
}

function updateGaugeMarkers() {
  const m = map.value
  if (!m || !ready.value) return
  const ids = store.stations.length ? store.stations.map(s => s.stationId) : (store.corridor?.stations.map(s => s.id) ?? GAUGE_LOCATIONS.map(g => g.id))
  for (const id of ids) {
    const loc = gaugeLngLat(id)
    if (!loc) continue
    const s = store.stations.find(x => x.stationId === id)
    const kind = gaugeKind(s)
    let marker = gaugeMarkers.get(id)
    if (!marker) {
      const el = document.createElement('button')
      el.type = 'button'
      el.className = 'jd-gauge'
      marker = new Marker({ element: el, anchor: 'right', offset: [-6, 0] }).setLngLat(loc.lngLat).addTo(m)
      marker.setPopup(new Popup({ offset: 18, closeButton: true, maxWidth: '18rem' }))
      gaugeMarkers.set(id, marker)
    }
    const el = marker.getElement()
    el.dataset.kind = kind
    const value = gaugeLabel(s, kind)
    el.innerHTML = `<span class="jd-dot"></span><span class="jd-code">${esc(id)}</span>${value ? `<span class="jd-val">${esc(value)}</span>` : ''}`
    el.setAttribute('aria-label', `${id}: ${value || 'no run'}`)
    marker.getPopup()?.setHTML(gaugePopupHtml(id, s))
  }
}

function updateArrivalMarkers() {
  const m = map.value
  if (!m || !ready.value) return
  const open = store.incident && (store.incident.level === 'WATCH' || store.incident.level === 'WARNING')
  const wanted = new Set(open && line.length > 1 ? store.bands.map(b => b.siteId) : [])
  for (const [id, mk] of arrivalMarkers) if (!wanted.has(id)) { mk.remove(); arrivalMarkers.delete(id) }
  if (!wanted.size) return
  const total = cum[cum.length - 1]
  // Label only what matters now: the next two sites the wave will reach. Others are dots (hover for details).
  const labelled = new Set(
    [...store.bands]
      .filter(b => (minutesBetween(store.simNow, b.latestArrival) ?? -1) > 0)
      .sort((a, b) => (toMs(a.earliestArrival) ?? 0) - (toMs(b.earliestArrival) ?? 0))
      .slice(0, 2)
      .map(b => b.siteId),
  )
  for (const b of store.bands) {
    const at = pointAlong(line, cum, riverKmToLineKm(controls.value, b.riverKm, total))
    const toEarliest = minutesBetween(store.simNow, b.earliestArrival)
    const toLatest = minutesBetween(store.simNow, b.latestArrival)
    let state = 'upcoming'
    let text = toEarliest !== null && toLatest !== null ? `in ${toEarliest}–${toLatest} min` : formatNptRange(b.earliestArrival, b.latestArrival)
    if (toLatest !== null && toLatest <= 0) { state = 'reached'; text = `Reached · ${formatNptRange(b.earliestArrival, b.latestArrival)}` }
    else if (toEarliest !== null && toEarliest <= 0) { state = 'arriving'; text = `Arriving now · until ${formatNpt(b.latestArrival)}` }
    let marker = arrivalMarkers.get(b.siteId)
    if (!marker) {
      const el = document.createElement('div')
      el.className = 'jd-arrival'
      marker = new Marker({ element: el, anchor: 'left', offset: [-6, 0] }).setLngLat(at).addTo(m)
      arrivalMarkers.set(b.siteId, marker)
    }
    marker.setLngLat(at)
    const el = marker.getElement()
    el.dataset.state = state
    el.title = `${b.siteName}: ${text}`
    el.innerHTML = `<span class="jd-adot"></span>` + (labelled.has(b.siteId)
      ? `<span class="jd-alabel"><span class="jd-site">${esc(b.siteName)}</span><span class="jd-when">${esc(text)}</span></span>`
      : '')
  }
}

function refreshOverlays() {
  updateFlood()
  updateGaugeMarkers()
  updateArrivalMarkers()
}

function fitCorridor() {
  const m = map.value
  if (!m || line.length < 2) return
  const lngs = line.map(p => p[0])
  const lats = line.map(p => p[1])
  const bounds: LngLatBoundsLike = [[Math.min(...lngs), Math.min(...lats)], [Math.max(...lngs), Math.max(...lats)]]
  m.fitBounds(bounds, { padding: { top: 48, bottom: 48, left: 64, right: 64 }, duration: 0 })
}

function toggleTerrain() {
  terrainOn.value = !terrainOn.value
  map.value?.setLayoutProperty('hillshade', 'visibility', terrainOn.value ? 'visible' : 'none')
}

onMounted(async () => {
  const [style] = await Promise.all([loadStyle(), loadRiver()])
  if (!container.value) return
  try {
    const m = new MlMap({
      container: container.value,
      style,
      bounds: CORRIDOR_BOUNDS,
      maxBounds: [[CORRIDOR_BOUNDS[0][0] - 1, CORRIDOR_BOUNDS[0][1] - 1], [CORRIDOR_BOUNDS[1][0] + 1, CORRIDOR_BOUNDS[1][1] + 1]],
      minZoom: 7,
      attributionControl: false,
      dragRotate: false,
      pitchWithRotate: false,
    })
    m.addControl(new NavigationControl({ showCompass: false }), 'top-right')
    m.addControl(new ScaleControl({ unit: 'metric' }), 'bottom-right')
    m.addControl(new AttributionControl({ compact: true }), 'bottom-right')
    m.on('error', e => {
      const msg = e.error?.message ?? ''
      if (/webgl/i.test(msg)) fatal.value = 'WebGL 2 is not available. Use the river diagram view instead.'
    })
    m.on('load', () => {
      addLayers(m)
      ready.value = true
      fitCorridor()
      refreshOverlays()
    })
    map.value = m
  } catch (e) {
    fatal.value = e instanceof Error ? e.message : String(e)
  }
})

watch(
  () => [store.simNow, store.bands, store.stations, store.incident?.level, store.corridor] as const,
  () => refreshOverlays(),
  { deep: true },
)

onUnmounted(() => {
  gaugeMarkers.forEach(mk => mk.remove())
  arrivalMarkers.forEach(mk => mk.remove())
  map.value?.remove()
  map.value = null
})
</script>

<style scoped>
.corridor-map { display: grid; gap: var(--sp-2); }
.map-wrap { position: relative; height: 30rem; border-radius: var(--r-sm); overflow: hidden; border: 1px solid var(--border); background: var(--surface-2); }
.map { position: absolute; inset: 0; }
.tools { position: absolute; top: var(--sp-3); left: var(--sp-3); display: flex; gap: var(--sp-2); z-index: 2; }
.tool {
  height: 1.875rem; padding: 0 var(--sp-3); border-radius: var(--r-sm); font-size: var(--fs-sm); font-weight: 500;
  background: var(--surface-1); color: var(--text); border: 1px solid var(--border-strong); box-shadow: var(--shadow-card);
}
.tool[aria-pressed='true'] { background: var(--accent-soft); color: var(--accent); border-color: var(--info-line); }
.legend {
  position: absolute; left: var(--sp-3); top: 3.25rem; z-index: 2; display: grid; gap: 4px;
  padding: var(--sp-2) var(--sp-3); background: var(--surface-1); border: 1px solid var(--border);
  border-radius: var(--r-sm); box-shadow: var(--shadow-card); font-size: var(--fs-xs); color: var(--text-2);
}
.legend-row { display: flex; align-items: center; gap: var(--sp-2); }
.legend-note { color: var(--text-3); max-width: 15rem; }
.swatch { display: inline-block; flex: none; }
.swatch.line { width: 1.25rem; height: 4px; border-radius: var(--r-pill); }
.swatch.reached { background: var(--danger-solid); }
.swatch.possible { background: var(--watch-solid); }
.swatch.river { background: var(--info); }
.swatch.dot { width: 10px; height: 10px; border-radius: var(--r-pill); }
.swatch.ok { background: var(--ok); }
.swatch.silent { background: var(--surface-1); border: 2px dashed var(--watch); }
.notes { list-style: none; display: grid; gap: 2px; font-size: var(--fs-xs); color: var(--text-3); }
.fatal {
  position: absolute; inset: 0; z-index: 3; display: grid; place-content: center; gap: var(--sp-2); text-align: center;
  background: var(--surface-2); color: var(--text-2); font-size: var(--fs-sm); padding: var(--sp-6);
}
.fatal strong { color: var(--text); font-size: var(--fs-md); }

/* Markers live inside MapLibre's own DOM, so they need :deep() */
:deep(.jd-gauge) {
  z-index: 2; display: inline-flex; align-items: center; gap: 6px; height: 1.75rem; padding: 0 var(--sp-2) 0 6px;
  border-radius: var(--r-pill); background: var(--surface-1); border: 2px solid var(--ok); color: var(--text);
  font-size: var(--fs-xs); box-shadow: var(--shadow-card); cursor: pointer; white-space: nowrap;
}
:deep(.jd-gauge .jd-dot) { width: 10px; height: 10px; border-radius: var(--r-pill); background: var(--ok); }
:deep(.jd-gauge .jd-code) { font-family: var(--font-mono); font-weight: 600; }
:deep(.jd-gauge .jd-val) { font-family: var(--font-mono); color: var(--text-2); }
:deep(.jd-gauge[data-kind='none']) { border-color: var(--border-strong); }
:deep(.jd-gauge[data-kind='none'] .jd-dot) { background: var(--text-3); }
:deep(.jd-gauge[data-kind='amber']) { border-color: var(--watch-solid); }
:deep(.jd-gauge[data-kind='amber'] .jd-dot) { background: var(--watch-solid); }
:deep(.jd-gauge[data-kind='red']) { border-color: var(--danger-solid); }
:deep(.jd-gauge[data-kind='red'] .jd-dot) { background: var(--danger-solid); }
:deep(.jd-gauge[data-kind='silent']) { border-style: dashed; border-color: var(--watch); background: var(--watch-soft); }
:deep(.jd-gauge[data-kind='silent'] .jd-dot) { background: var(--surface-1); border: 2px dashed var(--watch); }
:deep(.jd-gauge[data-kind='silent'] .jd-val) { color: var(--watch); font-family: var(--font-ui); font-weight: 600; }
:deep(.jd-gauge[data-kind='outage']) { border-color: var(--outage); }
:deep(.jd-gauge[data-kind='outage'] .jd-dot) { background: var(--outage); }

:deep(.jd-arrival) { z-index: 1; display: flex; align-items: center; gap: 6px; font-size: var(--fs-xs); white-space: nowrap; }
:deep(.jd-arrival .jd-adot) {
  width: 12px; height: 12px; border-radius: 3px; background: var(--surface-1); border: 2px solid var(--text-3);
  transform: rotate(45deg); flex: none;
}
:deep(.jd-arrival .jd-alabel) {
  display: grid; gap: 1px; padding: 3px var(--sp-2); border-radius: var(--r-sm); background: var(--surface-1);
  border: 1px solid var(--border-strong); box-shadow: var(--shadow-card);
}
:deep(.jd-arrival[data-state='arriving'] .jd-adot) { border-color: var(--watch-solid); background: var(--watch-soft); }
:deep(.jd-arrival[data-state='reached'] .jd-adot) { border-color: var(--danger-solid); background: var(--danger-solid); }
:deep(.jd-arrival .jd-site) { font-weight: 600; color: var(--text); }
:deep(.jd-arrival .jd-when) { font-family: var(--font-mono); color: var(--text-2); }
:deep(.jd-arrival[data-state='arriving'] .jd-alabel) { border-color: var(--watch-solid); background: var(--watch-soft); }
:deep(.jd-arrival[data-state='arriving'] .jd-when) { color: var(--watch); }
:deep(.jd-arrival[data-state='reached'] .jd-alabel) { border-color: var(--danger-line); background: var(--danger-soft); }
:deep(.jd-arrival[data-state='reached'] .jd-when) { color: var(--danger); }

:deep(.jd-popup) { display: grid; gap: 4px; font-family: var(--font-ui); font-size: var(--fs-sm); color: var(--text); }
:deep(.jd-popup h4) { font-size: var(--fs-md); font-weight: 600; margin-bottom: 2px; }
:deep(.jd-popup b) { color: var(--text-3); font-weight: 600; margin-right: 4px; }
:deep(.jd-popup .src) { color: var(--text-3); font-size: var(--fs-xs); }
</style>
