package org.jaldrishti.adapter.persistence;

import org.jaldrishti.application.port.out.MessageLogQuery;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * jOOQ implementation of MessageLogQuery (plain SQL joins; read-only).
 */
@Repository
public class JooqMessageLogQuery implements MessageLogQuery {

    private static final String DEPUTY_PREFIX = "[DEPUTY ESCALATION]";

    private final DSLContext dsl;

    public JooqMessageLogQuery(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext must not be null");
    }

    @Override
    public List<MessageLogView> findMessagesByRunId(String runId) {
        return dsl.fetch("""
                SELECT m.id AS message_id, m.channel, m.language, m.text, m.sent_at AS message_sent_at,
                       a.id AS action_id, a.incident_id, a.action_code, a.deadline_at, a.state,
                       a.escalation_step, a.acked_at,
                       p.id AS principal_id, p.display_name AS principal_name,
                       p.actor_type AS principal_actor_type, p.site_id AS principal_site_id,
                       d.id AS deputy_id, d.display_name AS deputy_name,
                       d.actor_type AS deputy_actor_type, d.site_id AS deputy_site_id
                FROM message m
                JOIN dispatched_action a ON a.id = m.dispatched_action_id
                JOIN duty_holder p ON p.id = a.duty_holder_id
                LEFT JOIN duty_holder d ON d.id = p.deputy_id
                WHERE a.run_id = ?
                ORDER BY m.sent_at, m.id
                """, runId).map(this::mapMessage);
    }

    @Override
    public List<CapMessageView> findCapMessagesByRunId(String runId) {
        return dsl.fetch("""
                SELECT c.identifier, c.incident_id, c.msg_type, c.status, c.sent_at,
                       c.gateway_response::text AS gateway_response, c.xml
                FROM cap_message c
                JOIN incident i ON i.id = c.incident_id
                WHERE i.run_id = ?
                ORDER BY c.sent_at, c.identifier
                """, runId).map(r -> new CapMessageView(
                r.get("identifier", String.class),
                r.get("incident_id", UUID.class),
                r.get("msg_type", String.class),
                r.get("status", String.class),
                instant(r, "sent_at"),
                r.get("gateway_response", String.class),
                r.get("xml", String.class)));
    }

    private MessageLogView mapMessage(Record r) {
        String text = r.get("text", String.class);
        boolean toDeputy = text != null && text.startsWith(DEPUTY_PREFIX) && r.get("deputy_id") != null;
        String who = toDeputy ? "deputy" : "principal";
        Integer step = r.get("escalation_step", Integer.class);
        return new MessageLogView(
                r.get("message_id", UUID.class),
                r.get("action_id", UUID.class),
                r.get("incident_id", UUID.class),
                instant(r, "message_sent_at"),
                toDeputy ? "DEPUTY_ESCALATION" : "ACTION",
                r.get("channel", String.class),
                r.get("language", String.class),
                text,
                r.get(who + "_id", UUID.class),
                r.get(who + "_name", String.class),
                r.get(who + "_actor_type", String.class),
                r.get(who + "_site_id", String.class),
                toDeputy ? r.get("principal_id", UUID.class) : null,
                toDeputy ? r.get("principal_name", String.class) : null,
                r.get("action_code", String.class),
                instant(r, "deadline_at"),
                r.get("state", String.class),
                step != null ? step : 0,
                instant(r, "acked_at"));
    }

    private static Instant instant(Record r, String column) {
        OffsetDateTime odt = r.get(column, OffsetDateTime.class);
        return odt != null ? odt.toInstant() : null;
    }
}
