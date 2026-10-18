package org.jaldrishti.adapter.persistence;

import org.jaldrishti.application.port.out.DutyMatrixRepository;
import org.jaldrishti.domain.ActorType;
import org.jaldrishti.domain.DutyMatrixVersion;
import org.jaldrishti.domain.DutyRule;
import org.jaldrishti.domain.IncidentLevel;
import org.jaldrishti.domain.MatrixStatus;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
public class JooqDutyMatrixRepository implements DutyMatrixRepository {

    private final DSLContext dsl;

    public JooqDutyMatrixRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext must not be null");
    }

    @Override
    public Optional<DutyMatrixVersion> findLatestApprovedVersion() {
        Record rec = dsl.selectFrom(table("duty_matrix_version"))
                .where(field("status").eq(MatrixStatus.APPROVED.name()))
                .orderBy(field("version").desc())
                .limit(1)
                .fetchOne();

        return Optional.ofNullable(rec).map(this::mapVersion);
    }

    @Override
    public Optional<DutyMatrixVersion> findVersion(int version) {
        Record rec = dsl.selectFrom(table("duty_matrix_version"))
                .where(field("version").eq(version))
                .fetchOne();

        return Optional.ofNullable(rec).map(this::mapVersion);
    }

    @Override
    public List<DutyRule> findRulesByVersionAndLevel(int version, IncidentLevel level) {
        return dsl.selectFrom(table("duty_rule"))
                .where(field("version").eq(version))
                .and(field("level").eq(level.name()))
                .fetch()
                .map(this::mapRule);
    }

    @Override
    public Optional<DutyRule> findRule(String ruleId, int version) {
        Record rec = dsl.selectFrom(table("duty_rule"))
                .where(field("rule_id").eq(ruleId))
                .and(field("version").eq(version))
                .fetchOne();

        return Optional.ofNullable(rec).map(this::mapRule);
    }

    @Override
    public void saveMatrixVersion(DutyMatrixVersion v) {
        OffsetDateTime approvedAt = PersistenceUtils.toOffsetDateTime(v.approvedAt());

        dsl.insertInto(table("duty_matrix_version"))
                .columns(
                        field("version"),
                        field("status"),
                        field("created_by"),
                        field("approved_by"),
                        field("approved_at")
                )
                .values(
                        v.version(),
                        v.status().name(),
                        v.createdBy(),
                        v.approvedBy(),
                        approvedAt
                )
                .onConflict(field("version"))
                .doUpdate()
                .set(field("status"), v.status().name())
                .set(field("approved_by"), v.approvedBy())
                .set(field("approved_at"), approvedAt)
                .execute();
    }

    @Override
    public void saveDutyRule(DutyRule r) {
        dsl.insertInto(table("duty_rule"))
                .columns(
                        field("rule_id"),
                        field("version"),
                        field("level"),
                        field("actor_type"),
                        field("site_scope"),
                        field("action_code"),
                        field("action_text_en"),
                        field("deadline_expr"),
                        field("ack_means"),
                        field("requires_ack")
                )
                .values(
                        r.ruleId(),
                        r.version(),
                        r.level().name(),
                        r.actorType().name(),
                        r.siteScopeJson(),
                        r.actionCode(),
                        r.actionTextEn(),
                        r.deadlineExpr(),
                        r.ackMeans(),
                        r.requiresAck()
                )
                .onConflict(field("rule_id"), field("version"))
                .doUpdate()
                .set(field("level"), r.level().name())
                .set(field("actor_type"), r.actorType().name())
                .set(field("site_scope"), r.siteScopeJson())
                .set(field("action_code"), r.actionCode())
                .set(field("action_text_en"), r.actionTextEn())
                .set(field("deadline_expr"), r.deadlineExpr())
                .set(field("ack_means"), r.ackMeans())
                .set(field("requires_ack"), r.requiresAck())
                .execute();
    }

    private DutyMatrixVersion mapVersion(Record r) {
        int ver = r.get("version", Integer.class);
        String st = r.get("status", String.class);
        MatrixStatus status = st != null ? MatrixStatus.valueOf(st) : MatrixStatus.DRAFT;
        String createdBy = r.get("created_by", String.class);
        String approvedBy = r.get("approved_by", String.class);
        Object appAt = r.get("approved_at");
        Instant approvedAt = PersistenceUtils.toInstant(appAt);

        return new DutyMatrixVersion(ver, status, createdBy, approvedBy, approvedAt);
    }

    private DutyRule mapRule(Record r) {
        return new DutyRule(
                r.get("rule_id", String.class),
                r.get("version", Integer.class),
                IncidentLevel.valueOf(r.get("level", String.class)),
                ActorType.valueOf(r.get("actor_type", String.class)),
                r.get("site_scope", String.class),
                r.get("action_code", String.class),
                r.get("action_text_en", String.class),
                r.get("deadline_expr", String.class),
                r.get("ack_means", String.class),
                Boolean.TRUE.equals(r.get("requires_ack", Boolean.class))
        );
    }
}
