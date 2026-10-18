package org.jaldrishti.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class R2RateRuleTest {

    private final CorridorConfig config = CorridorConfig.defaultConfig();

    @Test
    @DisplayName("Malekhu (MLK): 4.0m rise within 10 min window -> RATE_ALERT and WATCH")
    void malekhuRateOfRiseBreached() {
        Instant t0 = Instant.parse("2026-08-26T11:20:00Z");
        Instant t1 = Instant.parse("2026-08-26T11:30:00Z");

        Reading past = new Reading("test-run", "MLK", t0, 4.0, 0.0, t0, "DHM", AlertTag.P);
        Reading current = new Reading("test-run", "MLK", t1, 8.0, 0.0, t1, "DHM", AlertTag.P);

        RuleResult result = R2RateRule.evaluate(current, List.of(past), config, false);

        assertThat(result.fired()).isTrue();
        assertThat(result.resultingLevel()).isEqualTo(IncidentLevel.WATCH);
        assertThat(result.ruleId()).isEqualTo("R2");
        assertThat(result.description()).contains("rose 4.00m in 10 min");
    }

    @Test
    @DisplayName("Rise below 0.5m in 10 min -> does not fire")
    void normalRiseDoesNotFire() {
        Instant t0 = Instant.parse("2026-08-26T11:20:00Z");
        Instant t1 = Instant.parse("2026-08-26T11:30:00Z");

        Reading past = new Reading("test-run", "MLK", t0, 4.0, 0.0, t0, "DHM", AlertTag.P);
        Reading current = new Reading("test-run", "MLK", t1, 4.3, 0.0, t1, "DHM", AlertTag.P);

        RuleResult result = R2RateRule.evaluate(current, List.of(past), config, false);

        assertThat(result.fired()).isFalse();
    }

    @Test
    @DisplayName("Heightened watch active: 0.3m rise in 10 min fires (threshold halved to 0.25m)")
    void heightenedWatchHalvesThreshold() {
        Instant t0 = Instant.parse("2026-08-26T11:20:00Z");
        Instant t1 = Instant.parse("2026-08-26T11:30:00Z");

        Reading past = new Reading("test-run", "MLK", t0, 4.0, 0.0, t0, "DHM", AlertTag.P);
        Reading current = new Reading("test-run", "MLK", t1, 4.3, 0.0, t1, "DHM", AlertTag.P);

        RuleResult result = R2RateRule.evaluate(current, List.of(past), config, true);

        assertThat(result.fired()).isTrue();
        assertThat(result.description()).contains("0.25m");
    }
}
