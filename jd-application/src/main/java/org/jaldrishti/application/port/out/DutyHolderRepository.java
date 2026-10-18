package org.jaldrishti.application.port.out;

import org.jaldrishti.domain.ActorType;
import org.jaldrishti.domain.DutyHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for accessing registered duty holders along the corridor.
 */
public interface DutyHolderRepository {

    Optional<DutyHolder> findById(UUID id);

    List<DutyHolder> findAll();

    List<DutyHolder> findByActorType(ActorType actorType);

    List<DutyHolder> findBySiteId(String siteId);

    void save(DutyHolder dutyHolder);
}
