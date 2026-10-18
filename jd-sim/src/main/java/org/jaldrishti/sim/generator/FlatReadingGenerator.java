package org.jaldrishti.sim.generator;

import org.jaldrishti.domain.AlertTag;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

/**
 * Flat reading generator: monsoon baseline water level with linear trend and seeded Gaussian noise.
 */
public class FlatReadingGenerator implements ReadingGenerator {

    private final double baseLevel;
    private final double trendPer10Min;
    private final double noiseSd;
    private final AlertTag tag;
    private final Random random;

    public FlatReadingGenerator(double baseLevel, double trendPer10Min, double noiseSd, AlertTag tag, long seed) {
        this.baseLevel = baseLevel;
        this.trendPer10Min = trendPer10Min;
        this.noiseSd = noiseSd;
        this.tag = tag != null ? tag : AlertTag.A;
        this.random = new Random(seed);
    }

    @Override
    public GeneratedValue generate(Instant simTime, Instant simStartTime) {
        long minutes = simStartTime != null ? Math.max(0, Duration.between(simStartTime, simTime).toMinutes()) : 0;
        double trend = (minutes / 10.0) * trendPer10Min;
        double noise = noiseSd > 0 ? random.nextGaussian() * noiseSd : 0.0;
        double level = Math.round((baseLevel + trend + noise) * 100.0) / 100.0;
        return new GeneratedValue(level, tag);
    }
}
