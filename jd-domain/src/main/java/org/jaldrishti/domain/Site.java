package org.jaldrishti.domain;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Gazetteer point of interest along the river corridor (settlement, hydropower, junction).
 *
 * @param id              Site identifier (e.g., "BTW-S", "BID", "MLB")
 * @param corridorId      Corridor identifier
 * @param nameEn          English name
 * @param nameNe          Nepali name
 * @param aliases         Known aliases and transliterations
 * @param type            Site category (e.g., SETTLEMENT, HYDROPOWER, HIGHWAY_JUNCTION)
 * @param riverKm         River chainage in km from origin
 * @param lat             Latitude in decimal degrees
 * @param lon             Longitude in decimal degrees
 * @param dutyHolderTypes Typical duty holder roles registered at this site
 */
public record Site(
        String id,
        String corridorId,
        String nameEn,
        String nameNe,
        List<String> aliases,
        String type,
        double riverKm,
        double lat,
        double lon,
        List<ActorType> dutyHolderTypes
) {
    public Site {
        Objects.requireNonNull(id, "Site id must not be null");
        Objects.requireNonNull(corridorId, "corridorId must not be null");
        Objects.requireNonNull(nameEn, "nameEn must not be null");
        aliases = aliases == null ? Collections.emptyList() : List.copyOf(aliases);
        dutyHolderTypes = dutyHolderTypes == null ? Collections.emptyList() : List.copyOf(dutyHolderTypes);
    }
}
