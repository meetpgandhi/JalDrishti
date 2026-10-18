package org.jaldrishti.sim.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validates scenario definitions against engineering and physical constraints.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Never contradicts a published [P] data point.</li>
 *   <li>Never generates a reading crossing a threshold the historical record states was uncrossed.</li>
 * </ul>
 */
public class ScenarioValidator {

    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, List.copyOf(errors));
        }
    }

    public ValidationResult validate(Scenario scenario) {
        Objects.requireNonNull(scenario, "scenario must not be null");
        List<String> errors = new ArrayList<>();

        if (scenario.scenario() == null || scenario.scenario().isBlank()) {
            errors.add("Scenario ID is required");
        }
        if (scenario.corridor() == null || scenario.corridor().isBlank()) {
            errors.add("Corridor ID is required");
        }
        if (scenario.start() == null || scenario.end() == null) {
            errors.add("Start and end timestamps are required");
        }

        for (Map.Entry<String, Scenario.StationConfig> entry : scenario.stations().entrySet()) {
            String stationId = entry.getKey();
            Scenario.StationConfig st = entry.getValue();

            // Check pinned readings
            if (st.pinned() != null) {
                for (Scenario.PinnedReading pinned : st.pinned()) {
                    if (pinned.level() < 0) {
                        errors.add(String.format("Station %s pinned reading at %s has negative level: %.2f",
                                stationId, pinned.t(), pinned.level()));
                    }
                }
            }

            // Check mustStayBelow constraints
            if (st.generator() != null && st.generator().containsKey("mustStayBelow")) {
                Object limitObj = st.generator().get("mustStayBelow");
                if (limitObj instanceof Number limit) {
                    double maxAllowed = limit.doubleValue();
                    if (st.pinned() != null) {
                        for (Scenario.PinnedReading pinned : st.pinned()) {
                            if (pinned.level() >= maxAllowed) {
                                errors.add(String.format(
                                        "Contradiction: Station %s pinned reading %.2fm at %s violates mustStayBelow threshold of %.2fm [P]",
                                        stationId, pinned.level(), pinned.t(), maxAllowed
                                ));
                            }
                        }
                    }
                }
            }
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }
}
