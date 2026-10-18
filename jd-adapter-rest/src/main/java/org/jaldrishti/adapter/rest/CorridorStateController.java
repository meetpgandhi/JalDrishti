package org.jaldrishti.adapter.rest;

import org.jaldrishti.application.port.out.CorridorConfigRepository;
import org.jaldrishti.application.port.out.IncidentRepository;
import org.jaldrishti.application.port.out.ReadingRepository;
import org.jaldrishti.application.port.out.StationStateRepository;
import org.jaldrishti.domain.CorridorConfig;
import org.jaldrishti.domain.Incident;
import org.jaldrishti.domain.Reading;
import org.jaldrishti.domain.Site;
import org.jaldrishti.domain.Station;
import org.jaldrishti.domain.StationObservationState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for querying corridor configuration, metadata, and live station states.
 */
@RestController
@RequestMapping("/api/v1/corridors")
public class CorridorStateController {

    private final CorridorConfigRepository configRepository;
    private final StationStateRepository stationStateRepository;
    private final IncidentRepository incidentRepository;
    private final ReadingRepository readingRepository;

    public CorridorStateController(
            CorridorConfigRepository configRepository,
            StationStateRepository stationStateRepository,
            IncidentRepository incidentRepository,
            ReadingRepository readingRepository
    ) {
        this.configRepository = configRepository;
        this.stationStateRepository = stationStateRepository;
        this.incidentRepository = incidentRepository;
        this.readingRepository = readingRepository;
    }

    public record CorridorDetailsResponse(
            CorridorConfig config,
            List<Station> stations,
            List<Site> sites
    ) {}

    public record StationTileResponse(
            String stationId,
            String name,
            String river,
            double riverKm,
            Double warningM,
            Double dangerM,
            String state,
            boolean silent,
            boolean rateAlert,
            Double lastLevelM,
            String lastObservedAt,
            String telemetryGroup
    ) {}

    public record CorridorStateResponse(
            String corridorId,
            String runId,
            List<StationTileResponse> stations,
            Incident openIncident
    ) {}

    @GetMapping("/{id}")
    public ResponseEntity<CorridorDetailsResponse> getCorridor(@PathVariable("id") String corridorId) {
        Optional<CorridorConfig> configOpt = configRepository.findCurrent(corridorId);
        if (configOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<Station> stations = configRepository.findStations(corridorId);
        List<Site> sites = configRepository.findSites(corridorId);
        return ResponseEntity.ok(new CorridorDetailsResponse(configOpt.get(), stations, sites));
    }

    @GetMapping("/{id}/state")
    public ResponseEntity<CorridorStateResponse> getCorridorState(
            @PathVariable("id") String corridorId,
            @RequestParam(name = "runId", defaultValue = "live") String runId
    ) {
        List<Station> stations = configRepository.findStations(corridorId);
        List<StationTileResponse> tiles = new ArrayList<>();

        for (Station s : stations) {
            StationObservationState st = stationStateRepository.find(runId, s.id())
                    .orElse(StationObservationState.initial(runId, s.id()));

            Optional<Reading> latestReading = readingRepository.findLatest(runId, s.id());

            tiles.add(new StationTileResponse(
                    s.id(),
                    s.name(),
                    s.river(),
                    s.riverKm(),
                    s.warningM(),
                    s.dangerM(),
                    st.effectiveState().name(),
                    st.silent(),
                    st.rateAlert(),
                    latestReading.map(Reading::levelM).orElse(st.lastReportedLevel()),
                    latestReading.map(r -> r.observedAt().toString()).orElse(null),
                    s.telemetryGroup()
            ));
        }

        Incident openIncident = incidentRepository.findOpenIncident(runId, corridorId).orElse(null);

        return ResponseEntity.ok(new CorridorStateResponse(corridorId, runId, tiles, openIncident));
    }
}
