/**
 * JalDrishti Adapter — Common Alerting Protocol (CAP 1.2).
 *
 * <p>Builds OASIS CAP 1.2 XML for every WARNING and STAND_DOWN, and sends it
 * to the NDRRMA national gateway endpoint.
 *
 * <p>CRITICAL: Simulator runs ALWAYS use {@code <status>Exercise</status>}.
 * Never send {@code Actual} status from a simulation run.
 */
package org.jaldrishti.adapter.cap;
