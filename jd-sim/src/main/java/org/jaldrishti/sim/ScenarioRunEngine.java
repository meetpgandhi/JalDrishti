package org.jaldrishti.sim;

import org.jaldrishti.application.port.in.FieldReportCommand;
import org.jaldrishti.application.port.in.IngestReadingUseCase;
import org.jaldrishti.application.port.in.ManageSimulationUseCase;
import org.jaldrishti.application.port.in.ProcessFieldReportUseCase;
import org.jaldrishti.application.port.in.ReadingCommand;
import org.jaldrishti.application.port.out.LiveTelemetryPublisher;
import org.jaldrishti.domain.AlertTag;
import org.jaldrishti.domain.SenderTrust;
import org.jaldrishti.sim.generator.FlatReadingGenerator;
import org.jaldrishti.sim.generator.HydrographReadingGenerator;
import org.jaldrishti.sim.generator.ReadingGenerator;
import org.jaldrishti.sim.scenario.Scenario;
import org.jaldrishti.sim.scenario.ScenarioLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ScenarioRunEngine — drives a single active simulation run end-to-end.
 *
 * <p>Loads a scenario YAML, precomputes a full reading/event schedule (respecting
 * generator models, pinned [P] readings, outage windows, and variant toggles),
 * then advances the shared {@link SimClock} and replays that schedule through the
 * real ingestion ports ({@link IngestReadingUseCase}, {@link ProcessFieldReportUseCase})
 * as sim time passes each scheduled instant. This is the only way scenario data
 * enters the system — production code paths (TriggerEngine, ActionDispatcherService)
 * are never special-cased for simulation.
 *
 * <p>Mirrors the existing architecture decision (see {@code JalDrishtiWiringConfig})
 * that there is exactly one {@link SimClock} per running instance; this engine holds
 * exactly one active run at a time, consistent with the single-instance demo deployment.
 */
public class ScenarioRunEngine implements ManageSimulationUseCase {

    private static final ZoneOffset NPT = ZoneOffset.ofHoursMinutes(5, 45);
    private static final Pattern VARIANT_REF = Pattern.compile("^\\$\\{variants\\.(\\w+)\\}$");
    private static final Pattern ENABLED_WHEN_REF = Pattern.compile("^variants\\.(\\w+)$");
    private static final int DEFAULT_CADENCE_MIN = 10;

    private final SimClock clock;
    private final ScenarioLoader scenarioLoader;
    private final IngestReadingUseCase readingUseCase;
    private final ProcessFieldReportUseCase fieldReportUseCase;
    private final SimScheduler simScheduler;
    private final LiveTelemetryPublisher liveTelemetryPublisher;
    private final String corridorId;

    private volatile RunContext current;

