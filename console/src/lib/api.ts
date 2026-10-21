// The only place that calls fetch(). Every non-2xx response throws ApiError,
// so a failed request can never be shown to the user as a success.

export class ApiError extends Error {
  readonly status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(method: 'GET' | 'POST', url: string, body?: unknown): Promise<T> {
  const init: RequestInit = { method }
  if (body !== undefined) {
    init.headers = { 'Content-Type': 'application/json' }
    init.body = JSON.stringify(body)
  }
  const res = await fetch(url, init)
  const text = await res.text()
  if (!res.ok) {
    throw new ApiError(res.status, `${method} ${url} failed with ${res.status}${text ? `: ${text.slice(0, 200)}` : ''}`)
  }
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T>(url: string) => request<T>('GET', url),
  post: <T>(url: string, body?: unknown) => request<T>('POST', url, body),
}

const e = encodeURIComponent

/** Every endpoint the UI may call. Do not call any URL that is not listed here. */
export const endpoints = {
  corridor: (corridorId: string) => `/api/v1/corridors/${e(corridorId)}`,
  corridorState: (corridorId: string, runId: string) => `/api/v1/corridors/${e(corridorId)}/state?runId=${e(runId)}`,
  incidentEvents: (runId: string) => `/api/v1/incidents/events?runId=${e(runId)}`,
  why: (incidentId: string) => `/api/v1/incidents/${e(incidentId)}/why`,
  eventWhy: (incidentId: string, seq: number) => `/api/v1/incidents/${e(incidentId)}/events/${seq}/why`,
  actions: (incidentId: string) => `/api/v1/incidents/${e(incidentId)}/actions`,
  escalate: (incidentId: string) => `/api/v1/incidents/${e(incidentId)}/escalate`,
  cancelWatch: (incidentId: string) => `/api/v1/incidents/${e(incidentId)}/cancel-watch`,
  standDown: (incidentId: string) => `/api/v1/incidents/${e(incidentId)}/stand-down`,
  reports: (runId: string) => `/api/v1/reports?runId=${e(runId)}`,
  startRun: () => `/api/v1/sim/runs`,
  play: (runId: string) => `/api/v1/sim/runs/${e(runId)}/play`,
  pause: (runId: string) => `/api/v1/sim/runs/${e(runId)}/pause`,
  speed: (runId: string) => `/api/v1/sim/runs/${e(runId)}/speed`,
  clock: (runId: string) => `/api/v1/sim/runs/${e(runId)}/clock`,
  asHappened: (runId: string) => `/api/v1/sim/runs/${e(runId)}/timeline`,
  inject: (runId: string) => `/api/v1/sim/runs/${e(runId)}/inject`,
  replayReport: (runId: string) => `/api/v1/sim/runs/${e(runId)}/report`,
  ask: () => `/api/v1/assistant/ask`,
  dutyHolders: () => `/api/v1/phone/duty-holders`,
  inbox: (dutyHolderId: string) => `/api/v1/phone/inbox/${e(dutyHolderId)}`,
  phoneAck: (actionId: string) => `/api/v1/phone/ack/${e(actionId)}`,
  phoneDecline: (actionId: string) => `/api/v1/phone/decline/${e(actionId)}`,
  phoneReport: () => `/api/v1/phone/report`,
  signedAck: (actionId: string, token: string) => `/api/v1/acks/${e(actionId)}?token=${e(token)}`,
  stream: (runId: string) => `/api/v1/stream?runId=${e(runId)}`,
  messages: (runId: string) => `/api/v1/messages?runId=${e(runId)}`,
  capMessages: (runId: string) => `/api/v1/cap-messages?runId=${e(runId)}`,
}
