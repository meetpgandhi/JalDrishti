package org.jaldrishti.domain;

/**
 * Provenance tag attached to every reading, calculation, and event.
 *
 * <ul>
 *   <li>{@code P} — Published source (green badge)</li>
 *   <li>{@code D} — Derived calculation from published points (blue badge)</li>
 *   <li>{@code A} — Assumption / design default (amber badge)</li>
 * </ul>
 */
public enum AlertTag {
    P,
    D,
    A
}
