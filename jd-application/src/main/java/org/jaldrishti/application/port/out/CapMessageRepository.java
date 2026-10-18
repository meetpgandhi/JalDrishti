package org.jaldrishti.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbound port for recording generated CAP XML alerts.
 */
public interface CapMessageRepository {

    void save(String identifier, UUID incidentId, String msgType, String status, String xml, Instant sentAt, String gatewayResponse);

    List<String> findXmlByIncidentId(UUID incidentId);
}
