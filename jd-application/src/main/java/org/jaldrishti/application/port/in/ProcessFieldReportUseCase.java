package org.jaldrishti.application.port.in;

import org.jaldrishti.domain.FieldReportRecord;

/**
 * Inbound port for ingesting and processing unstructured field reports
 * via Gemini on Vertex AI.
 */
public interface ProcessFieldReportUseCase {

    /**
     * Ingests, identifies trust, parses via Gemini, validates against gazetteer,
     * and submits to trigger engine.
     *
     * @param command Inbound command containing raw text or audio URI
     * @return Stored field report record with parse status
     */
    FieldReportRecord processFieldReport(FieldReportCommand command);
}
