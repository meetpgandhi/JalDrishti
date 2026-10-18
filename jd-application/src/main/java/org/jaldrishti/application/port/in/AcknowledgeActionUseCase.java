package org.jaldrishti.application.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound port for duty holders acknowledging or declining dispatched actions.
 */
public interface AcknowledgeActionUseCase {

    /**
     * Records duty holder confirmation/acknowledgement of an assigned action.
     *
     * @param actionId Action UUID
     * @param ackData  Optional payload data (e.g., headcount, gate confirmation status)
     * @param when     Timestamp of receipt
     * @return Result of the operation
     */
    AckResult acknowledge(UUID actionId, String ackData, Instant when);

    /**
     * Records duty holder decline of an assigned action, immediately flagging to forecaster.
     *
     * @param actionId Action UUID
     * @param reason   Reason provided by duty holder
     * @param when     Timestamp of receipt
     * @return Result of the operation
     */
    AckResult decline(UUID actionId, String reason, Instant when);
}
