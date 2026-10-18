package org.jaldrishti.adapter.cap;

import org.jaldrishti.domain.ArrivalBand;
import org.jaldrishti.domain.Incident;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Serializes incident warnings into OASIS Common Alerting Protocol (CAP 1.2) XML.
 *
 * <p><strong>CRITICAL RULE (H5):</strong> In simulation mode, status is strictly {@code Exercise}.
 */
public final class Cap12Builder {

    private static final ZoneId NEPAL_ZONE = ZoneId.of("Asia/Kathmandu");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(NEPAL_ZONE);

    private Cap12Builder() {}

    public static String buildCapXml(
            Incident incident,
            List<ArrivalBand> arrivalBands,
            String msgType,
            boolean isExercise,
            String sender
    ) {
        String identifier = String.format("JD-%s-%s-%s",
                incident.corridorId().toUpperCase(),
                incident.anchorStation() != null ? incident.anchorStation() : "CORR",
                incident.id().toString().substring(0, 8));

        String status = isExercise ? "Exercise" : "Actual";
        String sent = ISO_OFFSET.format(incident.openedAt());

        Instant onset = incident.openedAt();
        Instant expires = incident.openedAt().plus(Duration.ofHours(6));

        if (arrivalBands != null && !arrivalBands.isEmpty()) {
            onset = arrivalBands.get(0).earliestArrival();
            expires = arrivalBands.get(arrivalBands.size() - 1).latestArrival().plus(Duration.ofHours(6));
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<alert xmlns=\"urn:oasis:names:tc:emergency:cap:1.2\">\n");
        xml.append("  <identifier>").append(identifier).append("</identifier>\n");
        xml.append("  <sender>").append(sender != null ? sender : "jaldrishti@dhm.gov.np").append("</sender>\n");
        xml.append("  <sent>").append(sent).append("</sent>\n");
        xml.append("  <status>").append(status).append("</status>\n");
        xml.append("  <msgType>").append(msgType != null ? msgType : "Alert").append("</msgType>\n");
        xml.append("  <scope>Public</scope>\n");
        xml.append("  <info>\n");
        xml.append("    <category>Geo</category>\n");
        xml.append("    <category>Met</category>\n");
        xml.append("    <event>Flash flood with debris</event>\n");
        xml.append("    <responseType>Evacuate</responseType>\n");
        xml.append("    <urgency>Immediate</urgency>\n");
        xml.append("    <severity>Extreme</severity>\n");
        xml.append("    <certainty>Observed</certainty>\n");
        xml.append("    <onset>").append(ISO_OFFSET.format(onset)).append("</onset>\n");
        xml.append("    <expires>").append(ISO_OFFSET.format(expires)).append("</expires>\n");
        xml.append("    <headline>Sudden flood on ").append(incident.corridorId())
                .append(": upstream silence/loss detected</headline>\n");
        xml.append("    <parameter><valueName>jaldrishti:corridorId</valueName><value>")
                .append(incident.corridorId()).append("</value></parameter>\n");
        xml.append("    <parameter><valueName>jaldrishti:configVersion</valueName><value>")
                .append(incident.configVersion()).append("</value></parameter>\n");
        xml.append("    <parameter><valueName>jaldrishti:matrixVersion</valueName><value>")
                .append(incident.matrixVersion()).append("</value></parameter>\n");

        if (arrivalBands != null) {
            for (ArrivalBand band : arrivalBands) {
                xml.append("    <area>\n");
                xml.append("      <areaDesc>").append(band.siteName()).append("</areaDesc>\n");
                xml.append("    </area>\n");
            }
        }

        xml.append("  </info>\n");
        xml.append("</alert>");

        return xml.toString();
    }
}
