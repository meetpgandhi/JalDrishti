package org.jaldrishti.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Computed flood wave arrival band for a downstream site.
 *
 * @param siteId          Site identifier
 * @param siteName        Display name
 * @param riverKm         River chainage in km
 * @param earliestArrival Earliest expected arrival time
 * @param latestArrival   Latest expected arrival time
 * @param tag             Provenance tag ([D] derived)
 */
public record ArrivalBand(
        String siteId,
        String siteName,
        double riverKm,
        Instant earliestArrival,
        Instant latestArrival,
        AlertTag tag
) {
    public ArrivalBand {
        Objects.requireNonNull(siteId, "siteId must not be null");
        Objects.requireNonNull(siteName, "siteName must not be null");
        Objects.requireNonNull(earliestArrival, "earliestArrival must not be null");
        Objects.requireNonNull(latestArrival, "latestArrival must not be null");
        Objects.requireNonNull(tag, "tag must not be null");
        if (latestArrival.isBefore(earliestArrival)) {
            throw new IllegalArgumentException("latestArrival cannot be before earliestArrival: "
                    + earliestArrival + " > " + latestArrival);
        }
    }
}
