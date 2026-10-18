package org.jaldrishti.domain;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class R5CascadePropertyTest {

    private final CorridorConfig config = CorridorConfig.defaultConfig();

    @Property
    void r5NeverFiresWhenDownstreamIsUpstreamOfAnchor(
            @net.jqwik.api.ForAll("distancesKm") double dUpstream,
            @net.jqwik.api.ForAll("distancesKm") double dDownstream
    ) {
        // If downstream is actually at or upstream of anchor, R5 must never fire
        if (dDownstream <= dUpstream) {
            Station st1 = new Station("ST1", "corridor", "St1", "River", dUpstream, 28.0, 85.0, 5.0, 7.0, 10, "grp");
            Station st2 = new Station("ST2", "corridor", "St2", "River", dDownstream, 28.0, 85.0, 5.0, 7.0, 10, "grp");

            Instant t0 = Instant.parse("2026-08-26T00:00:00Z");
            Instant t1 = t0.plusSeconds(600);

            StationObservationState st2State = new StationObservationState(
                    "run", "ST2", StationState.SILENT, false, null, t0, t1, true, t1, false, null, 1.0, 0.0
            );

            RuleResult result = R5CascadeRule.evaluate(st1, t0, t1, st2, st2State, config);
            assertThat(result.fired()).isFalse();
        }
    }

    @Property
    void r5OrderConsistencyWindowFollowsPhysicalVelocityBounds(
            @net.jqwik.api.ForAll("positiveDistancesKm") double deltaKm,
            @net.jqwik.api.ForAll("randomIntervalSeconds") long eventOffsetSec
    ) {
        Station st1 = new Station("ST1", "corridor", "St1", "River", 0.0, 28.0, 85.0, 5.0, 7.0, 10, "grp");
        Station st2 = new Station("ST2", "corridor", "St2", "River", deltaKm, 28.0, 85.0, 5.0, 7.0, 10, "grp");

        Instant tLastGood = Instant.parse("2026-08-26T00:00:00Z");
        Instant tFirstMissed = tLastGood.plusSeconds(600);

        double distanceM = deltaKm * 1000.0;
        long minTravelSec = Math.round(distanceM / config.vMaxMs()); // Fastest (35 m/s)
        long maxTravelSec = Math.round(distanceM / config.vMinMs()); // Slowest (3 m/s)

        Instant eventTime = tLastGood.plusSeconds(eventOffsetSec);

        StationObservationState st2State = new StationObservationState(
                "run", "ST2", StationState.RATE_ALERT, true, eventTime, eventTime, null,
                false, null, false, null, 4.0, 0.0
        );

        RuleResult result = R5CascadeRule.evaluate(st1, tLastGood, tFirstMissed, st2, st2State, config);

        Instant earliestPossible = tLastGood.plusSeconds(minTravelSec);
        Instant latestPossible = tFirstMissed.plusSeconds(maxTravelSec);

        boolean physicallyPossible = !eventTime.isBefore(earliestPossible) && !eventTime.isAfter(latestPossible);

        assertThat(result.fired()).isEqualTo(physicallyPossible);
    }

    @Provide
    Arbitrary<Double> distancesKm() {
        return Arbitraries.doubles().between(0.0, 200.0);
    }

    @Provide
    Arbitrary<Double> positiveDistancesKm() {
        return Arbitraries.doubles().between(1.0, 150.0);
    }

    @Provide
    Arbitrary<Long> randomIntervalSeconds() {
        return Arbitraries.longs().between(0, 15000);
    }
}
