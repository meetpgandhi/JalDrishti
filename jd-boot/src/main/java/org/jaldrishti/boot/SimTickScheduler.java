package org.jaldrishti.boot;

import org.jaldrishti.sim.ScenarioRunEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the active simulation run forward in real time, replacing the manual
 * {@code POST /internal/sweep} trigger with an automatic tick loop. Only active
 * under the {@code sim} profile; in {@code live} mode, Cloud Scheduler calls the
 * internal sweep/escalate endpoints directly instead.
 */
@Component
@Profile("sim")
public class SimTickScheduler {

    private final ScenarioRunEngine scenarioRunEngine;

    public SimTickScheduler(ScenarioRunEngine scenarioRunEngine) {
        this.scenarioRunEngine = scenarioRunEngine;
    }

    @Scheduled(fixedRate = 500)
    public void tick() {
        scenarioRunEngine.tick();
    }
}
