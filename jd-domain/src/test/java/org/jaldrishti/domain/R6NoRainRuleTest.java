package org.jaldrishti.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class R6NoRainRuleTest {

    private final CorridorConfig config = CorridorConfig.defaultConfig();

    @Test
    @DisplayName("MLK 4m rise on dry morning (0mm rain in 3h) -> WARNING with NON_RAIN tag")
    void suddenRiseWithoutRainFiresNonRainWarning() {
        RuleResult r2Fired = RuleResult.fired(
                "R2", IncidentLevel.WATCH, AlertTag.D, "MLK rose 4.0m", Collections.emptyMap()
        );

        RuleResult result = R6NoRainRule.evaluate(r2Fired, 0.0, config);

        assertThat(result.fired()).isTrue();
        assertThat(result.resultingLevel()).isEqualTo(IncidentLevel.WARNING);
        assertThat(result.nonRain()).isTrue();
        assertThat(result.ruleId()).isEqualTo("R6");
        assertThat(result.tag()).isEqualTo(AlertTag.D);
    }

    @Test
    @DisplayName("Monsoon rise with 35mm rain in 3h (> 5mm threshold) -> does NOT tag as NON_RAIN")
    void normalRainRiseDoesNotTriggerNonRainRule() {
        RuleResult r2Fired = RuleResult.fired(
                "R2", IncidentLevel.WATCH, AlertTag.D, "DVG rose 0.8m", Collections.emptyMap()
        );

        RuleResult result = R6NoRainRule.evaluate(r2Fired, 35.0, config);

        assertThat(result.fired()).isFalse();
        assertThat(result.nonRain()).isFalse();
    }
}
