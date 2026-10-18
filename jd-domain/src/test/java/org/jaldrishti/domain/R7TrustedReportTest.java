package org.jaldrishti.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class R7TrustedReportTest {

    private final CorridorConfig config = CorridorConfig.defaultConfig();

    @Test
    @DisplayName("26 Aug unit test: Nepali voice note from registered OFFICIAL -> SYB DEBRIS_FLOOD, WARNING [P]")
    void officialReportFiresWarning() {
        FieldReportObservation report = new FieldReportObservation(
                UUID.randomUUID(),
                "aug26-run",
                Instant.parse("2026-08-26T03:15:00Z"), // 09:00 NPT
                "hash-dao-dhunche",
                SenderTrust.OFFICIAL,
                "telegram",
                Language.ne,
                "रसुवागढीतिर भोटेकोसीमा ठूलो बाढी आयो।",
                "A huge flood has come down the Bhote Koshi towards Rasuwagadhi.",
                "SYB",
                "Syaphrubesi",
                HazardType.DEBRIS_FLOOD,
                List.of(EvidenceType.ROARING_SOUND, EvidenceType.FAST_RISE),
                Instant.parse("2026-08-26T03:15:00Z"),
                null,
                0.92,
                false
        );

        RuleResult result = R7TrustedReportRule.evaluate(report, config);

        assertThat(result.fired()).isTrue();
        assertThat(result.resultingLevel()).isEqualTo(IncidentLevel.WARNING);
        assertThat(result.ruleId()).isEqualTo("R7");
        assertThat(result.tag()).isEqualTo(AlertTag.P);
        assertThat(result.description()).contains("DEBRIS_FLOOD at SYB");
    }

    @Test
    @DisplayName("Observer sender report does NOT fire R7 (requires OFFICIAL)")
    void observerReportDoesNotFireR7() {
        FieldReportObservation report = new FieldReportObservation(
                UUID.randomUUID(),
                "test-run",
                Instant.now(),
                "hash-observer",
                SenderTrust.OBSERVER,
                "telegram",
                Language.ne,
                "बाढी आयो",
                "Flood came",
                "SYB",
                null,
                HazardType.DEBRIS_FLOOD,
                List.of(EvidenceType.FAST_RISE),
                Instant.now(),
                null,
                0.85,
                false
        );

        RuleResult result = R7TrustedReportRule.evaluate(report, config);
        assertThat(result.fired()).isFalse();
    }

    @Test
    @DisplayName("Low confidence (<0.7) official report does NOT fire R7")
    void lowConfidenceReportDoesNotFire() {
        FieldReportObservation report = new FieldReportObservation(
                UUID.randomUUID(),
                "test-run",
                Instant.now(),
                "hash-dao",
                SenderTrust.OFFICIAL,
                "telegram",
                Language.ne,
                "audio unclear",
                "unclear",
                "SYB",
                null,
                HazardType.DEBRIS_FLOOD,
                List.of(),
                Instant.now(),
                null,
                0.45,
                true
        );

        RuleResult result = R7TrustedReportRule.evaluate(report, config);
        assertThat(result.fired()).isFalse();
    }
}
