package org.jaldrishti.sim;

import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * SimClock — Injectable virtual clock implementing {@link Clock}.
 *
 * <p>Mechanics:
 * <pre>
 *   States: STOPPED | RUNNING | PAUSED
 *   sim_time = anchorSimTime + (wallNow - anchorWallTime) * speed
 * </pre>
 *
 * <p>When paused, resumed, or speed changes, both anchorSimTime and anchorWallTime
 * are re-anchored so elapsed virtual time is preserved deterministically.
 */
public class SimClock extends Clock implements Serializable {

    public enum State {
        STOPPED,
        RUNNING,
        PAUSED
    }

    private final ZoneId zone;
    private State state;
    private Instant anchorSimTime;
    private Instant anchorWallTime;
    private double speed;

    public SimClock(ZoneId zone, Instant startSimTime) {
        this.zone = zone != null ? zone : ZoneId.of("+05:45");
        this.state = State.STOPPED;
        this.anchorSimTime = startSimTime != null ? startSimTime : Instant.now();
        this.anchorWallTime = Instant.now();
        this.speed = 1.0;
    }

    public static SimClock create(Instant startSimTime) {
        return new SimClock(ZoneId.of("+05:45"), startSimTime);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (zone.equals(this.zone)) {
            return this;
        }
        SimClock copy = new SimClock(zone, this.anchorSimTime);
        synchronized (this) {
            copy.state = this.state;
            copy.anchorSimTime = this.anchorSimTime;
            copy.anchorWallTime = this.anchorWallTime;
            copy.speed = this.speed;
        }
        return copy;
    }

    @Override
    public synchronized Instant instant() {
        if (state == State.STOPPED || state == State.PAUSED) {
            return anchorSimTime;
        }
        Instant wallNow = Instant.now();
        long wallElapsedNanos = Duration.between(anchorWallTime, wallNow).toNanos();
        long simElapsedNanos = Math.round(wallElapsedNanos * speed);
        return anchorSimTime.plusNanos(simElapsedNanos);
    }

    public synchronized void start() {
        if (state != State.RUNNING) {
            this.anchorWallTime = Instant.now();
            this.state = State.RUNNING;
        }
    }

    public synchronized void pause() {
        if (state == State.RUNNING) {
            this.anchorSimTime = instant();
            this.anchorWallTime = Instant.now();
            this.state = State.PAUSED;
        }
    }

    public synchronized void resume() {
        if (state == State.PAUSED || state == State.STOPPED) {
            this.anchorWallTime = Instant.now();
            this.state = State.RUNNING;
        }
    }

    public synchronized void stop() {
        this.anchorSimTime = instant();
        this.anchorWallTime = Instant.now();
        this.state = State.STOPPED;
    }

    public synchronized void setSpeed(double newSpeed) {
        if (newSpeed <= 0.0) {
            throw new IllegalArgumentException("Simulation speed must be positive, got: " + newSpeed);
        }
        if (state == State.RUNNING) {
            this.anchorSimTime = instant();
            this.anchorWallTime = Instant.now();
        }
        this.speed = newSpeed;
    }

    public synchronized void setTime(Instant newSimTime) {
        Objects.requireNonNull(newSimTime, "newSimTime must not be null");
        this.anchorSimTime = newSimTime;
        this.anchorWallTime = Instant.now();
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized double getSpeed() {
        return speed;
    }

    public synchronized Instant getAnchorSimTime() {
        return anchorSimTime;
    }

    public synchronized Instant getAnchorWallTime() {
        return anchorWallTime;
    }
}
