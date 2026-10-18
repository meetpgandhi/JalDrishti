package org.jaldrishti.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class R11AutoCancelRuleTest {

    private final Station syp = new Station(
            "SYP", "corridor", "Syaphrubesi", "Bhote Koshi",
            15.0, 28.168, 85.371, 5.5, 7.0, 10, "vsat-rasuwa"
    );

    @Test
    @DisplayName("Silent station returns with normal reading below warning threshold -> closes WATCH")
    void silentStationReturnsClosesWatch() {
        Incident watchIncident = Incident.createWatch(
                UUID.randomUUID(), "test-run", "corridor", Instant.now(), "SYP", 1, 1
        );

        Reading normalReading = new Reading(
                "test-run", "SYP", Instant.now(), 2.5, 0.0, Instant.now(), "DHM", AlertTag.P
        );

        RuleResult result = R11AutoCancelRule.evaluate(watchIncident, syp, normalReading, null, false);

        assertThat(result.fired()).isTrue();
        assertThat(result.resultingLevel()).isEqualTo(IncidentLevel.STAND_DOWN);
        assertThat(result.ruleId()).isEqualTo("R11");
        assertThat(result.tag()).isEqualTo(AlertTag.D);
        assertThat(result.description()).contains("closing internal WATCH");
    }

    @Test
    @DisplayName("Observer at silent station reports NO_HAZARD -> closes WATCH")
    void observerReportsNoHazardClosesWatch() {
        Incident watchIncident = Incident.createWatch(
                UUID.randomUUID(), "test-run", "corridor", Instant.now(), "SYP", 1, 1
        );

        FieldReportObservation report = new FieldReportObservation(
                UUID.randomUUID(), "test-run", Instant.now(), "hash-gauge-reader",
                SenderTrust.OBSERVER, "telegram", Language.ne,
                "नदी शान्त छ, कुनै बाढी छैन", "River is calm, no flood",
                "SYB", null, HazardType.NO_HAZARD, Collections.emptyList(),
                Instant.now(), null, 0.95, false
        );

        RuleResult result = R11AutoCancelRule.evaluate(watchIncident, syp, null, report, false);

        assertThat(result.fired()).isTrue();
        assertThat(result.resultingLevel()).isEqualTo(IncidentLevel.STAND_DOWN);
        assertThat(result.description()).contains("NO_HAZARD");
    }

    @Test
    @DisplayName("Downstream window passes with no corroborating evidence -> closes WATCH")
    void downstreamWindowPassesClosesWatch() {
        Incident watchIncident = Incident.createWatch(
                UUID.randomUUID(), "test-run", "corridor", Instant.now(), "SYP", 1, 1
        );

        RuleResult result = R11AutoCancelRule.evaluate(watchIncident, syp, null, null, true);

        assertThat(result.fired()).isTrue();
        assertThat(result.resultingLevel()).isEqualTo(IncidentLevel.STAND_DOWN);
    }

    @Test
    @DisplayName("CRITICAL: A WARNING incident NEVER auto-cancels (requires forecaster stand-down)")
    void warningNeverAutoCancels() {
        Incident warningIncident = Incident.createWarning(
                UUID.randomUUID(), "test-run", "corridor", Instant.now(), "SYP", 1, 1, false
        );

        Reading normalReading = new Reading(
                "test-run", "SYP", Instant.now(), 2.0, 0.0, Instant.now(), "DHM", AlertTag.P
        );

        RuleResult result = R11AutoCancelRule.evaluate(warningIncident, syp, normalReading, null, true);

        assertThat(result.fired()).isFalse();
        assertThat(result.description()).contains("applies only to WATCH incidents");
    }
}
