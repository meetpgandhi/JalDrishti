package org.jaldrishti.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class R4OutageRuleTest {

    private final CorridorConfig config = CorridorConfig.defaultConfig();

    @Test
    @DisplayName("6 of 10 stations on one carrier silent (>50%) -> suppresses R3, opens outage ticket, zero WARNINGs")
    void carrierOutageSuppressesAlerts() {
        List<StationObservationState> group = new ArrayList<>();
        Instant now = Instant.parse("2026-08-26T02:10:00Z");

        // 6 silent stations
        for (int i = 1; i <= 6; i++) {
            group.add(new StationObservationState(
                    "test-run", "STATION-" + i, StationState.SILENT,
                    false, null, now.minusSeconds(600), now,
                    true, now, false, null, 1.5, 0.0
            ));
        }

        // 4 healthy stations
        for (int i = 7; i <= 10; i++) {
            group.add(new StationObservationState(
                    "test-run", "STATION-" + i, StationState.OK,
                    false, null, now, null,
                    false, null, false, null, 1.5, 0.0
            ));
        }

        RuleResult result = R4OutageRule.evaluate("carrier-alpha", group, config, now);

        assertThat(result.fired()).isTrue();
        assertThat(result.suppressesSilence()).isTrue();
        assertThat(result.resultingLevel()).isNull(); // Zero WARNINGs / Zero WATCHes
        assertThat(result.evidence().get("fractionSilent")).isEqualTo(0.6);
        assertThat(result.evidence().get("outageTicket").toString()).contains("TICKET-OUTAGE-carrier-alpha");
    }

    @Test
    @DisplayName("4 of 10 stations on one carrier silent (<=50%) -> does not suppress")
    void partialSilenceDoesNotTriggerOutageFilter() {
        List<StationObservationState> group = new ArrayList<>();
        Instant now = Instant.parse("2026-08-26T02:10:00Z");

        // 4 silent stations
        for (int i = 1; i <= 4; i++) {
            group.add(new StationObservationState(
                    "test-run", "STATION-" + i, StationState.SILENT,
                    false, null, now.minusSeconds(600), now,
                    true, now, false, null, 1.5, 0.0
            ));
        }
        // 6 healthy stations
        for (int i = 5; i <= 10; i++) {
            group.add(new StationObservationState(
                    "test-run", "STATION-" + i, StationState.OK,
                    false, null, now, null,
                    false, null, false, null, 1.5, 0.0
            ));
        }

        RuleResult result = R4OutageRule.evaluate("carrier-alpha", group, config, now);

        assertThat(result.fired()).isFalse();
        assertThat(result.suppressesSilence()).isFalse();
    }
}
