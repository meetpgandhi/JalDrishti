package org.jaldrishti.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Current operational telemetry state for a station in a given simulation or live run.
 *
 * @param runId              Simulation run ID or "live"
 * @param stationId          Station identifier
 * @param band               Height band status (OK, AMBER, RED)
 * @param rateAlert          Whether the rate-of-rise threshold is currently breached
 * @param rateAlertAt        Timestamp when rate-of-rise alert fired
 * @param lastGoodReadingAt  Timestamp of the last received good reading
 * @param firstMissedSlotAt  Timestamp of the first overdue slot
 * @param silent             Whether the station is currently in SILENT state
 * @param silentSince        Timestamp when the station was declared SILENT
 * @param telemetryOutage    Whether silence is explained by a shared telemetry carrier outage
 * @param outageTicket       Outage ticket ID if telemetryOutage is true
 * @param lastReportedLevel  Most recent observed water level in metres
 * @param lastReportedRain   Most recent observed rain in millimetres
 */
public record StationObservationState(
        String runId,
        String stationId,
        StationState band,
        boolean rateAlert,
        Instant rateAlertAt,
        Instant lastGoodReadingAt,
        Instant firstMissedSlotAt,
        boolean silent,
        Instant silentSince,
        boolean telemetryOutage,
        String outageTicket,
        Double lastReportedLevel,
        Double lastReportedRain
) {
    public StationObservationState {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(stationId, "stationId must not be null");
        Objects.requireNonNull(band, "band must not be null");
    }

    public static StationObservationState initial(String runId, String stationId) {
        return new StationObservationState(
                runId,
                stationId,
                StationState.OK,
                false,
                null,
                null,
                null,
                false,
                null,
                false,
                null,
                null,
                null
        );
    }

    /**
     * Effective station state factoring in SILENT, RATE_ALERT, and TELEMETRY_OUTAGE flags.
     */
    public StationState effectiveState() {
        if (telemetryOutage) {
            return StationState.TELEMETRY_OUTAGE;
        }
        if (silent) {
            return StationState.SILENT;
        }
        if (rateAlert) {
            return StationState.RATE_ALERT;
        }
        return band;
    }
}
