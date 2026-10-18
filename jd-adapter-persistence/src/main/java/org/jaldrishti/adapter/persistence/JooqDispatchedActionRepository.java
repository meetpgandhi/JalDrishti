package org.jaldrishti.adapter.persistence;

import org.jaldrishti.application.port.out.DispatchedActionRepository;
import org.jaldrishti.domain.ActionState;
import org.jaldrishti.domain.DispatchedAction;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
public class JooqDispatchedActionRepository implements DispatchedActionRepository {

    private final DSLContext dsl;

    public JooqDispatchedActionRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext must not be null");
    }

    @Override
    public void save(DispatchedAction a) {
        OffsetDateTime deadline = PersistenceUtils.toOffsetDateTime(a.deadlineAt());
        OffsetDateTime sent = PersistenceUtils.toOffsetDateTime(a.sentAt());
        OffsetDateTime acked = PersistenceUtils.toOffsetDateTime(a.ackedAt());

        dsl.insertInto(table("dispatched_action"))
                .columns(
                        field("id"),
                        field("run_id"),
                        field("incident_id"),
                        field("duty_holder_id"),
                        field("action_code"),
                        field("matrix_version"),
                        field("deadline_at"),
                        field("state"),
                        field("sent_at"),
                        field("acked_at"),
                        field("ack_data"),
                        field("escalation_step")
                )
                .values(
                        a.id(),
                        a.runId(),
                        a.incidentId(),
                        a.dutyHolderId(),
                        a.actionCode(),
                        a.matrixVersion(),
                        deadline,
                        a.state().name(),
                        sent,
                        acked,
                        a.ackDataJson(),
                        a.escalationStep()
                )
                .onConflict(field("id"))
                .doUpdate()
                .set(field("state"), a.state().name())
                .set(field("acked_at"), acked)
                .set(field("ack_data"), a.ackDataJson())
                .set(field("escalation_step"), a.escalationStep())
                .execute();
    }

    @Override
    public Optional<DispatchedAction> findById(UUID id) {
        Record rec = dsl.selectFrom(table("dispatched_action"))
                .where(field("id").eq(id))
                .fetchOne();

        return Optional.ofNullable(rec).map(this::mapRecord);
    }

    @Override
    public List<DispatchedAction> findByIncidentId(UUID incidentId) {
        return dsl.selectFrom(table("dispatched_action"))
                .where(field("incident_id").eq(incidentId))
                .orderBy(field("deadline_at").asc().nullsLast())
                .fetch()
                .map(this::mapRecord);
    }

    @Override
    public List<DispatchedAction> findByRunId(String runId) {
        return dsl.selectFrom(table("dispatched_action"))
                .where(field("run_id").eq(runId))
                .orderBy(field("sent_at").desc())
                .fetch()
                .map(this::mapRecord);
    }

    @Override
    public List<DispatchedAction> findPendingEscalation(String runId, Instant now) {
        return dsl.selectFrom(table("dispatched_action"))
                .where(field("run_id").eq(runId))
                .and(field("state").notIn(
                        ActionState.ACKED.name(),
                        ActionState.DECLINED.name(),
                        ActionState.UNRESOLVED.name()
                ))
                .fetch()
                .map(this::mapRecord);
    }

    @Override
    public boolean existsByIncidentAndHolderAndCode(UUID incidentId, UUID dutyHolderId, String actionCode) {
        Integer count = dsl.selectCount()
                .from(table("dispatched_action"))
                .where(field("incident_id").eq(incidentId))
                .and(field("duty_holder_id").eq(dutyHolderId))
                .and(field("action_code").eq(actionCode))
                .fetchOne(0, Integer.class);

        return count != null && count > 0;
    }

    private DispatchedAction mapRecord(Record r) {
        return new DispatchedAction(
                r.get("id", UUID.class),
                r.get("run_id", String.class),
                r.get("incident_id", UUID.class),
                r.get("duty_holder_id", UUID.class),
                r.get("action_code", String.class),
                r.get("matrix_version", Integer.class),
                PersistenceUtils.toInstant(r.get("deadline_at")),
                ActionState.valueOf(r.get("state", String.class)),
                PersistenceUtils.toInstant(r.get("sent_at")),
                PersistenceUtils.toInstant(r.get("acked_at")),
                r.get("ack_data", String.class),
                r.get("escalation_step", Integer.class)
        );
    }
}
