package org.jaldrishti.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An active or closed corridor-level flood incident.
 *
 * @param id            Incident UUID
 * @param runId         Simulation run ID or "live"
 * @param corridorId    Corridor identifier
 * @param level         Current incident severity level
 * @param openedAt      Timestamp when incident opened
 * @param closedAt      Timestamp when incident stood down, or null if open
 * @param anchorStation Most downstream gauge anchor providing corroborating evidence
 * @param configVersion Corridor configuration version used
 * @param matrixVersion Duty matrix version used
 * @param nonRain       Whether incident was triggered without rainfall (Rule R6)
 * @param closeReason   Forecaster-provided rationale for standing down
 */
public record Incident(
        UUID id,
        String runId,
        String corridorId,
        IncidentLevel level,
        Instant openedAt,
        Instant closedAt,
        String anchorStation,
        int configVersion,
        int matrixVersion,
        boolean nonRain,
        String closeReason
) {
    public Incident {
        Objects.requireNonNull(id, "Incident id must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(corridorId, "corridorId must not be null");
        Objects.requireNonNull(level, "level must not be null");
        Objects.requireNonNull(openedAt, "openedAt must not be null");
    }

    public static Incident createWatch(
            UUID id,
            String runId,
            String corridorId,
            Instant now,
            String anchorStation,
            int configVersion,
            int matrixVersion
    ) {
        return new Incident(
                id,
                runId,
                corridorId,
                IncidentLevel.WATCH,
                now,
                null,
                anchorStation,
                configVersion,
                matrixVersion,
                false,
                null
        );
    }

    public static Incident createWarning(
            UUID id,
            String runId,
            String corridorId,
            Instant now,
            String anchorStation,
            int configVersion,
            int matrixVersion,
            boolean nonRain
    ) {
        return new Incident(
                id,
                runId,
                corridorId,
                IncidentLevel.WARNING,
                now,
                null,
                anchorStation,
                configVersion,
                matrixVersion,
                nonRain,
                null
        );
    }

    public Incident escalateToWarning(Instant now, String newAnchor, boolean nonRain) {
        return new Incident(
                id,
                runId,
                corridorId,
                IncidentLevel.WARNING,
                openedAt,
                null,
                newAnchor != null ? newAnchor : anchorStation,
                configVersion,
                matrixVersion,
                nonRain,
                null
        );
    }

    public Incident standDown(Instant now, String reason) {
        Objects.requireNonNull(reason, "Stand down reason must not be null");
        return new Incident(
                id,
                runId,
                corridorId,
                IncidentLevel.STAND_DOWN,
                openedAt,
                now,
                anchorStation,
                configVersion,
                matrixVersion,
                nonRain,
                reason
        );
    }

    public boolean isOpen() {
        return level == IncidentLevel.WATCH || level == IncidentLevel.WARNING;
    }
}
