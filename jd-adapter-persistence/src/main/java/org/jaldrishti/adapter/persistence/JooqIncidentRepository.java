package org.jaldrishti.adapter.persistence;

import org.jaldrishti.application.port.out.IncidentRepository;
import org.jaldrishti.domain.Incident;
import org.jaldrishti.domain.IncidentLevel;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * jOOQ implementation of IncidentRepository.
 */
@Repository
public class JooqIncidentRepository implements IncidentRepository {

    private final DSLContext dsl;

    public JooqIncidentRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext must not be null");
    }

    @Override
    public Optional<Incident> findOpenIncident(String runId, String corridorId) {
        Record rec = dsl.selectFrom(table("incident"))
                .where(field("run_id").eq(runId))
                .and(field("corridor_id").eq(corridorId))
                .and(field("closed_at").isNull())
                .orderBy(field("opened_at").desc())
                .limit(1)
                .fetchOne();

        return Optional.ofNullable(rec).map(this::mapRecord);
    }

    @Override
    public Optional<Incident> findById(UUID incidentId) {
        Record rec = dsl.selectFrom(table("incident"))
                .where(field("id").eq(incidentId))
                .fetchOne();

        return Optional.ofNullable(rec).map(this::mapRecord);
    }

    @Override
    public void save(Incident incident) {
        Objects.requireNonNull(incident, "Incident must not be null");

        OffsetDateTime opened = incident.openedAt().atOffset(ZoneOffset.UTC);
        OffsetDateTime closed = incident.closedAt() != null ? incident.closedAt().atOffset(ZoneOffset.UTC) : null;

        dsl.insertInto(table("incident"))
                .columns(
                        field("id"),
                        field("run_id"),
                        field("corridor_id"),
                        field("level"),
                        field("opened_at"),
                        field("closed_at"),
                        field("anchor_station"),
                        field("config_version"),
                        field("matrix_version")
                )
                .values(
                        incident.id(),
                        incident.runId(),
                        incident.corridorId(),
                        incident.level().name(),
                        opened,
                        closed,
                        incident.anchorStation(),
                        incident.configVersion(),
                        incident.matrixVersion()
                )
                .onConflict(field("id"))
                .doUpdate()
                .set(field("level"), incident.level().name())
                .set(field("closed_at"), closed)
                .set(field("anchor_station"), incident.anchorStation())
                .execute();
    }

    private Incident mapRecord(Record r) {
        UUID id = r.get("id", UUID.class);
        String levelStr = r.get("level", String.class);
        IncidentLevel level = levelStr != null ? IncidentLevel.valueOf(levelStr) : IncidentLevel.WATCH;

        Instant opened = toInstant(r.get("opened_at"));
        Object closedObj = r.get("closed_at");
        Instant closed = closedObj != null ? toInstant(closedObj) : null;

        Integer configVer = r.get("config_version", Integer.class);
        Integer matrixVer = r.get("matrix_version", Integer.class);

        return new Incident(
                id,
                r.get("run_id", String.class),
                r.get("corridor_id", String.class),
                level,
                opened,
                closed,
                r.get("anchor_station", String.class),
                configVer != null ? configVer : 1,
                matrixVer != null ? matrixVer : 1,
                false,
                null
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
