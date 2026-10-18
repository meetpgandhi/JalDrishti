package org.jaldrishti.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class R1HeightRuleTest {

    @Test
    @DisplayName("Devghat (DVG): reading 7.31m crosses 7.3m warning threshold -> AMBER and WATCH")
    void devghatCrossesWarningThreshold() {
        Station dvg = new Station(
                "DVG", "bhote-trishuli-narayani", "Devghat", "Narayani",
                168.0, 27.706, 84.437, 7.3, 9.0, 10, "gsm-chitwan"
        );

        Reading reading = new Reading(
                "test-run", "DVG", Instant.parse("2026-08-26T15:00:00Z"),
                7.31, 2.0, Instant.parse("2026-08-26T15:00:05Z"),
                "DHM", AlertTag.P
        );

        RuleResult result = R1HeightRule.evaluate(reading, dvg);
        StationState band = R1HeightRule.determineBand(reading, dvg);

        assertThat(result.fired()).isTrue();
        assertThat(result.resultingLevel()).isEqualTo(IncidentLevel.WATCH);
        assertThat(result.ruleId()).isEqualTo("R1");
        assertThat(result.tag()).isEqualTo(AlertTag.P);
        assertThat(band).isEqualTo(StationState.AMBER);
    }

    @Test
    @DisplayName("Reading exceeds danger threshold -> RED and direct WARNING")
    void readingExceedsDangerThreshold() {
        Station dvg = new Station(
                "DVG", "bhote-trishuli-narayani", "Devghat", "Narayani",
                168.0, 27.706, 84.437, 7.3, 9.0, 10, "gsm-chitwan"
        );

        Reading reading = new Reading(
                "test-run", "DVG", Instant.parse("2026-08-26T15:10:00Z"),
                9.15, 5.0, Instant.parse("2026-08-26T15:10:05Z"),
                "DHM", AlertTag.P
        );

        RuleResult result = R1HeightRule.evaluate(reading, dvg);
        StationState band = R1HeightRule.determineBand(reading, dvg);

        assertThat(result.fired()).isTrue();
        assertThat(result.resultingLevel()).isEqualTo(IncidentLevel.WARNING);
        assertThat(band).isEqualTo(StationState.RED);
    }

    @Test
    @DisplayName("Reading below warning threshold -> OK and does not fire")
    void readingBelowWarningThreshold() {
        Station dvg = new Station(
                "DVG", "bhote-trishuli-narayani", "Devghat", "Narayani",
                168.0, 27.706, 84.437, 7.3, 9.0, 10, "gsm-chitwan"
        );

        Reading reading = new Reading(
                "test-run", "DVG", Instant.parse("2026-08-26T14:50:00Z"),
                6.57, 0.0, Instant.parse("2026-08-26T14:50:05Z"),
                "DHM", AlertTag.P
        );

        RuleResult result = R1HeightRule.evaluate(reading, dvg);
        StationState band = R1HeightRule.determineBand(reading, dvg);

        assertThat(result.fired()).isFalse();
        assertThat(band).isEqualTo(StationState.OK);
    }
}
