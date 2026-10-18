package org.jaldrishti.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Arrival-Time Model and Wave Travel Interpolator.
 *
 * <p>Calibrated from the 26 August 2026 published event [D]:
 * <ul>
 *   <li>RSW (km 0): front reference at 08:44 [P]</li>
 *   <li>BTW (km 38): travel time 36–46 min [D]</li>
 *   <li>MLK (km 100): travel time 146–179 min [D]</li>
 *   <li>Mugling (km 132): travel time 241–271 min [D]/[A]</li>
 *   <li>DVG (km 168): travel time 286–346 min [A]</li>
 * </ul>
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Linear interpolation between calibration points along river chainage.</li>
 *   <li>{@code earliestArrival = anchorLastGood + (travelTimeEarliest(site) - travelTimeEarliest(anchor))}</li>
 *   <li>{@code latestArrival   = anchorFirstMissed + (travelTimeLatest(site) - travelTimeLatest(anchor))}</li>
 * </ol>
 */
public final class ArrivalTimeCalculator {

    private final List<CalibrationPoint> calibrationPoints;

    public ArrivalTimeCalculator(List<CalibrationPoint> calibrationPoints) {
        Objects.requireNonNull(calibrationPoints, "calibrationPoints must not be null");
        if (calibrationPoints.isEmpty()) {
            throw new IllegalArgumentException("Must provide at least one calibration point");
        }
        List<CalibrationPoint> sorted = new ArrayList<>(calibrationPoints);
        sorted.sort(Comparator.comparingDouble(CalibrationPoint::riverKm));
        this.calibrationPoints = Collections.unmodifiableList(sorted);
    }

    /**
     * Default travel time calibration curve for the Bhote Koshi - Trishuli - Narayani corridor.
     */
    public static ArrivalTimeCalculator defaultCorridorCurve() {
        return new ArrivalTimeCalculator(List.of(
                new CalibrationPoint("RSW", 0.0, 0.0, 0.0, AlertTag.P),
                new CalibrationPoint("BTW-S", 38.0, 36.0, 46.0, AlertTag.D),
                new CalibrationPoint("MLB", 100.0, 146.0, 179.0, AlertTag.D),
                new CalibrationPoint("MUG", 132.0, 241.0, 271.0, AlertTag.D),
                new CalibrationPoint("DVS", 168.0, 286.0, 346.0, AlertTag.A)
        ));
    }

    public record TravelTimeRange(double earliestMinutes, double latestMinutes) {}

    /**
     * Interpolates the travel time range (earliest and latest minutes) from corridor origin to km x.
     */
    public TravelTimeRange interpolateTravelTime(double riverKm) {
        if (calibrationPoints.isEmpty()) {
            return new TravelTimeRange(0.0, 0.0);
        }

        CalibrationPoint first = calibrationPoints.get(0);
        if (riverKm <= first.riverKm()) {
            return new TravelTimeRange(first.travelTimeMinEarliest(), first.travelTimeMinLatest());
        }

        CalibrationPoint last = calibrationPoints.get(calibrationPoints.size() - 1);
        if (riverKm >= last.riverKm()) {
            return new TravelTimeRange(last.travelTimeMinEarliest(), last.travelTimeMinLatest());
        }

        for (int i = 0; i < calibrationPoints.size() - 1; i++) {
            CalibrationPoint p1 = calibrationPoints.get(i);
            CalibrationPoint p2 = calibrationPoints.get(i + 1);

            if (riverKm >= p1.riverKm() && riverKm <= p2.riverKm()) {
                double spanKm = p2.riverKm() - p1.riverKm();
                if (spanKm <= 0.0) {
                    return new TravelTimeRange(p1.travelTimeMinEarliest(), p1.travelTimeMinLatest());
                }
                double fraction = (riverKm - p1.riverKm()) / spanKm;

                double earliest = p1.travelTimeMinEarliest()
                        + fraction * (p2.travelTimeMinEarliest() - p1.travelTimeMinEarliest());
                double latest = p1.travelTimeMinLatest()
                        + fraction * (p2.travelTimeMinLatest() - p1.travelTimeMinLatest());

                return new TravelTimeRange(earliest, latest);
            }
        }

        return new TravelTimeRange(last.travelTimeMinEarliest(), last.travelTimeMinLatest());
    }

    /**
     * Computes the arrival band for a downstream site given an upstream anchor station.
     *
     * @param site               Target downstream gazetteer site
     * @param anchorStationKm    River chainage of the anchor station (e.g. 0.0 for RSW)
     * @param anchorLastGood     t_lastGood of the anchor station
     * @param anchorFirstMissed  t_firstMissed of the anchor station
     * @return Computed ArrivalBand
     */
    public ArrivalBand calculateBand(
            Site site,
            double anchorStationKm,
            Instant anchorLastGood,
            Instant anchorFirstMissed
    ) {
        Objects.requireNonNull(site, "Site must not be null");
        Objects.requireNonNull(anchorLastGood, "anchorLastGood must not be null");
        Objects.requireNonNull(anchorFirstMissed, "anchorFirstMissed must not be null");

        TravelTimeRange siteTT = interpolateTravelTime(site.riverKm());
        TravelTimeRange anchorTT = interpolateTravelTime(anchorStationKm);

        double relEarliestMin = Math.max(0.0, siteTT.earliestMinutes() - anchorTT.earliestMinutes());
        double relLatestMin = Math.max(0.0, siteTT.latestMinutes() - anchorTT.latestMinutes());

        // Round to nearest whole second
        long earliestSec = Math.round(relEarliestMin * 60.0);
        long latestSec = Math.round(relLatestMin * 60.0);

        Instant earliestArrival = anchorLastGood.plus(Duration.ofSeconds(earliestSec));
        Instant latestArrival = anchorFirstMissed.plus(Duration.ofSeconds(latestSec));

        // Ensure latestArrival is never before earliestArrival due to clock jitter
        if (latestArrival.isBefore(earliestArrival)) {
            latestArrival = earliestArrival;
        }

        return new ArrivalBand(
                site.id(),
                site.nameEn(),
                site.riverKm(),
                earliestArrival,
                latestArrival,
                AlertTag.D
        );
    }
}
