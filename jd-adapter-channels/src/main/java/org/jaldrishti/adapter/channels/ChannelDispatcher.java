package org.jaldrishti.adapter.channels;

import org.jaldrishti.application.port.out.ChannelDispatchPort;
import org.jaldrishti.domain.DispatchedAction;
import org.jaldrishti.domain.DutyHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dispatcher implementation delivering alerts to Web Phone Simulator,
 * Telegram bot, SMS, and Voice endpoints.
 */
@Component
public class ChannelDispatcher implements ChannelDispatchPort {

    private static final Logger log = LoggerFactory.getLogger(ChannelDispatcher.class);

    public record PhoneMessage(
            UUID actionId,
            UUID dutyHolderId,
            String actorType,
            String actionCode,
            String text,
            String ackUrl,
            Instant sentAt
    ) {}

    // In-memory phone simulator store keyed by dutyHolderId
    private final Map<UUID, List<PhoneMessage>> phoneInboxes = new ConcurrentHashMap<>();

    @Override
    public void send(DispatchedAction action, DutyHolder holder, String messageText, String ackUrl) {
        log.info("Dispatching action {} ({}) to duty holder {} ({}) via channel: {}",
                action.id(), action.actionCode(), holder.id(), holder.displayName(),
                holder.telegramChatId() != null ? "TELEGRAM" : "WEB_PHONE_SIM");

        PhoneMessage msg = new PhoneMessage(
                action.id(),
                holder.id(),
                holder.actorType().name(),
                action.actionCode(),
                messageText,
                ackUrl,
                Instant.now()
        );

        phoneInboxes.computeIfAbsent(holder.id(), k -> Collections.synchronizedList(new ArrayList<>())).add(msg);

        // If telegram chat ID is set, log or forward
        if (holder.telegramChatId() != null && !holder.telegramChatId().isBlank()) {
            log.info("[Telegram Outbound] To {}: {} | Ack: {}", holder.telegramChatId(), messageText, ackUrl);
        }
    }

    public List<PhoneMessage> getInbox(UUID dutyHolderId) {
        return phoneInboxes.getOrDefault(dutyHolderId, Collections.emptyList());
    }

    public void clearInboxes() {
        phoneInboxes.clear();
    }
}
