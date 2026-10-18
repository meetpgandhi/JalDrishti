package org.jaldrishti.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Audit replay report proving deterministic scenario execution,
 * physical wave arrival accuracy, and time saved ahead of as-happened response.
 */
public record ReplayReport(
        String runId,
        String corridorId,
        String scenario,
        Instant generatedAt,
        boolean primaryClaimVerified,
        String warningTimeNpt,
        String asHappenedPhoneCallTimeNpt,
        long minutesSaved,
        List<TimelineEvent> decisionTimeline,
        Map<String, Integer> provenanceCounts,
        Map<String, Object> actionsSummary,
        String deterministicSha256
) {
    public record TimelineEvent(
            long seq,
            String timeNpt,
            String eventType,
            String ruleId,
            String description,
            AlertTag tag
    ) {}
}
