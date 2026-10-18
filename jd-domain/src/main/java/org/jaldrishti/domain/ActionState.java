package org.jaldrishti.domain;

/**
 * Lifecycle states of an action dispatched to a duty holder.
 */
public enum ActionState {
    SENT,
    DELIVERED,
    ACKED,
    DECLINED,
    ESCALATED_1,
    ESCALATED_2,
    ESCALATED_3,
    UNRESOLVED;

    public boolean isTerminal() {
        return this == ACKED || this == DECLINED || this == UNRESOLVED;
    }
}
