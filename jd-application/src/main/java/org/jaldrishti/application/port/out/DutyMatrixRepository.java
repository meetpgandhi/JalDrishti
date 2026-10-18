package org.jaldrishti.application.port.out;

import org.jaldrishti.domain.DutyMatrixVersion;
import org.jaldrishti.domain.DutyRule;
import org.jaldrishti.domain.IncidentLevel;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for accessing and updating the duty matrix and associated rules.
 */
public interface DutyMatrixRepository {

    Optional<DutyMatrixVersion> findLatestApprovedVersion();

    Optional<DutyMatrixVersion> findVersion(int version);

    List<DutyRule> findRulesByVersionAndLevel(int version, IncidentLevel level);

    Optional<DutyRule> findRule(String ruleId, int version);

    void saveMatrixVersion(DutyMatrixVersion version);

    void saveDutyRule(DutyRule rule);
}
