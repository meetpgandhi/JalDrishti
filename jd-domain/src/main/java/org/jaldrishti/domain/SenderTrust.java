package org.jaldrishti.domain;

/**
 * Trust level of a field report sender.
 *
 * <ul>
 *   <li>{@code OFFICIAL} — Registered duty-holder; one credible report fires WARNING (Rule R7).</li>
 *   <li>{@code OBSERVER} — Registered gauge reader, school, or community observer; two independent reports or one plus sensor evidence fires WARNING (Rule R8).</li>
 *   <li>{@code PUBLIC} — Unregistered member of the public; shown in feed as corroboration.</li>
 * </ul>
 */
public enum SenderTrust {
    OFFICIAL,
    OBSERVER,
    PUBLIC
}
