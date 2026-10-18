package org.jaldrishti.domain;

import java.util.Objects;

/**
 * Versioned corridor configuration thresholds and parameters.
 *
 * <p>Every alert and rule evaluation logs the configuration version used.
 * Every parameter carries an underlying provenance tag [P] or [A].
 */
public record CorridorConfig(
        int version,
        String corridorId,
        int cadenceMin,                   // [P] 10 min
        int graceMin,                     // [A] 3 min default
        int sweepIntervalSec,             // [A] 60 s default
        double rateThreshold10Min,        // [A] 0.5 m default
        double rateThreshold30Min,        // [A] 0.8 m default
        double rateThreshold60Min,        // [A] 1.0 m default
        double vMinMs,                    // [A] 3.0 m/s minimum debris wave velocity
        double vMaxMs,                    // [A] 35.0 m/s maximum outburst wave velocity
        double outageFraction,            // [A] 0.5 carrier outage threshold
        double noRainThresholdMm3h,       // [A] 5.0 mm/3h no-rain threshold
        double parseConfidenceFloor,      // [A] 0.7 confidence threshold
        double watchRadiusHours,          // [A] 2.0 h notification horizon
        double warningRadiusHours,        // [A] 6.0 h notification horizon
        double heightenedMultiplier,      // [A] 0.5 rate threshold factor under heightened watch
        int maxWatchDurationDays          // [A] 7 days maximum heightened watch
) {
    public CorridorConfig {
        Objects.requireNonNull(corridorId, "corridorId must not be null");
        if (version < 1) {
            throw new IllegalArgumentException("Version must be >= 1");
        }
        if (cadenceMin <= 0) {
            throw new IllegalArgumentException("Cadence must be > 0");
        }
        if (vMinMs <= 0 || vMaxMs <= vMinMs) {
            throw new IllegalArgumentException("Invalid velocity bounds: [" + vMinMs + ", " + vMaxMs + "]");
        }
    }

    /**
     * Default corridor configuration for the Bhote Koshi - Trishuli - Narayani corridor.
     */
    public static CorridorConfig defaultConfig() {
        return new CorridorConfig(
                1,
                "bhote-trishuli-narayani",
                10,
                3,
                60,
                0.5,
                0.8,
                1.0,
                3.0,
                35.0,
                0.5,
                5.0,
                0.7,
                2.0,
                6.0,
                0.5,
                7
        );
    }
}
