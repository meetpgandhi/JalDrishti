package org.jaldrishti.domain;

/**
 * Severity level of an incident along the corridor.
 *
 * <p>State machine: {@code NORMAL -> WATCH -> WARNING -> STAND_DOWN}.
 * <ul>
 *   <li>{@code NORMAL} — No active alerts on the corridor.</li>
 *   <li>{@code WATCH} — Internal only. Forecaster + duty-holders with arrival ≤ 2h notified.</li>
 *   <li>{@code WARNING} — Corroborated; act now. All duty-holders with arrival ≤ 6h notified + CAP to NDRRMA. Public.</li>
 *   <li>{@code STAND_DOWN} — Forecaster ends the incident with a documented reason. All previously notified receive all-clear.</li>
 * </ul>
 */
public enum IncidentLevel {
    NORMAL,
    WATCH,
    WARNING,
    STAND_DOWN
}
