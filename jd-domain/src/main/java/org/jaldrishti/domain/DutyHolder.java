package org.jaldrishti.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Duty-holder registered along the corridor.
 * Sensitive fields (phone numbers) are strictly encrypted at rest.
 */
public record DutyHolder(
        UUID id,
        ActorType actorType,
        String siteId,
        String displayName,
        byte[] phoneEnc,
        String telegramChatId,
        Language language,
        UUID deputyId
) {
    public DutyHolder {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        if (language == null) {
            language = Language.ne;
        }
    }
}
