package org.jaldrishti.adapter.persistence;

import org.jaldrishti.application.port.out.ReadingRepository;
import org.jaldrishti.domain.AlertTag;
import org.jaldrishti.domain.Reading;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * jOOQ implementation of ReadingRepository.
 *
 * <p>Enforces idempotent inserts: {@code ON CONFLICT (run_id, station_id, observed_at) DO NOTHING}.
 */
@Repository
public class JooqReadingRepository implements ReadingRepository {

    private final DSLContext dsl;

    public JooqReadingRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext must not be null");
    }

    @Override
    public void save(Reading reading) {
        Objects.requireNonNull(reading, "Reading must not be null");

        OffsetDateTime obsAt = reading.observedAt().atOffset(ZoneOffset.UTC);
        OffsetDateTime recAt = reading.receivedAt().atOffset(ZoneOffset.UTC);

        dsl.insertInto(table("reading"))
                .columns(
                        field("run_id"),
                        field("station_id"),
                        field("observed_at"),
                        field("level_m"),
                        field("rain_mm"),
                        field("received_at"),
                        field("source"),
                        field("tag")
                )
                .values(
                        reading.runId(),
                        reading.stationId(),
                        obsAt,
                        reading.levelM(),
                        reading.rainMm(),
                        recAt,
                        reading.source(),
                        reading.tag().name()
                )
                .onConflict(field("run_id"), field("station_id"), field("observed_at"))
                .doNothing()
                .execute();
    }

    @Override
    public List<Reading> findRecent(String runId, String stationId, Instant since) {
        OffsetDateTime sinceOffset = since.atOffset(ZoneOffset.UTC);

        return dsl.selectFrom(table("reading"))
                .where(field("run_id").eq(runId))
                .and(field("station_id").eq(stationId))
                .and(field("observed_at").ge(sinceOffset))
                .orderBy(field("observed_at").desc())
                .fetch(this::mapRecord);
    }

    @Override
    public Optional<Reading> findLatest(String runId, String stationId) {
        Record rec = dsl.selectFrom(table("reading"))
                .where(field("run_id").eq(runId))
                .and(field("station_id").eq(stationId))
                .orderBy(field("observed_at").desc())
                .limit(1)
                .fetchOne();

        return Optional.ofNullable(rec).map(this::mapRecord);
    }

    private Reading mapRecord(Record r) {
        Object obsObj = r.get("observed_at");
        Instant obs = toInstant(obsObj);

        Object recObj = r.get("received_at");
        Instant rec = toInstant(recObj);

        Double level = r.get("level_m", Double.class);
        Double rain = r.get("rain_mm", Double.class);
        String tagStr = r.get("tag", String.class);
        AlertTag tag = tagStr != null ? AlertTag.valueOf(tagStr) : AlertTag.A;

        return new Reading(
                r.get("run_id", String.class),
                r.get("station_id", String.class),
                obs,
                level != null ? level : 0.0,
                rain != null ? rain : 0.0,
                rec,
                r.get("source", String.class),
                tag
        );
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
