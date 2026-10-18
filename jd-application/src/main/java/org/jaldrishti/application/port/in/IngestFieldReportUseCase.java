package org.jaldrishti.application.port.in;

/**
 * Inbound port for ingesting field reports into the trigger engine.
 */
public interface IngestFieldReportUseCase {

    void ingest(FieldReportCommand command);
}
