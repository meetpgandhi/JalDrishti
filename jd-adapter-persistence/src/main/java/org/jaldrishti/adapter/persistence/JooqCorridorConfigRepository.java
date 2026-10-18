package org.jaldrishti.adapter.persistence;

import org.jaldrishti.application.port.out.CorridorConfigRepository;
import org.jaldrishti.domain.CorridorConfig;
import org.jaldrishti.domain.Site;
import org.jaldrishti.domain.Station;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * jOOQ implementation of CorridorConfigRepository.
 */
@Repository
public class JooqCorridorConfigRepository implements CorridorConfigRepository {

    private final DSLContext dsl;
    private final Map<String, Boolean> heightenedWatchByRun = new ConcurrentHashMap<>();
    private final Map<String, Double> catchmentRainByRun = new ConcurrentHashMap<>();

    public JooqCorridorConfigRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext must not be null");
    }

    @Override
    public Optional<CorridorConfig> findCurrent(String corridorId) {
        return Optional.of(CorridorConfig.defaultConfig());
    }

    @Override
    public Optional<CorridorConfig> findByVersion(String corridorId, int version) {
        return Optional.of(CorridorConfig.defaultConfig());
    }

    @Override
    public List<Station> findStations(String corridorId) {
        return dsl.selectFrom(table("station"))
                .where(field("corridor_id").eq(corridorId))
                .orderBy(field("river_km").asc())
                .fetch(this::mapStation);
    }

    @Override
    public Optional<Station> findStationById(String stationId) {
        Record r = dsl.selectFrom(table("station"))
                .where(field("id").eq(stationId))
                .fetchOne();

        return Optional.ofNullable(r).map(this::mapStation);
    }

    @Override
    public List<Site> findSites(String corridorId) {
        return dsl.selectFrom(table("site"))
                .where(field("corridor_id").eq(corridorId))
                .orderBy(field("river_km").asc())
                .fetch(this::mapSite);
    }

    @Override
    public boolean isHeightenedWatchActive(String runId, String corridorId) {
        return Boolean.TRUE.equals(heightenedWatchByRun.get(runId + ":" + corridorId));
    }

    @Override
    public void setHeightenedWatchActive(String runId, String corridorId, boolean active) {
        heightenedWatchByRun.put(runId + ":" + corridorId, active);
    }

    @Override
    public double getCatchmentRainMm3h(String runId, String corridorId) {
        return catchmentRainByRun.getOrDefault(runId + ":" + corridorId, 0.0);
    }

    public void setCatchmentRainMm3h(String runId, String corridorId, double rainMm) {
        catchmentRainByRun.put(runId + ":" + corridorId, rainMm);
    }

    private Station mapStation(Record r) {
        Double riverKm = r.get("river_km", Double.class);
        Double lat = r.get("lat", Double.class);
        Double lon = r.get("lon", Double.class);
        Double warnM = r.get("warning_m", Double.class);
        Double dangM = r.get("danger_m", Double.class);
        Integer cad = r.get("cadence_min", Integer.class);

        return new Station(
                r.get("id", String.class),
                r.get("corridor_id", String.class),
                r.get("name_en", String.class),
                r.get("river", String.class),
                riverKm != null ? riverKm : 0.0,
                lat != null ? lat : 0.0,
                lon != null ? lon : 0.0,
                warnM,
                dangM,
                cad != null ? cad : 10,
                r.get("telemetry_group", String.class)
        );
    }

    private Site mapSite(Record r) {
        Double riverKm = r.get("river_km", Double.class);
        Double lat = r.get("lat", Double.class);
        Double lon = r.get("lon", Double.class);

        return new Site(
                r.get("id", String.class),
                r.get("corridor_id", String.class),
                r.get("name_en", String.class),
                r.get("name_ne", String.class),
                Collections.emptyList(),
                r.get("type", String.class),
                riverKm != null ? riverKm : 0.0,
                lat != null ? lat : 0.0,
                lon != null ? lon : 0.0,
                Collections.emptyList()
        );
    }
}
