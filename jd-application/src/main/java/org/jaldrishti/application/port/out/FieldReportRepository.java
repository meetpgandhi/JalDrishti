package org.jaldrishti.application.port.out;

import org.jaldrishti.domain.FieldReportRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for field report persistence.
 */
public interface FieldReportRepository {

    void save(FieldReportRecord report);

    Optional<FieldReportRecord> findById(UUID id);

    List<FieldReportRecord> findByRunId(String runId);
}
