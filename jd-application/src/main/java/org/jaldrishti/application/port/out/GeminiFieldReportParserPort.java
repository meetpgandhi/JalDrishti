package org.jaldrishti.application.port.out;

import org.jaldrishti.domain.FieldReportObservation;
import org.jaldrishti.domain.FieldReportRecord;
import org.jaldrishti.domain.Site;

import java.util.List;

/**
 * Outbound port for invoking Gemini on Vertex AI to extract structured
 * flood observations from inbound voice audio or raw text.
 */
public interface GeminiFieldReportParserPort {

    /**
     * Parses an inbound field report against the corridor gazetteer.
     *
     * @param report    Raw report record
     * @param gazetteer List of known sites along the corridor
     * @return Extracted observation
     */
    FieldReportObservation parse(FieldReportRecord report, List<Site> gazetteer);
}
