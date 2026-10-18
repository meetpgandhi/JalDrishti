package org.jaldrishti.application.port.in;

import org.jaldrishti.domain.IncidentLevel;
import org.jaldrishti.domain.RuleResult;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result summary of a periodic silence and cascade sweep across a corridor.
 */
public record SweepResult(
        String runId,
        String corridorId,
        Instant sweepAt,
        int stationsSweptCount,
        List<String> silentStationIds,
        List<RuleResult> firedRules,
        IncidentLevel activeIncidentLevel
) {
    public SweepResult {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(corridorId, "corridorId must not be null");
        Objects.requireNonNull(sweepAt, "sweepAt must not be null");
        silentStationIds = silentStationIds == null ? Collections.emptyList() : List.copyOf(silentStationIds);
        firedRules = firedRules == null ? Collections.emptyList() : List.copyOf(firedRules);
    }
}
