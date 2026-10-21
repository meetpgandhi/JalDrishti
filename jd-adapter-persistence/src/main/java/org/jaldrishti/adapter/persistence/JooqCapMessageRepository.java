package org.jaldrishti.adapter.persistence;

import org.jaldrishti.application.port.out.CapMessageRepository;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
public class JooqCapMessageRepository implements CapMessageRepository {

    private final DSLContext dsl;

    public JooqCapMessageRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext must not be null");
    }

    @Override
    public void save(String identifier, UUID incidentId, String msgType, String status, String xml, Instant sentAt, String gatewayResponse) {
        OffsetDateTime sent = PersistenceUtils.toOffsetDateTime(sentAt);
        JSONB gatewayJson = toJsonb(gatewayResponse);

        dsl.insertInto(table("cap_message"))
                .columns(
                        field("identifier"),
                        field("incident_id"),
                        field("msg_type"),
                        field("status"),
                        field("xml"),
                        field("sent_at"),
                        field("gateway_response")
                )
                .values(
                        identifier,
                        incidentId,
                        msgType,
                        status,
                        xml,
                        sent,
                        gatewayJson
                )
                .onConflict(field("identifier"))
                .doUpdate()
                .set(field("msg_type"), msgType)
                .set(field("status"), status)
                .set(field("xml"), xml)
                .set(field("gateway_response"), gatewayJson)
                .execute();
    }

    @Override
    public List<String> findXmlByIncidentId(UUID incidentId) {
        return dsl.select(field("xml", String.class))
                .from(table("cap_message"))
                .where(field("incident_id").eq(incidentId))
                .orderBy(field("sent_at").desc())
                .fetchInto(String.class);
    }

    /** gateway_response is JSONB: pass JSON through as-is, quote anything else as a JSON string. */
    private static JSONB toJsonb(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        if (t.startsWith("{") || t.startsWith("[")) {
            return JSONB.valueOf(t);
        }
        return JSONB.valueOf("\"" + t.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
    }
}
