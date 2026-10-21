import type { ParsedReport } from '@/types/api'

/** The sentence a rule wrote when it fired (payload.description), or a forecaster's reason (payload.reason). */
export function eventDescription(payloadJson: string | null | undefined): string | null {
  if (!payloadJson) return null
  try {
    const p = JSON.parse(payloadJson) as { description?: unknown; reason?: unknown }
    if (typeof p.description === 'string' && p.description.trim()) return p.description
    if (typeof p.reason === 'string' && p.reason.trim()) return `Reason: ${p.reason}`
    return null
  } catch {
    return null
  }
}

/** FieldReport.parsedJson → ParsedReport, or null if missing or malformed. */
export function parseReport(parsedJson: string | null | undefined): ParsedReport | null {
  if (!parsedJson) return null
  try {
    const p = JSON.parse(parsedJson) as Partial<ParsedReport>
    if (typeof p.hazardType !== 'string' || typeof p.confidence !== 'number') return null
    return p as ParsedReport
  } catch {
    return null
  }
}
