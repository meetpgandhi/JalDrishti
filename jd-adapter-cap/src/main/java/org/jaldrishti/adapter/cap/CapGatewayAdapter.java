package org.jaldrishti.adapter.cap;

import org.jaldrishti.application.port.out.CapGatewayPort;
import org.jaldrishti.application.port.out.CapMessageRepository;
import org.jaldrishti.domain.ArrivalBand;
import org.jaldrishti.domain.Incident;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Adapter that formats and publishes CAP 1.2 XML alerts to national gateways
 * and records them in the CapMessageRepository.
 */
public class CapGatewayAdapter implements CapGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(CapGatewayAdapter.class);

    private final CapMessageRepository repository;
    private final String senderAddress;

    public CapGatewayAdapter(CapMessageRepository repository, String senderAddress) {
        this.repository = repository;
        this.senderAddress = senderAddress != null ? senderAddress : "jaldrishti@dhm.gov.np";
    }

    @Override
    public String broadcast(Incident incident, List<ArrivalBand> arrivalBands, String msgType, boolean isExercise) {
        String xml = Cap12Builder.buildCapXml(incident, arrivalBands, msgType, isExercise, senderAddress);
        String identifier = String.format("JD-%s-%s-%s",
                incident.corridorId().toUpperCase(),
                incident.anchorStation() != null ? incident.anchorStation() : "CORR",
                incident.id().toString().substring(0, 8));

        log.info("Broadcasting CAP 1.2 XML alert: identifier={}, status={}, msgType={}",
                identifier, isExercise ? "Exercise" : "Actual", msgType);

        if (repository != null) {
            repository.save(
                    identifier,
                    incident.id(),
                    msgType,
                    isExercise ? "Exercise" : "Actual",
                    xml,
                    Instant.now(),
                    "{\"status\":\"ACCEPTED\",\"gateway\":\"NDRRMA_SIMULATED\"}"
            );
        }

        return xml;
    }
}
