package org.jaldrishti.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class R5CascadeRuleTest {

    private final CorridorConfig config = CorridorConfig.defaultConfig();

    private final Station rsw = new Station(
            "RSW", "bhote-trishuli-narayani", "Rasuwagadhi", "Bhote Koshi",
            0.0, 28.2887, 85.5282, 6.0, null, 10, "vsat-rasuwa"
    );

    private final Station syp = new Station(
            "SYP", "bhote-trishuli-narayani", "Syaphrubesi", "Bhote Koshi",
            15.0, 28.168, 85.371, 5.5, null, 10, "vsat-rasuwa"
    );

    @Test
    @DisplayName("26 Aug worked example: RSW lost 08:40-08:50 + SYP lost 08:40-08:50 (15km downstream) -> R5 fires WARNING")
    void august26WorkedExampleFiresWarning() {
        // 08:40:00 NPT = 02:55:00 UTC
        // 08:50:00 NPT = 03:05:00 UTC
        Instant rswLastGood = Instant.parse("2026-08-26T02:55:00Z");
        Instant rswFirstMissed = Instant.parse("2026-08-26T03:05:00Z");

        Instant sypLastGood = Instant.parse("2026-08-26T02:55:00Z");
        Instant sypFirstMissed = Instant.parse("2026-08-26T03:05:00Z");

        StationObservationState sypState = new StationObservationState(
                "aug26-run", "SYP", StationState.SILENT,
                false, null, sypLastGood, sypFirstMissed,
                true, sypFirstMissed.plusSeconds(180), false, null, 3.80, 0.0
        );

        RuleResult result = R5CascadeRule.evaluate(rsw, rswLastGood, rswFirstMissed, syp, sypState, config);

        assertThat(result.fired()).isTrue();
        assertThat(result.resultingLevel()).isEqualTo(IncidentLevel.WARNING);
        assertThat(result.ruleId()).isEqualTo("R5");
        assertThat(result.tag()).isEqualTo(AlertTag.D);
        assertThat(result.evidence().get("downstreamEvidenceType")).isEqualTo("SILENCE");
    }

    @Test
    @DisplayName("Downstream station has RATE_ALERT inside order-consistency window -> R5 fires WARNING")
    void downstreamRateAlertInsideWindowFiresWarning() {
        Instant rswLastGood = Instant.parse("2026-08-26T02:55:00Z");   // 08:40
        Instant rswFirstMissed = Instant.parse("2026-08-26T03:05:00Z"); // 08:50

        // Window for 15km: [08:40 + 7m08s, 08:50 + 83m20s] -> [08:47:08, 10:13:20]
        // Downstream rate alert at 09:00 (03:15:00 UTC) is inside [08:47, 10:13]
        Instant rateAlertAt = Instant.parse("2026-08-26T03:15:00Z");

        StationObservationState sypState = new StationObservationState(
                "aug26-run", "SYP", StationState.RATE_ALERT,
                true, rateAlertAt, rateAlertAt, null,
                false, null, false, null, 6.0, 0.0
        );

        RuleResult result = R5CascadeRule.evaluate(rsw, rswLastGood, rswFirstMissed, syp, sypState, config);

        assertThat(result.fired()).isTrue();
        assertThat(result.resultingLevel()).isEqualTo(IncidentLevel.WARNING);
        assertThat(result.evidence().get("downstreamEvidenceType")).isEqualTo("RATE_ALERT");
    }

    @Test
    @DisplayName("Downstream evidence too early (implied speed > 35 m/s) -> does not fire")
    void downstreamTooEarlyDoesNotFire() {
        Instant rswLastGood = Instant.parse("2026-08-26T02:55:00Z");   // 08:40
        Instant rswFirstMissed = Instant.parse("2026-08-26T03:05:00Z"); // 08:50

        // Earliest possible arrival at 15km is 08:47:08.
        // Rate alert at 08:42 implies 15000m / 120s = 125 m/s (> 35 m/s max)
        Instant tooEarly = Instant.parse("2026-08-26T02:57:00Z"); // 08:42

        StationObservationState sypState = new StationObservationState(
                "aug26-run", "SYP", StationState.RATE_ALERT,
                true, tooEarly, tooEarly, null,
                false, null, false, null, 6.0, 0.0
        );

        RuleResult result = R5CascadeRule.evaluate(rsw, rswLastGood, rswFirstMissed, syp, sypState, config);

        assertThat(result.fired()).isFalse();
    }

    @Test
    @DisplayName("Downstream evidence too late (implied speed < 3 m/s) -> does not fire")
    void downstreamTooLateDoesNotFire() {
        Instant rswLastGood = Instant.parse("2026-08-26T02:55:00Z");   // 08:40
        Instant rswFirstMissed = Instant.parse("2026-08-26T03:05:00Z"); // 08:50

        // Latest possible arrival is 10:13:20 (04:28:20 UTC).
        // Rate alert at 10:30 (04:45:00 UTC) implies speed < 3 m/s
        Instant tooLate = Instant.parse("2026-08-26T04:45:00Z"); // 10:30

        StationObservationState sypState = new StationObservationState(
                "aug26-run", "SYP", StationState.RATE_ALERT,
                true, tooLate, tooLate, null,
                false, null, false, null, 6.0, 0.0
        );

        RuleResult result = R5CascadeRule.evaluate(rsw, rswLastGood, rswFirstMissed, syp, sypState, config);

        assertThat(result.fired()).isFalse();
    }

    @Test
    @DisplayName("Reversed river order (j upstream of i) -> does not fire")
    void reversedRiverOrderDoesNotFire() {
        Instant sypLastGood = Instant.parse("2026-08-26T02:55:00Z");
        Instant sypFirstMissed = Instant.parse("2026-08-26T03:05:00Z");

        StationObservationState rswState = new StationObservationState(
                "aug26-run", "RSW", StationState.SILENT,
                false, null, sypLastGood, sypFirstMissed,
                true, sypFirstMissed, false, null, 1.62, 0.0
        );

        // Attempting to evaluate SYP (upstream km 15) to RSW (downstream km 0)
        RuleResult result = R5CascadeRule.evaluate(syp, sypLastGood, sypFirstMissed, rsw, rswState, config);

        assertThat(result.fired()).isFalse();
        assertThat(result.description()).contains("not downstream");
    }
}
