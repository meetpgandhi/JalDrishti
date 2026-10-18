package org.jaldrishti.sim.generator;

import org.jaldrishti.domain.AlertTag;

import java.time.Instant;

/**
 * Strategy interface for generating simulated sensor readings.
 */
public interface ReadingGenerator {

    record GeneratedValue(double levelM, AlertTag tag) {}

    GeneratedValue generate(Instant simTime, Instant simStartTime);
}
