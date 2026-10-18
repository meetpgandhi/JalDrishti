package org.jaldrishti.domain;

import java.util.Objects;

/**
 * Mapping rule defining what action a duty holder category must execute
 * at a given alert level along the corridor.
 */
public record DutyRule(
        String ruleId,
        int version,
        IncidentLevel level,
        ActorType actorType,
        String siteScopeJson,
        String actionCode,
        String actionTextEn,
        String deadlineExpr,
        String ackMeans,
        boolean requiresAck
) {
    public DutyRule {
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        Objects.requireNonNull(level, "level must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
        Objects.requireNonNull(actionCode, "actionCode must not be null");
        Objects.requireNonNull(actionTextEn, "actionTextEn must not be null");
        Objects.requireNonNull(deadlineExpr, "deadlineExpr must not be null");
        Objects.requireNonNull(ackMeans, "ackMeans must not be null");
    }
}
