package org.jaldrishti.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlineCalculatorTest {

    private final Instant now = Instant.parse("2026-08-26T08:53:00Z");

    @Test
    void evaluatesMinExpressionWhenEtaMinusIsSooner() {
        // eta = 09:05 -> eta - 15m = 08:50 (is before now + 10m = 09:03)
        Instant eta = Instant.parse("2026-08-26T09:05:00Z");
        Instant deadline = DeadlineCalculator.calculateDeadline("min(etaEarliest - 15m, now + 10m)", now, eta);

        assertThat(deadline).isEqualTo(Instant.parse("2026-08-26T08:50:00Z"));
    }

    @Test
    void evaluatesMinExpressionWhenNowPlusIsSooner() {
        // eta = 09:40 -> eta - 15m = 09:25; now + 10m = 09:03
        Instant eta = Instant.parse("2026-08-26T09:40:00Z");
        Instant deadline = DeadlineCalculator.calculateDeadline("min(etaEarliest - 15m, now + 10m)", now, eta);

        assertThat(deadline).isEqualTo(Instant.parse("2026-08-26T09:03:00Z"));
    }

    @Test
    void evaluatesNowPlusExpression() {
        Instant deadline = DeadlineCalculator.calculateDeadline("now + 15 min", now, null);
        assertThat(deadline).isEqualTo(Instant.parse("2026-08-26T09:08:00Z"));
    }

    @Test
    void evaluatesEtaMinusExpression() {
        Instant eta = Instant.parse("2026-08-26T10:00:00Z");
        Instant deadline = DeadlineCalculator.calculateDeadline("earliest arrival - 20 min", now, eta);
        assertThat(deadline).isEqualTo(Instant.parse("2026-08-26T09:40:00Z"));
    }

    @Test
    void evaluatesNoneExpression() {
        Instant deadline = DeadlineCalculator.calculateDeadline("none", now, null);
        assertThat(deadline).isNull();
    }

    @Test
    void compressesEscalationIntervalWhenDeadlineIsClose() {
        // 12 minutes left = 720 seconds -> 720 / 3 = 240 seconds = 4 minutes
        Instant deadline = now.plus(Duration.ofMinutes(12));
        Duration step = DeadlineCalculator.calculateEscalationInterval(now, deadline, Duration.ofMinutes(5));

        assertThat(step).isEqualTo(Duration.ofMinutes(4));
    }

    @Test
    void defaultsEscalationIntervalWhenDeadlineTooFar() {
        // 60 minutes left -> 60 / 3 = 20 min -> capped by default 5 min
        Instant deadline = now.plus(Duration.ofMinutes(60));
        Duration step = DeadlineCalculator.calculateEscalationInterval(now, deadline, Duration.ofMinutes(5));

        assertThat(step).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void handlesPastDeadline() {
        Instant pastDeadline = now.minus(Duration.ofMinutes(2));
        Duration step = DeadlineCalculator.calculateEscalationInterval(now, pastDeadline, Duration.ofMinutes(5));

        assertThat(step).isEqualTo(Duration.ofSeconds(60));
    }
}
