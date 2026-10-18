package org.jaldrishti.application.port.in;

import org.jaldrishti.domain.FieldReportObservation;

/**
 * Inbound port for ingesting field reports into the trigger engine.
 */
public interface IngestFieldReportUseCase {

    void ingest(FieldReportCommand command);

    void handleFieldReport(FieldReportObservation report);
}
