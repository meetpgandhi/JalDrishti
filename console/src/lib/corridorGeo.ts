import type { LngLat } from '@/lib/geo'
import type { Tag } from '@/types/api'

// Gauge locations used by the corridor map when the backend does not supply them
// (GET /api/v1/corridors/{id} currently returns lat/lon = 0 because the station table has no coordinates).
// P = taken from the Department of Hydrology and Meteorology (DHM) station list or station page.
// A = approximate settlement location; the gauge is snapped onto the river line by the map.
export interface GaugeLocation { id: string; lngLat: LngLat; tag: Tag; source: string }

export const GAUGE_LOCATIONS: GaugeLocation[] = [
  { id: 'RSW', lngLat: [85.377649, 28.271297], tag: 'P', source: 'DHM station 446.22, Bhotekoshi at Rasuwagadi' },
  { id: 'SYP', lngLat: [85.34278, 28.16594], tag: 'P', source: 'DHM station 446.25, Bhote Koshi at Shyaprubesi' },
  { id: 'BTW', lngLat: [85.18295, 27.97009], tag: 'P', source: 'DHM station 447, Trisuli River at Betrawati' },
  { id: 'GLC', lngLat: [85.00029, 27.79591], tag: 'A', source: 'Galchhi Rural Municipality (approximate, not a DHM gauge location)' },
  { id: 'MLK', lngLat: [84.83452, 27.8097], tag: 'A', source: 'Malekhu town (approximate, not a DHM gauge location)' },
  { id: 'DVG', lngLat: [84.42762, 27.72548], tag: 'P', source: 'DHM station 450, Narayani River at Devghat' },
]

/** Generous box around the corridor, used to limit panning. [[west, south], [east, north]] */
export const CORRIDOR_BOUNDS: [LngLat, LngLat] = [[84.15, 27.55], [85.65, 28.45]]

/** River line produced by scripts/build_corridor_river.py (OpenStreetMap). Optional. */
export const RIVER_GEOJSON_URL = '/geo/corridor-river.geojson'

/** Basemap style. OpenFreeMap: free, no key, OpenStreetMap data. Override with VITE_MAP_STYLE_URL. */
export const MAP_STYLE_URL: string = import.meta.env.VITE_MAP_STYLE_URL ?? 'https://tiles.openfreemap.org/styles/positron'

/** Terrain (hillshade) tiles: AWS Open Data "Terrain Tiles", Terrarium encoding, no key, no SLA. */
export const TERRAIN_TILES_URL: string =
  import.meta.env.VITE_TERRAIN_TILES_URL ?? 'https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png'

export const TERRAIN_ATTRIBUTION = 'Terrain: <a href="https://registry.opendata.aws/terrain-tiles/" target="_blank" rel="noopener">AWS Terrain Tiles</a> (SRTM courtesy of USGS)'
export const RIVER_ATTRIBUTION = 'River line © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors'
