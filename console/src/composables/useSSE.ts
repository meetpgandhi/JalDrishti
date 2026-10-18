import { ref, onMounted, onUnmounted } from 'vue'

export interface SseOptions {
  runId?: string
  onReading?: (data: any) => void
  onStationState?: (data: any) => void
  onIncident?: (data: any) => void
  onBands?: (data: any) => void
  onClock?: (data: any) => void
  onError?: (err: Event) => void
}

/**
 * Composable for connecting to the JalDrishti SSE event stream (/api/v1/stream).
 * Automatically reconnects and dispatches typed platform events.
 */
export function useSSE(options: SseOptions) {
  const isConnected = ref(false)
  const lastEventId = ref<string | null>(null)
  let eventSource: EventSource | null = null

  const connect = () => {
    const runId = options.runId || 'live'
    const url = `/api/v1/stream?runId=${encodeURIComponent(runId)}`

    eventSource = new EventSource(url)

    eventSource.onopen = () => {
      isConnected.value = true
    }

    eventSource.onerror = (err) => {
      isConnected.value = false
      if (options.onError) {
        options.onError(err)
      }
    }

    // Typed SSE event listeners
    eventSource.addEventListener('connected', (e: MessageEvent) => {
      lastEventId.value = e.lastEventId
      isConnected.value = true
    })

    eventSource.addEventListener('reading', (e: MessageEvent) => {
      lastEventId.value = e.lastEventId
      if (options.onReading) {
        options.onReading(JSON.parse(e.data))
      }
    })

    eventSource.addEventListener('station-state', (e: MessageEvent) => {
      lastEventId.value = e.lastEventId
      if (options.onStationState) {
        options.onStationState(JSON.parse(e.data))
      }
    })

    eventSource.addEventListener('incident', (e: MessageEvent) => {
      lastEventId.value = e.lastEventId
      if (options.onIncident) {
        options.onIncident(JSON.parse(e.data))
      }
    })

    eventSource.addEventListener('bands', (e: MessageEvent) => {
      lastEventId.value = e.lastEventId
      if (options.onBands) {
        options.onBands(JSON.parse(e.data))
      }
    })

    eventSource.addEventListener('clock', (e: MessageEvent) => {
      lastEventId.value = e.lastEventId
      if (options.onClock) {
        options.onClock(JSON.parse(e.data))
      }
    })
  }

  const disconnect = () => {
    if (eventSource) {
      eventSource.close()
      eventSource = null
      isConnected.value = false
    }
  }

  onMounted(() => {
    connect()
  })

  onUnmounted(() => {
    disconnect()
  })

  return {
    isConnected,
    lastEventId,
    connect,
    disconnect
  }
}
