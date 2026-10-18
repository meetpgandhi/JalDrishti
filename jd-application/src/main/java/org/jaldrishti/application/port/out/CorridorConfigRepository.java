package org.jaldrishti.application.port.out;

import org.jaldrishti.domain.CorridorConfig;
import org.jaldrishti.domain.Site;
import org.jaldrishti.domain.Station;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for querying corridor metadata, configurations, stations, and sites.
 */
public interface CorridorConfigRepository {

    Optional<CorridorConfig> findCurrent(String corridorId);

    Optional<CorridorConfig> findByVersion(String corridorId, int version);

    List<Station> findStations(String corridorId);

    Optional<Station> findStationById(String stationId);

    List<Site> findSites(String corridorId);

    default List<Site> findSitesByCorridor(String corridorId) {
        return findSites(corridorId);
    }

    boolean isHeightenedWatchActive(String runId, String corridorId);

    void setHeightenedWatchActive(String runId, String corridorId, boolean active);

    double getCatchmentRainMm3h(String runId, String corridorId);
}
