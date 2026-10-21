import { ref, onMounted, onUnmounted, watch, type Ref } from 'vue'
import { endpoints } from '@/lib/api'

// Live stream from GET /api/v1/stream?runId=. Event names sent by the backend:
// connected, reading, station-state, clock, incident (OPENED / LEVEL_CHANGED / STOOD_DOWN),
// evidence (EVIDENCE_ADDED), bands (either ArrivalBand[] or a BANDS_UPDATED incident event).
export interface SseHandlers {
  onReading?: (data: unknown) => void
  onStationState?: (data: unknown) => void
  onClock?: (data: unknown) => void
  onIncident?: (data: unknown) => void
  onEvidence?: (data: unknown) => void
  onBands?: (data: unknown) => void
}

export function useSSE(runId: Ref<string | null>, handlers: SseHandlers) {
  const isConnected = ref(false)
  let source: EventSource | null = null

  const listen = (name: string, handler?: (data: unknown) => void) => {
    source?.addEventListener(name, (e: Event) => {
      isConnected.value = true
      if (!handler) return
      try {
        handler(JSON.parse((e as MessageEvent<string>).data))
      } catch {
        /* ignore a malformed event rather than breaking the stream */
      }
    })
  }

  const connect = () => {
    source = new EventSource(endpoints.stream(runId.value ?? 'live'))
    source.onopen = () => { isConnected.value = true }
    source.onerror = () => { isConnected.value = false } // EventSource retries by itself
    listen('connected')
    listen('reading', handlers.onReading)
    listen('station-state', handlers.onStationState)
    listen('clock', handlers.onClock)
    listen('incident', handlers.onIncident)
    listen('evidence', handlers.onEvidence)
    listen('bands', handlers.onBands)
  }

  const disconnect = () => {
    source?.close()
    source = null
    isConnected.value = false
  }

  onMounted(connect)
  onUnmounted(disconnect)
  watch(runId, (next, prev) => {
    if (next !== prev) {
      disconnect()
      connect()
    }
  })

  return { isConnected }
}
