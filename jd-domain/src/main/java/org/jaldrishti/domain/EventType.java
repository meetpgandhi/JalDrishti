package org.jaldrishti.domain;

/**
 * Event type for the append-only incident_event stream.
 */
public enum EventType {
    OPENED,
    LEVEL_CHANGED,
    EVIDENCE_ADDED,
    BANDS_UPDATED,
    STOOD_DOWN
}
