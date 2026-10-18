package org.jaldrishti.domain;

import java.util.Objects;

/**
 * Empirical arrival calibration point measured from an upstream anchor.
 *
 * @param siteId                Site identifier (e.g. "BTW-S", "MLB")
 * @param riverKm               Chainage in km from origin
 * @param travelTimeMinEarliest Earliest travel time in minutes from anchor front
 * @param travelTimeMinLatest   Latest travel time in minutes from anchor front
 * @param tag                   Provenance tag ([D] or [A])
 */
public record CalibrationPoint(
        String siteId,
        double riverKm,
        double travelTimeMinEarliest,
        double travelTimeMinLatest,
        AlertTag tag
) {
    public CalibrationPoint {
        Objects.requireNonNull(siteId, "siteId must not be null");
        Objects.requireNonNull(tag, "tag must not be null");
        if (travelTimeMinEarliest < 0 || travelTimeMinLatest < travelTimeMinEarliest) {
            throw new IllegalArgumentException("Invalid travel time range: ["
                    + travelTimeMinEarliest + ", " + travelTimeMinLatest + "]");
        }
    }
}
