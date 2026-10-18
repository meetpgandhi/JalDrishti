package org.jaldrishti.application.port.out;

import org.jaldrishti.domain.DispatchedAction;
import org.jaldrishti.domain.DutyHolder;

/**
 * Outbound port for dispatching formatted alerts to communication channels:
 * web phone simulator, Telegram bot, SMS, and voice.
 */
public interface ChannelDispatchPort {

    /**
     * Dispatches an alert message to the appropriate channel for the duty holder.
     *
     * @param action      The action record
     * @param holder      The duty holder recipient
     * @param messageText Localized directive text
     * @param ackUrl      HMAC-signed acknowledgement link
     */
    void send(DispatchedAction action, DutyHolder holder, String messageText, String ackUrl);
}
