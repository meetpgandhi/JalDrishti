package org.jaldrishti.adapter.persistence;

import org.jaldrishti.application.port.out.FieldReportRepository;
import org.jaldrishti.domain.FieldReportRecord;
import org.jaldrishti.domain.ReportStatus;
import org.jaldrishti.domain.SenderTrust;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
public class JooqFieldReportRepository implements FieldReportRepository {

    private final DSLContext dsl;

    public JooqFieldReportRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext must not be null");
    }

    @Override
    public void save(FieldReportRecord r) {
        OffsetDateTime received = PersistenceUtils.toOffsetDateTime(r.receivedAt());

        dsl.insertInto(table("field_report"))
                .columns(
                        field("id"),
                        field("run_id"),
                        field("received_at"),
                        field("sender_hash"),
                        field("trust"),
                        field("channel"),
                        field("audio_uri"),
                        field("raw_text"),
                        field("parsed"),
                        field("status"),
                        field("model_id")
                )
                .values(
                        r.id(),
                        r.runId(),
                        received,
                        r.senderHash(),
                        r.trust().name(),
                        r.channel(),
                        r.audioUri(),
                        r.rawText(),
                        r.parsedJson(),
                        r.status().name(),
                        r.modelId()
                )
                .onConflict(field("id"))
                .doUpdate()
                .set(field("parsed"), r.parsedJson())
                .set(field("status"), r.status().name())
                .set(field("model_id"), r.modelId())
                .execute();
    }

    @Override
    public Optional<FieldReportRecord> findById(UUID id) {
        Record rec = dsl.selectFrom(table("field_report"))
                .where(field("id").eq(id))
                .fetchOne();

        return Optional.ofNullable(rec).map(this::mapRecord);
    }

    @Override
    public List<FieldReportRecord> findByRunId(String runId) {
        return dsl.selectFrom(table("field_report"))
                .where(field("run_id").eq(runId))
                .orderBy(field("received_at").desc())
                .fetch()
                .map(this::mapRecord);
    }

    private FieldReportRecord mapRecord(Record r) {
        return new FieldReportRecord(
                r.get("id", UUID.class),
                r.get("run_id", String.class),
                PersistenceUtils.toInstant(r.get("received_at")),
                r.get("sender_hash", String.class),
                SenderTrust.valueOf(r.get("trust", String.class)),
                r.get("channel", String.class),
                r.get("audio_uri", String.class),
                r.get("raw_text", String.class),
                r.get("parsed", String.class),
                ReportStatus.valueOf(r.get("status", String.class)),
                r.get("model_id", String.class)
        );
    }
}
