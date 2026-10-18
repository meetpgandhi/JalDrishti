package org.jaldrishti.application.port.out;

import org.jaldrishti.domain.Reading;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying hydrological readings.
 *
 * <p>Insert must be idempotent: {@code ON CONFLICT (run_id, station_id, observed_at) DO NOTHING}.
 */
public interface ReadingRepository {

    void save(Reading reading);

    List<Reading> findRecent(String runId, String stationId, Instant since);

    Optional<Reading> findLatest(String runId, String stationId);
}
