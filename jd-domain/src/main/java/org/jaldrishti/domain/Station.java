package org.jaldrishti.domain;

import java.util.Objects;

/**
 * Hydrological river gauge station along a river corridor.
 *
 * @param id             Station identifier (e.g., "RSW", "SYP")
 * @param corridorId     Corridor identifier (e.g., "bhote-trishuli-narayani")
 * @param name           Display name (e.g., "Rasuwagadhi")
 * @param river          River name (e.g., "Bhote Koshi")
 * @param riverKm        Distance downstream from corridor origin in km
 * @param lat            Latitude in decimal degrees
 * @param lon            Longitude in decimal degrees
 * @param warningM       Warning stage threshold in metres, or null if uncalibrated
 * @param dangerM        Danger stage threshold in metres, or null if uncalibrated
 * @param cadenceMin     Expected telemetry interval in minutes (default: 10 [P])
 * @param telemetryGroup Identifier for shared telemetry infrastructure/carrier
 */
public record Station(
        String id,
        String corridorId,
        String name,
        String river,
        double riverKm,
        double lat,
        double lon,
        Double warningM,
        Double dangerM,
        int cadenceMin,
        String telemetryGroup
) {
    public Station {
        Objects.requireNonNull(id, "Station id must not be null");
        Objects.requireNonNull(corridorId, "CorridorId must not be null");
        if (cadenceMin <= 0) {
            throw new IllegalArgumentException("Cadence must be greater than zero, got: " + cadenceMin);
        }
    }

    public boolean hasWarningThreshold() {
        return warningM != null;
    }

    public boolean hasDangerThreshold() {
        return dangerM != null;
    }
}
