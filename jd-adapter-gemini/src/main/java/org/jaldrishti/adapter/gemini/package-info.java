/**
 * JalDrishti Adapter — Vertex AI Gemini.
 *
 * <p>Three responsibilities:
 * <ol>
 *   <li>Field-report parsing: turns audio/text into {@code FieldObservation} JSON
 *       (temperature 0, structured output, gazetteer in system prompt)</li>
 *   <li>Message localisation: writes duty-holder messages in ne/hi/en
 *       (temperature 0, numeric guard always runs after)</li>
 *   <li>Incident assistant: answers forecaster questions via read-only tools (FR-18)</li>
 * </ol>
 *
 * <p>Gemini NEVER sets alert levels or evaluates trigger rules.
 * On any timeout or guard failure, reviewed templates are used instead.
 */
package org.jaldrishti.adapter.gemini;
