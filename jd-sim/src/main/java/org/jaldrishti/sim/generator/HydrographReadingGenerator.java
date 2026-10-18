package org.jaldrishti.sim.generator;

import org.jaldrishti.domain.AlertTag;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Hydrograph reading generator: baseline level followed by rapid rising limb to peak,
 * and exponential recession back to base.
 */
public class HydrographReadingGenerator implements ReadingGenerator {

    private final double base;
    private final Instant jumpAt;
    private final double jumpM;
    private final double peakAfterMin;
    private final double recessionHalfLifeMin;
    private final Double mustStayBelow;
    private final AlertTag tag;

    public HydrographReadingGenerator(
            double base,
            Instant jumpAt,
            double jumpM,
            double peakAfterMin,
            double recessionHalfLifeMin,
            Double mustStayBelow,
            AlertTag tag
    ) {
        this.base = base;
        this.jumpAt = Objects.requireNonNull(jumpAt, "jumpAt must not be null");
        this.jumpM = jumpM;
        this.peakAfterMin = Math.max(1.0, peakAfterMin);
        this.recessionHalfLifeMin = Math.max(1.0, recessionHalfLifeMin);
        this.mustStayBelow = mustStayBelow;
        this.tag = tag != null ? tag : AlertTag.A;
    }

    @Override
    public GeneratedValue generate(Instant simTime, Instant simStartTime) {
        if (simTime.isBefore(jumpAt)) {
            return new GeneratedValue(base, tag);
        }

        double minutesSinceJump = Duration.between(jumpAt, simTime).toSeconds() / 60.0;
        double level;

        if (minutesSinceJump <= peakAfterMin) {
            // Linear or sinusoidal rising limb
            double fraction = minutesSinceJump / peakAfterMin;
            level = base + jumpM * fraction;
        } else {
            // Exponential recession limb
            double minutesSincePeak = minutesSinceJump - peakAfterMin;
            double decayRate = Math.log(2.0) / recessionHalfLifeMin;
            double surplus = jumpM * Math.exp(-decayRate * minutesSincePeak);
            level = base + surplus;
        }

        if (mustStayBelow != null && level >= mustStayBelow) {
            level = mustStayBelow - 0.01;
        }

        level = Math.round(level * 100.0) / 100.0;
        return new GeneratedValue(level, tag);
    }
}
