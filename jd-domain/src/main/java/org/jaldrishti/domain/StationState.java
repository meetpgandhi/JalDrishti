package org.jaldrishti.domain;

/**
 * Operational telemetry and threshold state of a river monitoring gauge.
 *
 * <ul>
 *   <li>{@code OK} — reading within normal range</li>
 *   <li>{@code AMBER} — reading ≥ warning level (below danger)</li>
 *   <li>{@code RED} — reading ≥ danger level</li>
 *   <li>{@code RATE_ALERT} — overlaid flag: rising faster than threshold (any band)</li>
 *   <li>{@code SILENT} — expected reading overdue by more than grace period (default: 3 min [A])</li>
 *   <li>{@code TELEMETRY_OUTAGE} — silent, but explained by shared carrier failure (Rule R4)</li>
 * </ul>
 */
public enum StationState {
    OK,
    AMBER,
    RED,
    RATE_ALERT,
    SILENT,
    TELEMETRY_OUTAGE
}
