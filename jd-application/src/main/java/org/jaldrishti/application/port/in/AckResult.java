package org.jaldrishti.application.port.in;

import org.jaldrishti.domain.ActionState;

import java.util.UUID;

/**
 * Result of an action acknowledgement or decline.
 */
public record AckResult(
        boolean success,
        UUID actionId,
        ActionState newState,
        String message
) {}
