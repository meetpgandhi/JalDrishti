package org.jaldrishti.adapter.persistence;

import org.jaldrishti.application.port.out.IncidentEventRepository;
import org.jaldrishti.domain.AlertTag;
import org.jaldrishti.domain.EventType;
import org.jaldrishti.domain.IncidentEvent;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * jOOQ implementation of IncidentEventRepository.
 *
 * <p><strong>CRITICAL ARCHITECTURE RULE:</strong> This table is append-only.
 * Events are immutable audit records and must never be updated or deleted.
 */
@Repository
public class JooqIncidentEventRepository implements IncidentEventRepository {

    private final DSLContext dsl;

    public JooqIncidentEventRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext must not be null");
    }

    @Override
    public void append(IncidentEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        OffsetDateTime atOffset = event.at().atOffset(ZoneOffset.UTC);
        String jsonPayload = event.payloadJson() != null ? event.payloadJson() : "{}";

        dsl.insertInto(table("incident_event"))
                .columns(
                        field("incident_id"),
                        field("at"),
                        field("type"),
                        field("rule_id"),
                        field("payload")
                )
                .values(
                        event.incidentId(),
                        atOffset,
                        event.type().name(),
                        event.ruleId(),
                        JSONB.valueOf(jsonPayload)
                )
                .execute();
    }

    @Override
    public List<IncidentEvent> findByIncident(UUID incidentId) {
        return dsl.selectFrom(table("incident_event"))
                .where(field("incident_id").eq(incidentId))
                .orderBy(field("seq").asc())
                .fetch(this::mapRecord);
    }

    @Override
    public List<IncidentEvent> findByRunId(String runId) {
        return dsl.select(
                        field("ie.seq"),
                        field("ie.incident_id"),
                        field("ie.at"),
                        field("ie.type"),
                        field("ie.rule_id"),
                        field("ie.payload")
                )
                .from(table("incident_event").as("ie"))
                .join(table("incident").as("i"))
                .on(field("ie.incident_id").eq(field("i.id")))
                .where(field("i.run_id").eq(runId))
                .orderBy(field("ie.seq").asc())
                .fetch(this::mapRecord);
    }

    private IncidentEvent mapRecord(Record r) {
        Long seq = r.get("seq", Long.class);
        UUID incidentId = r.get("incident_id", UUID.class);
        Instant at = toInstant(r.get("at"));
        String typeStr = r.get("type", String.class);
        EventType type = typeStr != null ? EventType.valueOf(typeStr) : EventType.OPENED;
        String ruleId = r.get("rule_id", String.class);
        Object payloadObj = r.get("payload");
        String payloadJson = payloadObj != null ? payloadObj.toString() : "{}";

        return new IncidentEvent(seq, incidentId, at, type, ruleId, payloadJson, AlertTag.D);
    }

    private Instant toInstant(Object timeObj) {
        if (timeObj instanceof OffsetDateTime odt) {
            return odt.toInstant();
        } else if (timeObj instanceof Timestamp ts) {
            return ts.toInstant();
        } else if (timeObj instanceof Instant inst) {
            return inst;
        }
        return Instant.now();
    }
}
