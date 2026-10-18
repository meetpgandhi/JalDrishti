package org.jaldrishti.adapter.persistence;

import org.jaldrishti.application.port.out.StationStateRepository;
import org.jaldrishti.domain.StationObservationState;
import org.jaldrishti.domain.StationState;
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
 * jOOQ implementation of StationStateRepository.
 */
@Repository
public class JooqStationStateRepository implements StationStateRepository {

    private final DSLContext dsl;

    public JooqStationStateRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext must not be null");
    }

    @Override
    public Optional<StationObservationState> find(String runId, String stationId) {
        Record rec = dsl.selectFrom(table("station_state"))
                .where(field("run_id").eq(runId))
                .and(field("station_id").eq(stationId))
                .fetchOne();

        return Optional.ofNullable(rec).map(this::mapRecord);
    }

    @Override
    public List<StationObservationState> findByRunId(String runId) {
        return dsl.selectFrom(table("station_state"))
                .where(field("run_id").eq(runId))
                .fetch(this::mapRecord);
    }

    @Override
    public void save(StationObservationState state) {
        Objects.requireNonNull(state, "state must not be null");

        OffsetDateTime silentSince = state.silentSince() != null
                ? state.silentSince().atOffset(ZoneOffset.UTC) : null;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        dsl.insertInto(table("station_state"))
                .columns(
                        field("run_id"),
                        field("station_id"),
                        field("band"),
                        field("rate_flag"),
                        field("silent_since"),
                        field("outage_ticket"),
                        field("updated_at")
                )
                .values(
                        state.runId(),
                        state.stationId(),
                        state.band().name(),
                        state.rateAlert(),
                        silentSince,
                        state.outageTicket(),
                        now
                )
                .onConflict(field("run_id"), field("station_id"))
                .doUpdate()
                .set(field("band"), state.band().name())
                .set(field("rate_flag"), state.rateAlert())
                .set(field("silent_since"), silentSince)
                .set(field("outage_ticket"), state.outageTicket())
                .set(field("updated_at"), now)
                .execute();
    }

    private StationObservationState mapRecord(Record r) {
        String bandStr = r.get("band", String.class);
        StationState band = bandStr != null ? StationState.valueOf(bandStr) : StationState.OK;
        Boolean rateFlag = r.get("rate_flag", Boolean.class);
        Object silentObj = r.get("silent_since");
        Instant silentSince = silentObj != null ? toInstant(silentObj) : null;
        String ticket = r.get("outage_ticket", String.class);

        return new StationObservationState(
                r.get("run_id", String.class),
                r.get("station_id", String.class),
                band,
                Boolean.TRUE.equals(rateFlag),
                null,
                null,
                null,
                silentSince != null,
                silentSince,
                ticket != null,
                ticket,
                null,
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
