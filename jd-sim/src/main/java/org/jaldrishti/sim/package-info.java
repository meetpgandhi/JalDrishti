/**
 * JalDrishti Simulation Engine.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@code SimClock} — injectable {@code java.time.Clock} that runs at configurable
 *       speed (1× to 240×); state persisted to {@code sim_run} table for restart safety</li>
 *   <li>Scenario loader and validator — loads YAML from {@code scenarios/}; rejects
 *       files contradicting published [P] data points</li>
 *   <li>Generator models: flat, hydrograph, outage</li>
 *   <li>Live-injection API — DESTROY_GAUGE, RESTORE_GAUGE, SET_LEVEL, RAIN,
 *       REPORT, TELEMETRY_OUTAGE, HEIGHTENED_WATCH</li>
 *   <li>Replay report generator (FR-17)</li>
 * </ul>
 *
 * <p>The simulator publishes to the same Pub/Sub topics as the live DHM adapter.
 * Nothing in the trigger engine is special-cased for simulation.
 */
package org.jaldrishti.sim;
