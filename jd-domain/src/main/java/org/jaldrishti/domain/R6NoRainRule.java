package org.jaldrishti.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Rule R6 — Rate Without Rain.
 *
 * <p>Fires when Rule R2 rate-of-rise fires while catchment rainfall remains below the threshold
 * (default: &lt; 5.0 mm in 3 hours [A]).
 *
 * <p>Result: WARNING tagged with {@code NON_RAIN} (indicates outburst flood, landslide dam breach,
 * or glacial lake outburst).
 */
public final class R6NoRainRule {

    public static final String RULE_ID = "R6";

    private R6NoRainRule() {}

    public static RuleResult evaluate(
            RuleResult r2Result,
            double catchmentRainMm3h,
            CorridorConfig config
    ) {
        Objects.requireNonNull(r2Result, "r2Result must not be null");
        Objects.requireNonNull(config, "CorridorConfig must not be null");

        if (!r2Result.fired()) {
            return RuleResult.notFired(RULE_ID, "Rule R2 rate of rise did not fire");
        }

        double threshold = config.noRainThresholdMm3h();
        if (catchmentRainMm3h < threshold) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("r2Evidence", r2Result.evidence());
            evidence.put("catchmentRainMm3h", catchmentRainMm3h);
            evidence.put("thresholdMm3h", threshold);

            return RuleResult.firedNonRain(
                    RULE_ID,
                    IncidentLevel.WARNING,
                    AlertTag.D,
                    String.format("Sudden river rise without rainfall: R2 fired with catchment rain of %.1fmm in 3h (< threshold %.1fmm)",
                            catchmentRainMm3h, threshold),
                    evidence
            );
        }

        return RuleResult.notFired(
                RULE_ID,
                String.format("Catchment rain of %.1fmm in 3h meets or exceeds non-rain threshold of %.1fmm",
                        catchmentRainMm3h, threshold)
        );
    }
}
