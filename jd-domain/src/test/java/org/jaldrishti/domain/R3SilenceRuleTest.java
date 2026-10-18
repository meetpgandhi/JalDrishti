package org.jaldrishti.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class R3SilenceRuleTest {

    private final CorridorConfig config = CorridorConfig.defaultConfig();
    private final Station rsw = new Station(
            "RSW", "bhote-trishuli-narayani", "Rasuwagadhi", "Bhote Koshi",
            0.0, 28.2887, 85.5282, 6.0, null, 10, "vsat-rasuwa"
    );

    @Test
    @DisplayName("26 Aug unit test: RSW last reported 08:40, cadence 10m, grace 3m -> 08:53 sweep fires SILENT and internal WATCH")
    void rswGoesSilentAt0853Sweep() {
        // Nepal time: 08:40:00+05:45 is 02:55:00 UTC
        Instant lastGood = Instant.parse("2026-08-26T02:55:00Z"); // 08:40 NPT

        // 08:52:59 NPT - within grace (grace threshold is exactly 08:53:00 NPT)
        Instant sweep0852 = lastGood.plusSeconds(12 * 60); // +12 min
        RuleResult resultBefore = R3SilenceRule.evaluate(rsw, lastGood, sweep0852, config);
        assertThat(resultBefore.fired()).isFalse();

        // 08:53:01 NPT - 1 sec after 3-min grace expires
        Instant sweep0853 = lastGood.plusSeconds(13 * 60 + 1); // +13 min 1 sec
        RuleResult resultAfter = R3SilenceRule.evaluate(rsw, lastGood, sweep0853, config);

        assertThat(resultAfter.fired()).isTrue();
        assertThat(resultAfter.resultingLevel()).isEqualTo(IncidentLevel.WATCH);
        assertThat(resultAfter.ruleId()).isEqualTo("R3");
        assertThat(resultAfter.tag()).isEqualTo(AlertTag.D);
        assertThat(resultAfter.description()).contains("RSW is SILENT");
        assertThat(resultAfter.evidence().get("firstMissedSlotAt")).isEqualTo(lastGood.plusSeconds(600).toString());
    }

    @Test
    @DisplayName("Station with no last good reading does not fire silence alert")
    void stationWithNoReadingsDoesNotFire() {
        RuleResult result = R3SilenceRule.evaluate(rsw, null, Instant.now(), config);
        assertThat(result.fired()).isFalse();
    }
}
