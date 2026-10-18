package org.jaldrishti.adapter.persistence;

import org.jaldrishti.application.port.out.DispatchedMessageRepository;
import org.jaldrishti.domain.DispatchedMessageRecord;
import org.jaldrishti.domain.GuardResult;
import org.jaldrishti.domain.Language;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
public class JooqDispatchedMessageRepository implements DispatchedMessageRepository {

    private final DSLContext dsl;

    public JooqDispatchedMessageRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext must not be null");
    }

    @Override
    public void save(DispatchedMessageRecord m) {
        OffsetDateTime sent = PersistenceUtils.toOffsetDateTime(m.sentAt());

        dsl.insertInto(table("message"))
                .columns(
                        field("id"),
                        field("dispatched_action_id"),
                        field("channel"),
                        field("language"),
                        field("text"),
                        field("guard_result"),
                        field("fallback_used"),
                        field("model_id"),
                        field("sent_at")
                )
                .values(
                        m.id(),
                        m.dispatchedActionId(),
                        m.channel(),
                        m.language().name(),
                        m.text(),
                        m.guardResult().name(),
                        m.fallbackUsed(),
                        m.modelId(),
                        sent
                )
                .execute();
    }

    @Override
    public List<DispatchedMessageRecord> findByActionId(UUID actionId) {
        return dsl.selectFrom(table("message"))
                .where(field("dispatched_action_id").eq(actionId))
                .orderBy(field("sent_at").asc())
                .fetch()
                .map(this::mapRecord);
    }

    private DispatchedMessageRecord mapRecord(Record r) {
        return new DispatchedMessageRecord(
                r.get("id", UUID.class),
                r.get("dispatched_action_id", UUID.class),
                r.get("channel", String.class),
                Language.valueOf(r.get("language", String.class)),
                r.get("text", String.class),
                GuardResult.valueOf(r.get("guard_result", String.class)),
                Boolean.TRUE.equals(r.get("fallback_used", Boolean.class)),
                r.get("model_id", String.class),
                PersistenceUtils.toInstant(r.get("sent_at"))
        );
    }
}
