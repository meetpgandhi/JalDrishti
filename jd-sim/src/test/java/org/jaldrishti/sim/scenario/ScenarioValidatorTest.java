package org.jaldrishti.sim.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioValidatorTest {

    private final ScenarioValidator validator = new ScenarioValidator();

    @Test
    @DisplayName("Valid scenario passes validation")
    void validScenarioPasses() {
        Scenario scenario = new Scenario(
                "s1", 1, "bhote-trishuli-narayani", 1,
                "2026-08-26T08:00:00Z", "2026-08-26T16:00:00Z", 42,
                Map.of(), List.of(), List.of(),
                Map.of("RSW", new Scenario.StationConfig(
                        Map.of("model", "flat"),
                        List.of(new Scenario.PinnedReading("08:40", 1.62, "P", "src")),
                        null
                )),
                new Scenario.WeatherConfig(Map.of()),
                List.of(), List.of(), Map.of()
        );

        ScenarioValidator.ValidationResult result = validator.validate(scenario);
        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("Scenario violating mustStayBelow constraint is rejected")
    void violatingConstraintFails() {
        Scenario scenario = new Scenario(
                "s1", 1, "bhote-trishuli-narayani", 1,
                "2026-08-26T08:00:00Z", "2026-08-26T16:00:00Z", 42,
                Map.of(), List.of(), List.of(),
                Map.of("DVG", new Scenario.StationConfig(
                        Map.of("model", "hydrograph", "mustStayBelow", 7.3),
                        List.of(new Scenario.PinnedReading("16:00", 7.45, "P", "src")),
                        null
                )),
                new Scenario.WeatherConfig(Map.of()),
                List.of(), List.of(), Map.of()
        );

        ScenarioValidator.ValidationResult result = validator.validate(scenario);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(err -> err.contains("mustStayBelow threshold of 7.20m") || err.contains("mustStayBelow threshold of 7.30m"));
    }
}
