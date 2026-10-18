package org.jaldrishti.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class R10HeightenedWatchRuleTest {

    private final Station rsw = new Station(
            "RSW", "corridor", "Rasuwagadhi", "Bhote Koshi",
            0.0, 28.2887, 85.5282, 6.0, null, 10, "vsat-rasuwa"
    );

    private final Station btw = new Station(
            "BTW", "corridor", "Betrawati", "Trishuli",
            38.0, 27.974, 85.181, 4.1, null, 10, "gsm-nuwakot"
    );

    @Test
    @DisplayName("Heightened watch on top station RSW: RSW goes silent -> WARNING directly at next sweep")
    void heightenedWatchOnTopStationFiresWarningDirectly() {
        RuleResult result = R10HeightenedWatchRule.evaluate(true, rsw, true, true);

        assertThat(result.fired()).isTrue();
        assertThat(result.resultingLevel()).isEqualTo(IncidentLevel.WARNING);
        assertThat(result.ruleId()).isEqualTo("R10");
        assertThat(result.tag()).isEqualTo(AlertTag.D);
        assertThat(result.description()).contains("directly elevated to WARNING");
    }

    @Test
    @DisplayName("Heightened watch active but station is NOT top station -> does not fire direct warning")
    void nonTopStationDoesNotFireDirectWarning() {
        RuleResult result = R10HeightenedWatchRule.evaluate(true, btw, false, true);
        assertThat(result.fired()).isFalse();
    }

    @Test
    @DisplayName("Heightened watch NOT active -> does not fire")
    void heightenedWatchInactiveDoesNotFire() {
        RuleResult result = R10HeightenedWatchRule.evaluate(false, rsw, true, true);
        assertThat(result.fired()).isFalse();
    }
}
