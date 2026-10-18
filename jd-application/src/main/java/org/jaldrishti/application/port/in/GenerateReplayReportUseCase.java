package org.jaldrishti.application.port.in;

import org.jaldrishti.domain.ReplayReport;

/**
 * Inbound port to assemble and produce the post-incident audit and replay report (FR-17).
 */
public interface GenerateReplayReportUseCase {

    /**
     * Generates a deterministic replay report for a given simulation run.
     *
     * @param runId        Simulation run identifier or "live"
     * @param scenarioName Name of scenario replayed
     * @return Audit replay report
     */
    ReplayReport generateReport(String runId, String scenarioName);
}
