package org.jaldrishti.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class R9SeismicRuleTest {

    @Test
    @DisplayName("26 Aug seismic event: M4.4 at (28.2887, 85.5282) in headwater polygon -> opens WATCH")
    void august26SeismicEventOpensWatch() {
        SeismicObservation obs = new SeismicObservation(
                "seis-01", "aug26-run",
                Instant.parse("2026-08-26T02:52:00Z"), // 08:37 NPT
                Instant.parse("2026-08-26T03:07:00Z"),
                4.4, 28.2887, 85.5282, "USGS"
        );

        RuleResult result = R9SeismicRule.evaluate(obs, false);

        assertThat(result.fired()).isTrue();
        assertThat(result.resultingLevel()).isEqualTo(IncidentLevel.WATCH);
        assertThat(result.ruleId()).isEqualTo("R9");
        assertThat(result.tag()).isEqualTo(AlertTag.D);
    }

    @Test
    @DisplayName("Seismic event M4.4 in polygon corroborated by R3 within 30 min -> elevated to WARNING")
    void seismicCorroboratedByR3ElevatesToWarning() {
        SeismicObservation obs = new SeismicObservation(
                "seis-01", "aug26-run",
                Instant.parse("2026-08-26T02:52:00Z"),
                Instant.parse("2026-08-26T03:07:00Z"),
                4.4, 28.2887, 85.5282, "USGS"
        );

        RuleResult result = R9SeismicRule.evaluate(obs, true);

        assertThat(result.fired()).isTrue();
        assertThat(result.resultingLevel()).isEqualTo(IncidentLevel.WARNING);
    }

    @Test
    @DisplayName("Seismic magnitude below 3.5 -> does not fire")
    void smallSeismicDoesNotFire() {
        SeismicObservation obs = new SeismicObservation(
                "seis-02", "test-run",
                Instant.now(), Instant.now(),
                3.1, 28.2887, 85.5282, "USGS"
        );

        RuleResult result = R9SeismicRule.evaluate(obs, false);
        assertThat(result.fired()).isFalse();
    }
}
