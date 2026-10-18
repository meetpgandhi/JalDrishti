package org.jaldrishti.application.port.out;

import org.jaldrishti.domain.ArrivalBand;
import org.jaldrishti.domain.Incident;

import java.util.List;

/**
 * Outbound port for broadcasting Common Alerting Protocol (CAP 1.2) XML alerts
 * to the NDRRMA national gateway.
 */
public interface CapGatewayPort {

    /**
     * Builds and posts OASIS CAP 1.2 XML for an active incident.
     * In simulation mode, status is strictly 'Exercise'.
     *
     * @param incident     Active incident
     * @param arrivalBands Calculated arrival bands along corridor
     * @param msgType      Alert, Update, or Cancel
     * @param isExercise   Whether simulation mode is active
     * @return Generated XML
     */
    String broadcast(Incident incident, List<ArrivalBand> arrivalBands, String msgType, boolean isExercise);
}