    public ScenarioRunEngine(
            SimClock clock,
            IngestReadingUseCase readingUseCase,
            ProcessFieldReportUseCase fieldReportUseCase,
            SimScheduler simScheduler,
            LiveTelemetryPublisher liveTelemetryPublisher,
            String corridorId
    ) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.scenarioLoader = new ScenarioLoader();
        this.readingUseCase = readingUseCase;
        this.fieldReportUseCase = fieldReportUseCase;
        this.simScheduler = simScheduler;
        this.liveTelemetryPublisher = liveTelemetryPublisher;
        this.corridorId = corridorId;
    }

    private static final class RunContext {
        final String runId;
        final Scenario scenario;
        final Instant startInstant;
        final Instant endInstant;
        final List<ScheduledItem> schedule;
        final List<SpeedBandWindow> speedBands;
        int cursor = 0;
        volatile String status = "RUNNING";
        volatile Double manualSpeed = null;

        RunContext(String runId, Scenario scenario, Instant startInstant, Instant endInstant,
                   List<ScheduledItem> schedule, List<SpeedBandWindow> speedBands) {
            this.runId = runId;
            this.scenario = scenario;
            this.startInstant = startInstant;
            this.endInstant = endInstant;
            this.schedule = schedule;
            this.speedBands = speedBands;
        }
    }

    private record SpeedBandWindow(Instant from, Instant to, double speed) {}

    private record ScheduledItem(
            Instant at,
            Kind kind,
            String stationId,
            double levelM,
            double rainMm,
            AlertTag tag,
            String reportText,
            SenderTrust trust
    ) {
        enum Kind { READING, FIELD_REPORT }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public control surface
    // ─────────────────────────────────────────────────────────────────────────

    public synchronized RunSnapshot start(String runId, String scenarioName, Map<String, Object> variantOverrides) {
        Scenario scenario = loadScenario(scenarioName);

        Map<String, Object> variants = new HashMap<>(scenario.variants());
        if (variantOverrides != null) {
            variants.putAll(variantOverrides);
        }

        Instant startInstant = OffsetDateTime.parse(scenario.start()).toInstant();
        Instant endInstant = OffsetDateTime.parse(scenario.end()).toInstant();
        LocalDate scenarioDate = OffsetDateTime.parse(scenario.start()).toLocalDate();

        List<ScheduledItem> schedule = buildSchedule(scenario, variants, scenarioDate, startInstant, endInstant);
        List<SpeedBandWindow> speedBands = buildSpeedBands(scenario, scenarioDate);

        RunContext ctx = new RunContext(runId, scenario, startInstant, endInstant, schedule, speedBands);
        this.current = ctx;

        clock.setTime(startInstant);
        clock.setSpeed(speedAt(speedBands, startInstant));
        clock.start();

        return snapshotOf(ctx);
    }

    public synchronized RunSnapshot play(String runId) {
        if (current != null && current.runId.equals(runId)) {
            clock.resume();
            current.status = "RUNNING";
            return snapshotOf(current);
        }
        return null;
    }

    public synchronized RunSnapshot pause(String runId) {
        if (current != null && current.runId.equals(runId)) {
            clock.pause();
            current.status = "PAUSED";
            return snapshotOf(current);
        }
        return null;
    }

    public synchronized RunSnapshot setSpeed(String runId, double speed) {
        if (current != null && current.runId.equals(runId)) {
            current.manualSpeed = speed;
            clock.setSpeed(speed);
            return snapshotOf(current);
        }
        return null;
    }

    public synchronized RunSnapshot snapshot(String runId) {
        if (current != null && current.runId.equals(runId)) {
            return snapshotOf(current);
        }
        return null;
    }

    public synchronized List<TimelineMark> getAsHappenedTimeline(String runId) {
        if (current == null || !current.runId.equals(runId)) {
            return List.of();
        }
        LocalDate date = OffsetDateTime.parse(current.scenario.start()).toLocalDate();
        Map<String, Object> variants = current.scenario.variants();
        List<TimelineMark> marks = new ArrayList<>();
        for (Map<String, Object> entry : current.scenario.asHappened()) {
            Object t = entry.get("t");
            Instant at = resolveTimeExpr(t != null ? t.toString() : null, variants, date);
            if (at != null) {
                Object label = entry.getOrDefault("label", "");
                Object tag = entry.getOrDefault("tag", "P");
                marks.add(new TimelineMark(at, String.valueOf(label), String.valueOf(tag)));
            }
        }
        marks.sort(Comparator.comparing(TimelineMark::at));
        return marks;
    }

    /**
     * Advances the active run: ingests any scheduled items now due, runs the
     * silence-sweep/escalation tick, and publishes a clock update. Called
     * periodically by a scheduler bean; no-op when no run is active or paused.
     */
    public synchronized void tick() {
        RunContext ctx = current;
        if (ctx == null) {
            return;
        }
        if (!"RUNNING".equals(ctx.status)) {
            return;
        }

        Instant now = clock.instant();

        double desiredSpeed = ctx.manualSpeed != null ? ctx.manualSpeed : speedAt(ctx.speedBands, now);
        if (Math.abs(desiredSpeed - clock.getSpeed()) > 1e-9) {
            clock.setSpeed(desiredSpeed);
        }

        while (ctx.cursor < ctx.schedule.size() && !ctx.schedule.get(ctx.cursor).at().isAfter(now)) {
            ScheduledItem item = ctx.schedule.get(ctx.cursor);
            try {
                processItem(ctx, item);
            } catch (Exception e) {
                System.err.println("Error processing scenario item at " + item.at() + ": " + e.getMessage());
            } finally {
                ctx.cursor++;
            }
        }

        if (simScheduler != null) {
            simScheduler.tick(ctx.runId, corridorId);
        }

        if (liveTelemetryPublisher != null) {
            liveTelemetryPublisher.publishClock(ctx.runId, new LiveTelemetryPublisher.ClockUpdate(
                    ctx.runId, ctx.scenario.scenario(), now, clock.getSpeed(), ctx.status
            ));
        }

        if (!now.isBefore(ctx.endInstant)) {
            ctx.status = "COMPLETE";
            clock.pause();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Schedule processing
    // ─────────────────────────────────────────────────────────────────────────

    private void processItem(RunContext ctx, ScheduledItem item) {
        switch (item.kind()) {
            case READING -> {
                if (readingUseCase != null) {
                    readingUseCase.ingest(new ReadingCommand(
                            ctx.runId, item.stationId(), item.at(), item.levelM(), item.rainMm(),
                            "SIM:" + ctx.scenario.scenario(), item.tag()
                    ));
                }
            }
            case FIELD_REPORT -> {
                if (fieldReportUseCase != null) {
                    fieldReportUseCase.processFieldReport(new FieldReportCommand(
                            ctx.runId, item.at(), "scenario-event", item.trust(), "scenario_event", null, item.reportText()
                    ));
                }
            }
        }
    }

    private double speedAt(List<SpeedBandWindow> bands, Instant now) {
        double speed = 1.0;
        for (SpeedBandWindow band : bands) {
            if (!now.isBefore(band.from()) && now.isBefore(band.to())) {
                return band.speed();
            }
            if (!now.isBefore(band.from())) {
                speed = band.speed();
            }
        }
        return speed;
    }

    private List<SpeedBandWindow> buildSpeedBands(Scenario scenario, LocalDate date) {
        List<SpeedBandWindow> bands = new ArrayList<>();
        for (Scenario.SpeedBand band : scenario.speedProfile()) {
            Instant from = resolveTimeExpr(band.from(), scenario.variants(), date);
            Instant to = resolveTimeExpr(band.to(), scenario.variants(), date);
            if (from != null && to != null) {
                bands.add(new SpeedBandWindow(from, to, band.speed()));
            }
        }
        bands.sort(Comparator.comparing(SpeedBandWindow::from));
        return bands;
    }

    private List<ScheduledItem> buildSchedule(
            Scenario scenario, Map<String, Object> variants, LocalDate date, Instant start, Instant end
    ) {
        List<ScheduledItem> items = new ArrayList<>();

        for (Map.Entry<String, Scenario.StationConfig> entry : scenario.stations().entrySet()) {
            String stationId = entry.getKey();
            items.addAll(buildStationSchedule(stationId, entry.getValue(), variants, date, start, end));
        }

        for (Scenario.ScenarioEvent event : scenario.events()) {
            if (!"FIELD_REPORT".equalsIgnoreCase(event.type())) {
                continue; // SEISMIC and other event types are not yet wired to an ingestion port
            }
            if (!resolveEnabledWhen(event.enabledWhen(), variants)) {
                continue;
            }
            Instant at = resolveTimeExpr(event.t(), variants, date);
            if (at == null) {
                continue;
            }
            SenderTrust trust = event.trust() != null
                    ? SenderTrust.valueOf(event.trust().toUpperCase())
                    : SenderTrust.OBSERVER;
            items.add(new ScheduledItem(at, ScheduledItem.Kind.FIELD_REPORT, null, 0, 0, AlertTag.A,
                    event.text(), trust));
        }

        items.sort(Comparator.comparing(ScheduledItem::at));
        return items;
    }

    private List<ScheduledItem> buildStationSchedule(
            String stationId, Scenario.StationConfig config, Map<String, Object> variants,
            LocalDate date, Instant start, Instant end
    ) {
        Map<Instant, ScheduledItem> byInstant = new HashMap<>();

        ReadingGenerator generator = buildGenerator(config.generator(), variants, date);
        if (generator != null) {
            long cadenceSeconds = Duration.ofMinutes(DEFAULT_CADENCE_MIN).toSeconds();
            for (Instant t = start; !t.isAfter(end); t = t.plusSeconds(cadenceSeconds)) {
                ReadingGenerator.GeneratedValue value = generator.generate(t, start);
                byInstant.put(t, new ScheduledItem(t, ScheduledItem.Kind.READING, stationId,
                        value.levelM(), 0.0, value.tag(), null, null));
            }
        }

        Instant lastPinnedAt = null;
        if (config.pinned() != null) {
            for (Scenario.PinnedReading pinned : config.pinned()) {
                Instant at = resolveTimeExpr(pinned.t(), variants, date);
                if (at == null) {
                    continue;
                }
                AlertTag tag = parseTag(pinned.tag(), AlertTag.P);
                byInstant.put(at, new ScheduledItem(at, ScheduledItem.Kind.READING, stationId,
                        pinned.level(), 0.0, tag, null, null));
                if (lastPinnedAt == null || at.isAfter(lastPinnedAt)) {
                    lastPinnedAt = at;
                }
            }
        }

        Instant outageFrom = null;
        if (config.outage() != null) {
            Scenario.OutageConfig outage = config.outage();
            if (outage.fromSlot() != null) {
                outageFrom = resolveTimeExpr(outage.fromSlot(), variants, date);
            } else if (outage.afterLastPinned() && lastPinnedAt != null) {
                outageFrom = lastPinnedAt.plusSeconds(1);
            }
        }

        List<ScheduledItem> result = new ArrayList<>();
        for (Map.Entry<Instant, ScheduledItem> e : byInstant.entrySet()) {
            if (outageFrom != null && !e.getKey().isBefore(outageFrom)) {
                continue; // station is silent from this point on
            }
            result.add(e.getValue());
        }
        return result;
    }

    private ReadingGenerator buildGenerator(Map<String, Object> generator, Map<String, Object> variants, LocalDate date) {
        if (generator == null) {
            return null;
        }
        String model = String.valueOf(generator.get("model"));
        long seed = asLong(generator.get("seed"), 42L);

        if ("flat".equals(model)) {
            double level = asDouble(generator.get("level"), 0.0);
            double trend = asDouble(generator.get("trendPer10Min"), 0.0);
            double noiseSd = asDouble(generator.get("noiseSd"), 0.0);
            AlertTag tag = parseTag((String) generator.get("tag"), AlertTag.A);
            return new FlatReadingGenerator(level, trend, noiseSd, tag, seed);
        }

        if ("hydrograph".equals(model)) {
            double base = asDouble(generator.get("base"), 0.0);
            AlertTag tag = parseTag((String) generator.get("tag"), AlertTag.A);
            Double mustStayBelow = generator.containsKey("mustStayBelow") ? asDouble(generator.get("mustStayBelow"), 0.0) : null;

            if (generator.containsKey("jumpAt")) {
                Instant jumpAt = resolveTimeExpr(String.valueOf(generator.get("jumpAt")), variants, date);
                double jumpM = asDouble(generator.get("jumpM"), 0.0);
                double peakAfterMin = asDouble(generator.get("peakAfterMin"), 30.0);
                double recessionHalfLifeMin = asDouble(generator.get("recessionHalfLifeMin"), 90.0);
                if (jumpAt != null) {
                    return new HydrographReadingGenerator(base, jumpAt, jumpM, peakAfterMin, recessionHalfLifeMin, mustStayBelow, tag);
                }
            } else if (generator.containsKey("riseStart") && generator.get("peak") instanceof Map) {
                Instant riseStart = resolveTimeExpr(String.valueOf(generator.get("riseStart")), variants, date);
                @SuppressWarnings("unchecked")
                Map<String, Object> peak = (Map<String, Object>) generator.get("peak");
                Instant peakAt = resolveTimeExpr(String.valueOf(peak.get("t")), variants, date);
                double peakLevel = asDouble(peak.get("level"), base);
                if (riseStart != null && peakAt != null) {
                    double jumpM = peakLevel - base;
                    double peakAfterMin = Math.max(1.0, Duration.between(riseStart, peakAt).toSeconds() / 60.0);
                    // Recession rate beyond the scenario's peak is not published [P]; a slow 180-min
                    // half-life keeps the level from unrealistically snapping back within the run window.
                    return new HydrographReadingGenerator(base, riseStart, jumpM, peakAfterMin, 180.0, mustStayBelow, tag);
                }
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Variant / expression resolution
    // ─────────────────────────────────────────────────────────────────────────

    private Instant resolveTimeExpr(String expr, Map<String, Object> variants, LocalDate date) {
        if (expr == null) {
            return null;
        }
        String resolved = expr;
        Matcher m = VARIANT_REF.matcher(expr);
        if (m.matches()) {
            Object v = variants.get(m.group(1));
            if (v == null) {
                return null;
            }
            resolved = v.toString();
        }
        if (resolved.matches("\\d{1,2}:\\d{2}")) {
            LocalTime time = LocalTime.parse(resolved.length() == 4 ? "0" + resolved : resolved);
            return date.atTime(time).atOffset(NPT).toInstant();
        }
        try {
            return OffsetDateTime.parse(resolved).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean resolveEnabledWhen(String expr, Map<String, Object> variants) {
        if (expr == null) {
            return true;
        }
        Matcher m = ENABLED_WHEN_REF.matcher(expr.trim());
        if (m.matches()) {
            Object v = variants.get(m.group(1));
            return v == null || Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
        }
        return true;
    }

    private AlertTag parseTag(String tag, AlertTag fallback) {
        if (tag == null) {
            return fallback;
        }
        try {
            return AlertTag.valueOf(tag.trim().split("[\\s(]")[0]);
        } catch (Exception e) {
            return fallback;
        }
    }

    private double asDouble(Object o, double fallback) {
        if (o == null) {
            return fallback;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return fallback;
        }
    }

    private long asLong(Object o, long fallback) {
        if (o == null) {
            return fallback;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return fallback;
        }
    }

    private RunSnapshot snapshotOf(RunContext ctx) {
        return new RunSnapshot(ctx.runId, ctx.scenario.scenario(), ctx.status, clock.getSpeed(),
                clock.instant(), ctx.startInstant, ctx.endInstant);
    }

    private Scenario loadScenario(String name) {
        String resourcePath = "scenarios/" + name + ".yaml";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in != null) {
                return scenarioLoader.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load scenario from classpath: " + resourcePath, e);
        }
        File file = new File("scenarios/" + name + ".yaml");
        if (!file.exists()) {
            file = new File("../scenarios/" + name + ".yaml");
        }
        try {
            return scenarioLoader.load(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load scenario: " + name, e);
        }
    }
}
