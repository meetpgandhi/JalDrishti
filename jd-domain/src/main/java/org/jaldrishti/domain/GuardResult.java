package org.jaldrishti.domain;

/**
 * Result of the numeric safety guard check on localized Gemini output.
 */
public enum GuardResult {
    PASS,
    FAILED,
    SKIPPED
}
