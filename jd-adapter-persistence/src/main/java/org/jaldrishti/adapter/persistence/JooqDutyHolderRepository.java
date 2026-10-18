package org.jaldrishti.adapter.persistence;

import org.jaldrishti.application.port.out.DutyHolderRepository;
import org.jaldrishti.domain.ActorType;
import org.jaldrishti.domain.DutyHolder;
import org.jaldrishti.domain.Language;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
public class JooqDutyHolderRepository implements DutyHolderRepository {

    private final DSLContext dsl;

    public JooqDutyHolderRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext must not be null");
    }

    @Override
    public Optional<DutyHolder> findById(UUID id) {
        Record rec = dsl.selectFrom(table("duty_holder"))
                .where(field("id").eq(id))
                .fetchOne();

        return Optional.ofNullable(rec).map(this::mapRecord);
    }

    @Override
    public List<DutyHolder> findAll() {
        return dsl.selectFrom(table("duty_holder"))
                .orderBy(field("display_name").asc())
                .fetch()
                .map(this::mapRecord);
    }

    @Override
    public List<DutyHolder> findByActorType(ActorType actorType) {
        return dsl.selectFrom(table("duty_holder"))
                .where(field("actor_type").eq(actorType.name()))
                .fetch()
                .map(this::mapRecord);
    }

    @Override
    public List<DutyHolder> findBySiteId(String siteId) {
        return dsl.selectFrom(table("duty_holder"))
                .where(field("site_id").eq(siteId))
                .fetch()
                .map(this::mapRecord);
    }

    @Override
    public void save(DutyHolder h) {
        dsl.insertInto(table("duty_holder"))
                .columns(
                        field("id"),
                        field("actor_type"),
                        field("site_id"),
                        field("display_name"),
                        field("phone_enc"),
                        field("telegram_chat_id"),
                        field("language"),
                        field("deputy_id")
                )
                .values(
                        h.id(),
                        h.actorType().name(),
                        h.siteId(),
                        h.displayName(),
                        h.phoneEnc(),
                        h.telegramChatId(),
                        h.language() != null ? h.language().name() : "ne",
                        h.deputyId()
                )
                .onConflict(field("id"))
                .doUpdate()
                .set(field("actor_type"), h.actorType().name())
                .set(field("site_id"), h.siteId())
                .set(field("display_name"), h.displayName())
                .set(field("phone_enc"), h.phoneEnc())
                .set(field("telegram_chat_id"), h.telegramChatId())
                .set(field("language"), h.language() != null ? h.language().name() : "ne")
                .set(field("deputy_id"), h.deputyId())
                .execute();
    }

    private DutyHolder mapRecord(Record r) {
        String langStr = r.get("language", String.class);
        Language lang = Language.ne;
        if (langStr != null) {
            try {
                lang = Language.valueOf(langStr);
            } catch (Exception ignored) {}
        }

        return new DutyHolder(
                r.get("id", UUID.class),
                ActorType.valueOf(r.get("actor_type", String.class)),
                r.get("site_id", String.class),
                r.get("display_name", String.class),
                r.get("phone_enc", byte[].class),
                r.get("telegram_chat_id", String.class),
                lang,
                r.get("deputy_id", UUID.class)
        );
    }
}
