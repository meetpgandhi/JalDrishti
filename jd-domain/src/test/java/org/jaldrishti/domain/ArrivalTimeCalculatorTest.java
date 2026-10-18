package org.jaldrishti.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArrivalTimeCalculatorTest {

    private final ArrivalTimeCalculator calculator = ArrivalTimeCalculator.defaultCorridorCurve();
    private static final ZoneId NPT = ZoneId.of("+05:45");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm").withZone(NPT);

    // RSW anchor on 26 August: lastGood 08:40, firstMissed 08:50
    // 08:40:00 NPT = 02:55:00 UTC
    // 08:50:00 NPT = 03:05:00 UTC
    private final Instant rswLastGood = Instant.parse("2026-08-26T02:55:00Z");
    private final Instant rswFirstMissed = Instant.parse("2026-08-26T03:05:00Z");

    @Test
    @DisplayName("Betrawati (BTW-S, km 38): expected band 09:16-09:36 [D]")
    void betrawatiArrivalBand() {
        Site site = new Site("BTW-S", "corridor", "Betrawati", "बेत्रावती", List.of(), "SETTLEMENT", 38.0, 27.974, 85.181, List.of());
        ArrivalBand band = calculator.calculateBand(site, 0.0, rswLastGood, rswFirstMissed);

        assertThat(TIME_FMT.format(band.earliestArrival())).isEqualTo("09:16");
        assertThat(TIME_FMT.format(band.latestArrival())).isEqualTo("09:36");
        assertThat(band.tag()).isEqualTo(AlertTag.D);
    }

    @Test
    @DisplayName("Bidur (BID, km 55): expected band 09:46-10:12 [D]")
    void bidurArrivalBand() {
        Site site = new Site("BID", "corridor", "Trishuli Bazaar / Bidur", "बिदुर", List.of(), "SETTLEMENT", 55.0, 27.992, 85.108, List.of());
        ArrivalBand band = calculator.calculateBand(site, 0.0, rswLastGood, rswFirstMissed);

        assertThat(TIME_FMT.format(band.earliestArrival())).isEqualTo("09:46");
        assertThat(TIME_FMT.format(band.latestArrival())).isEqualTo("10:12");
    }

    @Test
    @DisplayName("Galchhi (GLB, km 88): expected band 10:45-11:23 [D]")
    void galchhiArrivalBand() {
        Site site = new Site("GLB", "corridor", "Galchhi Bazaar", "गल्छी", List.of(), "SETTLEMENT", 88.0, 27.850, 85.066, List.of());
        ArrivalBand band = calculator.calculateBand(site, 0.0, rswLastGood, rswFirstMissed);

        assertThat(TIME_FMT.format(band.earliestArrival())).isEqualTo("10:45");
        assertThat(TIME_FMT.format(band.latestArrival())).isEqualTo("11:23");
    }

    @Test
    @DisplayName("Malekhu (MLB, km 100): expected band 11:06-11:39 [D]")
    void malekhuArrivalBand() {
        Site site = new Site("MLB", "corridor", "Malekhu", "मालेखु", List.of(), "SETTLEMENT", 100.0, 27.879, 84.953, List.of());
        ArrivalBand band = calculator.calculateBand(site, 0.0, rswLastGood, rswFirstMissed);

        assertThat(TIME_FMT.format(band.earliestArrival())).isEqualTo("11:06");
        assertThat(TIME_FMT.format(band.latestArrival())).isEqualTo("11:39");
    }

    @Test
    @DisplayName("Mugling (MUG, km 132): expected band 12:41-13:21 [D]")
    void muglingArrivalBand() {
        Site site = new Site("MUG", "corridor", "Mugling", "मुग्लिन", List.of(), "HIGHWAY_JUNCTION", 132.0, 27.872, 84.729, List.of());
        ArrivalBand band = calculator.calculateBand(site, 0.0, rswLastGood, rswFirstMissed);

        assertThat(TIME_FMT.format(band.earliestArrival())).isEqualTo("12:41");
        assertThat(TIME_FMT.format(band.latestArrival())).isEqualTo("13:21");
    }

    @Test
    @DisplayName("Devghat (DVS, km 168): expected band 13:26-14:36 [D]")
    void devghatArrivalBand() {
        Site site = new Site("DVS", "corridor", "Devghat", "देवघाट", List.of(), "SETTLEMENT", 168.0, 27.706, 84.437, List.of());
        ArrivalBand band = calculator.calculateBand(site, 0.0, rswLastGood, rswFirstMissed);

        assertThat(TIME_FMT.format(band.earliestArrival())).isEqualTo("13:26");
        assertThat(TIME_FMT.format(band.latestArrival())).isEqualTo("14:36");
    }
}
