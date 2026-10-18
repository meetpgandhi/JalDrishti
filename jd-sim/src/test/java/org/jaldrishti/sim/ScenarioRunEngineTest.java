package org.jaldrishti.sim;

import org.jaldrishti.application.port.in.FieldReportCommand;
import org.jaldrishti.application.port.in.IngestReadingUseCase;
import org.jaldrishti.application.port.in.ManageSimulationUseCase;
import org.jaldrishti.application.port.in.ProcessFieldReportUseCase;
import org.jaldrishti.application.port.in.ReadingCommand;
import org.jaldrishti.domain.FieldReportRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link ScenarioRunEngine}, loading the real S1 scenario YAML
 * (scenarios/aug26-as-happened.yaml), reproduces the exact R5 worked example
 * from docs/HANDOFF.md: RSW's last good reading at 08:40, first missed at
 * 08:50; SYP's last good reading at 08:40 (default variant), then silent.
 *
 * <p>The clock is driven manually (paused, then stepped with setTime) so the
 * test is deterministic and does not depend on real wall-clock elapsed time.
 */
class ScenarioRunEngineTest {

    private SimClock clock;
    private RecordingReadingUseCase readingUseCase;
    private RecordingFieldReportUseCase fieldReportUseCase;
    private ScenarioRunEngine engine;

    private static final class RecordingReadingUseCase implements IngestReadingUseCase {
        final List<ReadingCommand> commands = new ArrayList<>();

        @Override
        public void ingest(ReadingCommand command) {
            commands.add(command);
        }
    }

    private static final class RecordingFieldReportUseCase implements ProcessFieldReportUseCase {
        final List<FieldReportCommand> commands = new ArrayList<>();

        @Override
        public FieldReportRecord processFieldReport(FieldReportCommand command) {
            commands.add(command);
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        clock = SimClock.create(Instant.parse("2026-08-26T02:15:00Z")); // 08:00 NPT
        readingUseCase = new RecordingReadingUseCase();
        fieldReportUseCase = new RecordingFieldReportUseCase();
        engine = new ScenarioRunEngine(clock, readingUseCase, fieldReportUseCase, null, null, "bhote-trishuli-narayani");
    }

    private void stepTo(Instant simTime) {
        clock.pause();
        clock.setTime(simTime);
        engine.tick();
    }

    @Test
    @DisplayName("S1 schedule reproduces the documented R5 worked example for RSW and SYP")
    void s1ScheduleMatchesR5WorkedExample() {
        ManageSimulationUseCase.RunSnapshot started = engine.start("test-run", "aug26-as-happened", null);
        assertThat(started.runId()).isEqualTo("test-run");
        assertThat(started.scenario()).isEqualTo("aug26-as-happened");

        Instant scenarioStart = Instant.parse("2026-08-26T02:15:00Z"); // 08:00 NPT
        // Drive the clock forward in 10-minute steps through the critical window (08:00 -> 09:10 NPT)
        for (int minutes = 0; minutes <= 70; minutes += 10) {
            stepTo(scenarioStart.plus(Duration.ofMinutes(minutes)));
        }

        List<ReadingCommand> rsw = readingUseCase.commands.stream()
                .filter(c -> c.stationId().equals("RSW"))
                .sorted(Comparator.comparing(ReadingCommand::observedAt))
                .toList();
        List<ReadingCommand> syp = readingUseCase.commands.stream()
                .filter(c -> c.stationId().equals("SYP"))
                .sorted(Comparator.comparing(ReadingCommand::observedAt))
                .toList();

        // RSW: last good reading at 08:40 NPT, nothing at/after 08:50 NPT (outage.fromSlot = 08:50)
        Instant t0840 = Instant.parse("2026-08-26T02:55:00Z");
        Instant t0850 = Instant.parse("2026-08-26T03:05:00Z");

        assertThat(rsw).isNotEmpty();
        assertThat(rsw.get(rsw.size() - 1).observedAt()).isEqualTo(t0840);
        assertThat(rsw).noneMatch(c -> !c.observedAt().isBefore(t0850));
        // The 08:40 pinned [P] reading must carry its published value, not the flat generator's noise.
        assertThat(rsw.stream().filter(c -> c.observedAt().equals(t0840)).findFirst().orElseThrow().levelM())
                .isEqualTo(1.62);

        // SYP: default variant syaphrubesiLastReading = 08:40; afterLastPinned outage means
        // nothing is emitted after that pinned reading either.
        assertThat(syp).isNotEmpty();
        assertThat(syp.get(syp.size() - 1).observedAt()).isEqualTo(t0840);
        assertThat(syp.stream().filter(c -> c.observedAt().equals(t0840)).findFirst().orElseThrow().levelM())
                .isEqualTo(3.80);
    }

    @Test
    @DisplayName("Speed profile bands are honoured: fast through the calm period, slow through the critical half-hour")
    void speedBandsAreAppliedAsSimTimeAdvances() {
        engine.start("test-run", "aug26-as-happened", null);

        clock.pause();
        clock.setTime(Instant.parse("2026-08-26T02:15:00Z")); // 08:00 NPT -> first band (60x)
        engine.tick();
        assertThat(clock.getSpeed()).isEqualTo(60.0);

        clock.setTime(Instant.parse("2026-08-26T02:40:00Z")); // 08:25 NPT -> still in 08:00-08:36 band
        engine.tick();
        assertThat(clock.getSpeed()).isEqualTo(60.0);

        clock.setTime(Instant.parse("2026-08-26T02:55:00Z")); // 08:40 NPT -> critical half-hour band (2x)
        engine.tick();
        assertThat(clock.getSpeed()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Default-enabled scenario events (the DAO Dhunche phone call) are replayed through the real port")
    void scenarioFieldReportEventIsReplayed() {
        engine.start("test-run", "aug26-as-happened", null);

        clock.pause();
        clock.setTime(Instant.parse("2026-08-26T03:16:00Z")); // 09:01 NPT, just after the 09:00 call
        engine.tick();

        assertThat(fieldReportUseCase.commands).hasSize(1);
        assertThat(fieldReportUseCase.commands.get(0).rawText()).contains("भोटेकोसी");
    }
}
