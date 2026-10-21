// Shapes returned by the JalDrishti backend (Spring Boot, jd-adapter-rest / jd-domain).
// All timestamps are ISO-8601 strings in UTC. Do not add fields that the backend does not send.

export type Iso = string
export type Tag = 'P' | 'D' | 'A'
export type Tone = 'neutral' | 'ok' | 'watch' | 'danger' | 'info' | 'ai' | 'outage'

export type StationStateCode = 'OK' | 'AMBER' | 'RED' | 'RATE_ALERT' | 'SILENT' | 'TELEMETRY_OUTAGE'
export type IncidentLevel = 'NORMAL' | 'WATCH' | 'WARNING' | 'STAND_DOWN'
export type ActionState =
  | 'SENT' | 'DELIVERED' | 'ACKED' | 'DECLINED'
  | 'ESCALATED_1' | 'ESCALATED_2' | 'ESCALATED_3' | 'UNRESOLVED'
export type EventType = 'OPENED' | 'LEVEL_CHANGED' | 'EVIDENCE_ADDED' | 'BANDS_UPDATED' | 'STOOD_DOWN'
export type Trust = 'OFFICIAL' | 'OBSERVER' | 'PUBLIC'
export type ReportStatus = 'PENDING' | 'PARSED' | 'UNPARSED' | 'REJECTED'
export type ActorType =
  | 'HYDRO_CONTROL' | 'HYDRO_SAFETY' | 'WARD_CHAIR' | 'CDO'
  | 'TRAFFIC_POLICE' | 'SCHOOL_PRINCIPAL' | 'NDRRMA' | 'GAUGE_READER'
export type HazardType =
  | 'DEBRIS_FLOOD' | 'RAPID_RISE' | 'LANDSLIDE' | 'BRIDGE_DAMAGE'
  | 'PEOPLE_TRAPPED' | 'NO_HAZARD' | 'UNCLEAR'
export type InjectionType =
  | 'DESTROY_GAUGE' | 'RESTORE_GAUGE' | 'SET_LEVEL' | 'RAIN' | 'REPORT' | 'HEIGHTENED_WATCH'

/** GET /api/v1/corridors/{id}/state?runId= → stations[] */
export interface StationTile {
  stationId: string
  name: string
  river: string
  riverKm: number
  warningM: number | null
  dangerM: number | null
  state: StationStateCode
  silent: boolean
  rateAlert: boolean
  lastLevelM: number | null
  lastObservedAt: Iso | null
  telemetryGroup: string | null
}

export interface Incident {
  id: string
  runId: string
  corridorId: string
  level: IncidentLevel
  openedAt: Iso
  closedAt: Iso | null
  anchorStation: string | null
  configVersion: number
  matrixVersion: number
  nonRain: boolean
  closeReason: string | null
}

/** GET /api/v1/corridors/{id}/state?runId= */
export interface CorridorState {
  corridorId: string
  runId: string
  stations: StationTile[]
  openIncident: Incident | null
}

export interface Station {
  id: string
  corridorId: string
  name: string
  river: string
  riverKm: number
  lat: number
  lon: number
  warningM: number | null
  dangerM: number | null
  cadenceMin: number
  telemetryGroup: string | null
}

export interface Site {
  id: string
  corridorId: string
  nameEn: string
  nameNe: string
  aliases: string[]
  type: string
  riverKm: number
  lat: number
  lon: number
  dutyHolderTypes: ActorType[]
}

export interface CorridorConfig {
  version: number
  corridorId: string
  cadenceMin: number
  graceMin: number
  vMinMs: number
  vMaxMs: number
  [key: string]: unknown
}

/** GET /api/v1/corridors/{id} */
export interface CorridorDetails {
  config: CorridorConfig
  stations: Station[]
  sites: Site[]
}

/** POST /api/v1/sim/runs and /play, /pause, /speed, GET /clock */
export interface RunSnapshot {
  runId: string
  scenario: string
  status: string
  speed: number
  simTime: Iso
  startTime: Iso
  endTime: Iso
}

/** SSE event "bands" carries ArrivalBand[] */
export interface ArrivalBand {
  siteId: string
  siteName: string
  riverKm: number
  earliestArrival: Iso
  latestArrival: Iso
  tag: Tag
}

/** GET /api/v1/incidents/events?runId= */
export interface IncidentEvent {
  seq: number | null
  incidentId: string
  at: Iso
  type: EventType
  ruleId: string | null
  payloadJson: string | null
  tag: Tag
}

/** GET /api/v1/incidents/{id}/why and /api/v1/incidents/{id}/events/{seq}/why */
export interface WhyExplanation {
  incidentId: string
  eventSeq: number
  at: Iso
  timeNpt: string
  ruleId: string | null
  ruleName: string
  explanation: string
  mathematicalInputs: Record<string, unknown>
  tag: Tag
  configVersion: number
}

