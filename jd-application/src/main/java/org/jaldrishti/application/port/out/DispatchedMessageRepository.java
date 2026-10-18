package org.jaldrishti.application.port.out;

import org.jaldrishti.domain.DispatchedMessageRecord;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port for recording dispatched message log entries.
 */
public interface DispatchedMessageRepository {

    void save(DispatchedMessageRecord message);

    List<DispatchedMessageRecord> findByActionId(UUID actionId);
}
