package org.jaldrishti.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class R8ObserverRuleTest {

    private final CorridorConfig config = CorridorConfig.defaultConfig();

    private final Map<String, Site> sites = Map.of(
            "SYB", new Site("SYB", "corridor", "Syaphrubesi", "स्याफ्रुबेसी", List.of(), "SETTLEMENT", 15.0, 28.168, 85.371, List.of()),
            "BTW-S", new Site("BTW-S", "corridor", "Betrawati", "बेत्रावती", List.of(), "SETTLEMENT", 38.0, 27.974, 85.181, List.of()),
            "TIM", new Site("TIM", "corridor", "Timure", "तिमुरे", List.of(), "SETTLEMENT", 3.0, 28.270, 85.514, List.of())
    );

    @Test
    @DisplayName("Single OBSERVER report + active sensor evidence -> WARNING")
    void singleObserverWithSensorEvidenceFiresWarning() {
        FieldReportObservation report = new FieldReportObservation(
                UUID.randomUUID(), "test-run", Instant.now(), "hash-gauge-reader-syp",
                SenderTrust.OBSERVER, "telegram", Language.ne,
                "नदी ह्वात्तै बढ्यो", "River rose rapidly", "SYB", null,
                HazardType.RAPID_RISE, List.of(EvidenceType.FAST_RISE), Instant.now(), null, 0.85, false
        );

        RuleResult result = R8ObserverRule.evaluate(report, Collections.emptyList(), sites, true, config);

        assertThat(result.fired()).isTrue();
        assertThat(result.resultingLevel()).isEqualTo(IncidentLevel.WARNING);
        assertThat(result.ruleId()).isEqualTo("R8");
        assertThat(result.tag()).isEqualTo(AlertTag.D);
    }

    @Test
    @DisplayName("Two independent OBSERVER reports within 30 min and 20 km -> WARNING")
    void twoIndependentObserverReportsFireWarning() {
        Instant t0 = Instant.parse("2026-08-26T03:00:00Z");
        Instant t1 = Instant.parse("2026-08-26T03:15:00Z"); // 15 min apart (< 30 min)

        // SYB (km 15) and TIM (km 3) are 12 km apart (< 20 km)
        FieldReportObservation rep1 = new FieldReportObservation(
                UUID.randomUUID(), "test-run", t0, "hash-observer-timure",
                SenderTrust.OBSERVER, "telegram", Language.ne,
                "ठूलो बाढी आयो", "Big flood", "TIM", null,
                HazardType.DEBRIS_FLOOD, List.of(EvidenceType.BOULDERS), t0, null, 0.88, false
        );

        FieldReportObservation rep2 = new FieldReportObservation(
                UUID.randomUUID(), "test-run", t1, "hash-observer-syaphrubesi",
                SenderTrust.OBSERVER, "telegram", Language.ne,
                "नदी उर्लिएको छ", "River raging", "SYB", null,
                HazardType.DEBRIS_FLOOD, List.of(EvidenceType.MUDDY_WATER), t1, null, 0.90, false
        );

        RuleResult result = R8ObserverRule.evaluate(rep2, List.of(rep1), sites, false, config);

        assertThat(result.fired()).isTrue();
        assertThat(result.resultingLevel()).isEqualTo(IncidentLevel.WARNING);
        assertThat(result.description()).contains("Two independent observer reports");
    }

    @Test
    @DisplayName("Two reports from the SAME sender hash -> does NOT fire (not independent)")
    void sameSenderDoesNotFire() {
        Instant t0 = Instant.parse("2026-08-26T03:00:00Z");
        Instant t1 = Instant.parse("2026-08-26T03:15:00Z");

        FieldReportObservation rep1 = new FieldReportObservation(
                UUID.randomUUID(), "test-run", t0, "hash-same-user",
                SenderTrust.OBSERVER, "telegram", Language.ne,
                "पानी बढ्यो", "Water rose", "TIM", null,
                HazardType.DEBRIS_FLOOD, List.of(), t0, null, 0.88, false
        );

        FieldReportObservation rep2 = new FieldReportObservation(
                UUID.randomUUID(), "test-run", t1, "hash-same-user",
                SenderTrust.OBSERVER, "telegram", Language.ne,
                "अझै बढ्दैछ", "Still rising", "TIM", null,
                HazardType.DEBRIS_FLOOD, List.of(), t1, null, 0.90, false
        );

        RuleResult result = R8ObserverRule.evaluate(rep2, List.of(rep1), sites, false, config);

        assertThat(result.fired()).isFalse();
    }

    @Test
    @DisplayName("Two reports > 20 km apart (e.g. TIM km 3 and BTW-S km 38 = 35 km) -> does NOT fire")
    void tooFarApartDoesNotFire() {
        Instant t0 = Instant.parse("2026-08-26T03:00:00Z");
        Instant t1 = Instant.parse("2026-08-26T03:15:00Z");

        FieldReportObservation rep1 = new FieldReportObservation(
                UUID.randomUUID(), "test-run", t0, "hash-observer-1",
                SenderTrust.OBSERVER, "telegram", Language.ne,
                "बाढी आयो", "Flood", "TIM", null,
                HazardType.DEBRIS_FLOOD, List.of(), t0, null, 0.88, false
        );

        FieldReportObservation rep2 = new FieldReportObservation(
                UUID.randomUUID(), "test-run", t1, "hash-observer-2",
                SenderTrust.OBSERVER, "telegram", Language.ne,
                "बाढी आयो", "Flood", "BTW-S", null,
                HazardType.DEBRIS_FLOOD, List.of(), t1, null, 0.90, false
        );

        RuleResult result = R8ObserverRule.evaluate(rep2, List.of(rep1), sites, false, config);

        assertThat(result.fired()).isFalse();
    }
}