/** GET /api/v1/incidents/{id}/actions */
export interface DispatchedAction {
  id: string
  runId: string
  incidentId: string
  dutyHolderId: string
  actionCode: string
  matrixVersion: number
  deadlineAt: Iso | null
  state: ActionState
  sentAt: Iso
  ackedAt: Iso | null
  ackDataJson: string | null
  escalationStep: number
}

/** GET /api/v1/phone/duty-holders (the backend sends more fields; use only these) */
export interface DutyHolder {
  id: string
  actorType: ActorType
  siteId: string
  displayName: string
  language: string
}

/** GET /api/v1/phone/inbox/{dutyHolderId} */
export interface PhoneMessage {
  actionId: string
  dutyHolderId: string
  actorType: string
  actionCode: string
  text: string
  ackUrl: string
  sentAt: Iso
}

/** GET /api/v1/messages?runId= — every message JalDrishti sent in the run, oldest first */
export interface MessageLogEntry {
  messageId: string
  actionId: string
  incidentId: string
  sentAt: Iso
  /** ACTION = the duty-holder's own order; DEPUTY_ESCALATION = sent to their deputy because they did not confirm */
  kind: 'ACTION' | 'DEPUTY_ESCALATION'
  /** 'WEB_PHONE_SIM' | 'TELEGRAM' */
  channel: string
  /** 'ne' | 'hi' | 'en' */
  language: string
  text: string
  recipientId: string
  recipientName: string | null
  recipientActorType: ActorType | null
  recipientSiteId: string | null
  /** set only for DEPUTY_ESCALATION: whose action this is */
  principalId: string | null
  principalName: string | null
  actionCode: string
  deadlineAt: Iso | null
  /** current state of the action, not the state when the message was sent */
  actionState: ActionState
  escalationStep: number
  ackedAt: Iso | null
}

/** GET /api/v1/cap-messages?runId= — CAP 1.2 public alerts prepared for NDRRMA */
export interface CapMessageEntry {
  identifier: string
  incidentId: string
  /** 'Alert' | 'Update' | 'Cancel' */
  msgType: string
  /** 'Exercise' (training) | 'Actual' (live) */
  status: string
  sentAt: Iso
  /** raw JSON text the gateway returned; contains NDRRMA_SIMULATED while no real gateway is connected */
  gatewayResponse: string | null
  xml: string
}

/** POST /api/v1/reports?runId= and POST /api/v1/phone/report */
export interface FieldReport {
  id: string
  runId: string
  receivedAt: Iso
  senderHash: string
  trust: Trust
  channel: string
  audioUri: string | null
  rawText: string
  parsedJson: string | null
  status: ReportStatus
  modelId: string | null
}

/** JSON inside FieldReport.parsedJson */
export interface ParsedReport {
  language?: string
  transcript?: string
  translationEn?: string
  locationId?: string | null
  locationText?: string | null
  hazardType: HazardType
  evidence?: string[]
  observedTimeText?: string | null
  peopleAtRiskCount?: number | null
  confidence: number
  needsCallback?: boolean
}

/** GET /api/v1/sim/runs/{runId}/timeline (the published "as happened" record) */
export interface TimelineMark {
  at: Iso
  label: string
  tag: Tag
}

export interface ReplayTimelineEvent {
  seq: number
  timeNpt: string
  eventType: string
  ruleId: string | null
  description: string
  tag: Tag
}

/** GET /api/v1/sim/runs/{runId}/report */
export interface ReplayReport {
  runId: string
  corridorId: string
  scenario: string
  generatedAt: Iso
  primaryClaimVerified: boolean
  warningTimeNpt: string | null
  asHappenedPhoneCallTimeNpt: string | null
  minutesSaved: number
  decisionTimeline: ReplayTimelineEvent[]
  provenanceCounts: Record<string, number>
  actionsSummary: Record<string, unknown>
  deterministicSha256: string
}

/** POST /api/v1/sim/runs/{runId}/inject */
export interface InjectionRequest {
  type: InjectionType
  stationId?: string
  levelM?: number
  rainMm?: number
  reportText?: string
  role?: Trust
  active?: boolean
}

export interface InjectionResponse {
  success: boolean
  type: string
  target: string
  message: string
  at: Iso
}

/** POST /api/v1/assistant/ask  body { question, runId } */
export interface AskResponse {
  answer: string
  sources: string[] | null
  tag: Tag | null
}

/** SSE "reading" */
export interface ReadingUpdate {
  stationId: string
  observedAt: Iso
  levelM: number
  rainMm: number
  tag: string | null
}

/** SSE "station-state" */
export interface StationStateUpdate {
  stationId: string
  state: StationStateCode
  silent: boolean
  rateAlert: boolean
  lastLevelM: number | null
  lastObservedAt: Iso | null
}

/** SSE "clock" */
export interface ClockUpdate {
  runId: string
  scenario: string
  simTime: Iso
  speed: number
  status: string
}

/** POST /api/v1/incidents/{id}/escalate | /cancel-watch | /stand-down  body { reason } */
export interface ForecasterActionResponse {
  status: string
  incidentId: string
}
