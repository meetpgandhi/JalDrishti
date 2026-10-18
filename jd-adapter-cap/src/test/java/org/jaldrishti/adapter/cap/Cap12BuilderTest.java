package org.jaldrishti.adapter.cap;

import org.jaldrishti.domain.AlertTag;
import org.jaldrishti.domain.ArrivalBand;
import org.jaldrishti.domain.Incident;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Cap12BuilderTest {

    @Test
    void buildsExerciseStatusInSimulation() {
        Incident incident = Incident.createWarning(
                UUID.randomUUID(),
                "sim-run-123",
                "bhote-trishuli",
                Instant.parse("2026-08-26T08:53:00Z"),
                "RSW",
                1,
                1,
                true
        );

        ArrivalBand band = new ArrivalBand(
                "BTW",
                "Betrawati",
                38.0,
                Instant.parse("2026-08-26T09:16:00Z"),
                Instant.parse("2026-08-26T09:36:00Z"),
                AlertTag.D
        );

        String xml = Cap12Builder.buildCapXml(incident, List.of(band), "Alert", true, "jaldrishti@dhm.example");

        assertThat(xml).contains("<status>Exercise</status>");
        assertThat(xml).contains("<msgType>Alert</msgType>");
        assertThat(xml).contains("<areaDesc>Betrawati</areaDesc>");
        assertThat(xml).contains("<valueName>jaldrishti:matrixVersion</valueName>");
        assertThat(xml).contains("<event>Flash flood with debris</event>");
    }

    @Test
    void buildsActualStatusInLive() {
        Incident incident = Incident.createWarning(
                UUID.randomUUID(),
                "live",
                "bhote-trishuli",
                Instant.parse("2026-08-26T08:53:00Z"),
                "RSW",
                1,
                1,
                false
        );

        String xml = Cap12Builder.buildCapXml(incident, List.of(), "Alert", false, "jaldrishti@dhm.gov.np");

        assertThat(xml).contains("<status>Actual</status>");
    }
}
