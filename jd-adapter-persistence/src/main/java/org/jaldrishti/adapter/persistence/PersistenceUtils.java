package org.jaldrishti.adapter.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class PersistenceUtils {

    private PersistenceUtils() {}

    public static Instant toInstant(Object timeObj) {
        if (timeObj == null) {
            return null;
        }
        if (timeObj instanceof OffsetDateTime odt) {
            return odt.toInstant();
        } else if (timeObj instanceof Timestamp ts) {
            return ts.toInstant();
        } else if (timeObj instanceof Instant inst) {
            return inst;
        }
        return Instant.now();
    }

    public static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }
}
