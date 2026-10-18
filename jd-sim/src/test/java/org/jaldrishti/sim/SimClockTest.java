package org.jaldrishti.sim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SimClockTest {

    @Test
    @DisplayName("SimClock initial state is STOPPED and preserves start time")
    void initialStateIsStopped() {
        Instant t0 = Instant.parse("2026-08-26T08:00:00+05:45");
        SimClock clock = SimClock.create(t0);

        assertThat(clock.getState()).isEqualTo(SimClock.State.STOPPED);
        assertThat(clock.instant()).isEqualTo(t0);
        assertThat(clock.getSpeed()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("SimClock advances when running and accelerates with speed multiplier")
    void runningClockAdvancesWithSpeed() throws InterruptedException {
        Instant t0 = Instant.parse("2026-08-26T08:00:00+05:45");
        SimClock clock = SimClock.create(t0);
        clock.setSpeed(10.0); // 10x speed
        clock.start();

        assertThat(clock.getState()).isEqualTo(SimClock.State.RUNNING);

        Thread.sleep(50); // 50ms wall time -> 500ms sim time (0.5s)
        Instant t1 = clock.instant();

        assertThat(t1).isAfter(t0);
        long elapsedMillis = t1.toEpochMilli() - t0.toEpochMilli();
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(300); // Allow thread scheduling jitter
    }

    @Test
    @DisplayName("SimClock freezes time when PAUSED and resumes without losing position")
    void pauseAndResumePreservesTime() throws InterruptedException {
        Instant t0 = Instant.parse("2026-08-26T08:00:00+05:45");
        SimClock clock = SimClock.create(t0);
        clock.start();

        Thread.sleep(20);
        clock.pause();

        assertThat(clock.getState()).isEqualTo(SimClock.State.PAUSED);
        Instant pausedTime = clock.instant();

        Thread.sleep(50);
        // Time must stay strictly frozen while paused
        assertThat(clock.instant()).isEqualTo(pausedTime);

        clock.resume();
        assertThat(clock.getState()).isEqualTo(SimClock.State.RUNNING);

        Thread.sleep(20);
        assertThat(clock.instant()).isAfter(pausedTime);
    }
}
