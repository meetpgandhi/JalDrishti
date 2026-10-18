package org.jaldrishti.application.port.in;

/**
 * Inbound port for ingesting sensor readings into the trigger engine.
 */
public interface IngestReadingUseCase {

    void ingest(ReadingCommand command);
}
