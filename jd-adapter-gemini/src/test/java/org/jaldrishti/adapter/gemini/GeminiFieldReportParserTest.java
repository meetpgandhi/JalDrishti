package org.jaldrishti.adapter.gemini;

import org.jaldrishti.domain.EvidenceType;
import org.jaldrishti.domain.FieldReportObservation;
import org.jaldrishti.domain.FieldReportRecord;
import org.jaldrishti.domain.HazardType;
import org.jaldrishti.domain.ReportStatus;
import org.jaldrishti.domain.SenderTrust;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiFieldReportParserTest {

    private final GeminiFieldReportParser parser = new GeminiFieldReportParser("gemini-3.6-flash", "asia-south1", false);

    @Test
    void parsesNepaliWorkedExampleFromDhunche() {
        String nepaliText = "म स्याफ्रुबेसीबाट बोल्दैछु। भोटेकोसीमा ठूलो गेग्रान सहितको बाढी आयो, पुल बग्यो, मानिसहरू माथि भाग्दैछन्।";
        FieldReportRecord record = new FieldReportRecord(
                UUID.randomUUID(),
                "live",
                Instant.parse("2026-08-26T08:50:00Z"),
                "hash123",
                SenderTrust.OFFICIAL,
                "phone_sim",
                null,
                nepaliText,
                null,
                ReportStatus.PENDING,
                null
        );

        FieldReportObservation obs = parser.parse(record, Collections.emptyList());

        assertThat(obs.locationId()).isEqualTo("SYB");
        assertThat(obs.hazardType()).isEqualTo(HazardType.DEBRIS_FLOOD);
        assertThat(obs.evidence()).contains(EvidenceType.BOULDERS, EvidenceType.BRIDGE_GONE, EvidenceType.PEOPLE_FLEEING);
        assertThat(obs.confidence()).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void parsesHindiWorkedExampleFromGalchhi() {
        String hindiText = "गल्छी के पास त्रिशूली नदी बहुत तेज़ी से बढ़ रही है, पानी मटमैला है और पत्थरों की आवाज़ आ रही है।";
        FieldReportRecord record = new FieldReportRecord(
                UUID.randomUUID(),
                "live",
                Instant.parse("2026-08-26T10:40:00Z"),
                "hash456",
                SenderTrust.OBSERVER,
                "telegram",
                null,
                hindiText,
                null,
                ReportStatus.PENDING,
                null
        );

        FieldReportObservation obs = parser.parse(record, Collections.emptyList());

        assertThat(obs.locationId()).isEqualTo("GLB");
        assertThat(obs.hazardType()).isEqualTo(HazardType.RAPID_RISE);
        assertThat(obs.evidence()).contains(EvidenceType.FAST_RISE, EvidenceType.MUDDY_WATER, EvidenceType.ROARING_SOUND);
    }

    @Test
    void parsesCalmRiverReportAsNoHazard() {
        String text = "नदी सामान्य छ, पानीको बहाव शान्त छ।";
        FieldReportRecord record = new FieldReportRecord(
                UUID.randomUUID(),
                "live",
                Instant.parse("2026-08-26T14:30:00Z"),
                "hash789",
                SenderTrust.OFFICIAL,
                "sms",
                null,
                text,
                null,
                ReportStatus.PENDING,
                null
        );

        FieldReportObservation obs = parser.parse(record, Collections.emptyList());

        assertThat(obs.hazardType()).isEqualTo(HazardType.NO_HAZARD);
        assertThat(obs.isHazardous()).isFalse();
    }
}
