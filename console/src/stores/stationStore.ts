import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface StationTile {
  stationId: string
  name: string
  river: string
  riverKm: number
  warningM?: number | null
  dangerM?: number | null
  state: 'OK' | 'AMBER' | 'RED' | 'RATE_ALERT' | 'SILENT' | 'TELEMETRY_OUTAGE'
  silent: boolean
  rateAlert: boolean
  lastLevelM?: number | null
  lastObservedAt?: string | null
  telemetryGroup?: string
  tag?: 'P' | 'D' | 'A'
}

export interface IncidentState {
  id: string
  level: 'NORMAL' | 'WATCH' | 'WARNING' | 'STAND_DOWN'
  anchorStation?: string
  openedAt?: string
  nonRain?: boolean
}

export interface ArrivalBandItem {
  siteId: string
  siteName: string
  riverKm: number
  earliestArrival: string
  latestArrival: string
  tag: 'P' | 'D' | 'A'
}

export const useStationStore = defineStore('station', () => {
  const corridorId = ref('bhote-trishuli-narayani')
  const runId = ref('live')
  const stations = ref<StationTile[]>([])
  const openIncident = ref<IncidentState | null>(null)
  const arrivalBands = ref<ArrivalBandItem[]>([])
  const simTime = ref<string>('08:00:00')
  const simSpeed = ref<number>(1)
  const isSimRunning = ref<boolean>(false)
  const isLoading = ref<boolean>(false)

  const activeLevel = computed(() => openIncident.value?.level || 'NORMAL')

  const fetchCorridorState = async (selectedRunId = 'live') => {
    isLoading.value = true
    runId.value = selectedRunId
    try {
      const res = await fetch(`/api/v1/corridors/${corridorId.value}/state?runId=${encodeURIComponent(selectedRunId)}`)
      if (res.ok) {
        const data = await res.json()
        stations.value = data.stations || []
        openIncident.value = data.openIncident || null
      }
    } catch (e) {
      console.warn('Failed to load initial corridor state; running with local simulation defaults', e)
      // Provide default corridor gauge tiles if backend is loading
      if (stations.value.length === 0) {
        stations.value = [
          { stationId: 'RSW', name: 'Rasuwagadhi', river: 'Bhote Koshi', riverKm: 0, warningM: 6.0, dangerM: null, state: 'OK', silent: false, rateAlert: false, lastLevelM: 1.62, tag: 'P' },
          { stationId: 'SYP', name: 'Syaphrubesi', river: 'Bhote Koshi', riverKm: 15, warningM: 5.5, dangerM: null, state: 'OK', silent: false, rateAlert: false, lastLevelM: 3.80, tag: 'P' },
          { stationId: 'BTW', name: 'Betrawati', river: 'Trishuli', riverKm: 38, warningM: 4.1, dangerM: null, state: 'OK', silent: false, rateAlert: false, lastLevelM: 3.55, tag: 'P' },
          { stationId: 'GLC', name: 'Galchhi', river: 'Trishuli', riverKm: 88, warningM: null, dangerM: null, state: 'OK', silent: false, rateAlert: false, lastLevelM: 5.00, tag: 'A' },
          { stationId: 'MLK', name: 'Malekhu', river: 'Trishuli', riverKm: 100, warningM: null, dangerM: null, state: 'OK', silent: false, rateAlert: false, lastLevelM: 4.00, tag: 'P' },
          { stationId: 'DVG', name: 'Devghat', river: 'Narayani', riverKm: 168, warningM: 7.3, dangerM: 9.0, state: 'OK', silent: false, rateAlert: false, lastLevelM: 4.60, tag: 'P' }
        ]
      }
    } finally {
      isLoading.value = false
    }
  }

  const handleReadingEvent = (reading: any) => {
    const idx = stations.value.findIndex(s => s.stationId === reading.stationId)
    if (idx !== -1) {
      stations.value[idx].lastLevelM = reading.levelM
      stations.value[idx].lastObservedAt = reading.observedAt
      stations.value[idx].silent = false
    }
  }

  const handleStationStateEvent = (stateEv: any) => {
    const idx = stations.value.findIndex(s => s.stationId === stateEv.stationId)
    if (idx !== -1) {
      stations.value[idx].state = stateEv.effectiveState || stateEv.band
      stations.value[idx].silent = !!stateEv.silent
      stations.value[idx].rateAlert = !!stateEv.rateAlert
    }
  }

  const handleIncidentEvent = (incEv: any) => {
    if (incEv.type === 'STOOD_DOWN') {
      openIncident.value = null
    } else {
      openIncident.value = {
        id: incEv.incidentId,
        level: incEv.type === 'LEVEL_CHANGED' ? 'WARNING' : 'WATCH',
        anchorStation: incEv.anchorStation,
        openedAt: incEv.at
      }
    }
  }

  const handleBandsEvent = (bandsEv: any) => {
    if (Array.isArray(bandsEv)) {
      arrivalBands.value = bandsEv
    }
  }

  return {
    corridorId,
    runId,
    stations,
    openIncident,
    arrivalBands,
    simTime,
    simSpeed,
    isSimRunning,
    isLoading,
    activeLevel,
    fetchCorridorState,
    handleReadingEvent,
    handleStationStateEvent,
    handleIncidentEvent,
    handleBandsEvent
  }
})
