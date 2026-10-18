package org.jaldrishti.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure Java domain utility to evaluate duty action deadline expressions
 * and calculate dynamic escalation intervals.
 */
public final class DeadlineCalculator {

    private static final Pattern MIN_EXPR_PATTERN = Pattern.compile(
            "min\\s*\\(\\s*(?:etaEarliest|earliest arrival)\\s*[-−]\\s*(\\d+)\\s*(?:m|min)?\\s*,\\s*now\\s*\\+\\s*(\\d+)\\s*(?:m|min)?\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ETA_MINUS_PATTERN = Pattern.compile(
            "(?:etaEarliest|earliest arrival)\\s*[-−]\\s*(\\d+)\\s*(?:m|min)?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NOW_PLUS_PATTERN = Pattern.compile(
            "now\\s*\\+\\s*(\\d+)\\s*(?:m|min)?",
            Pattern.CASE_INSENSITIVE
    );

    private DeadlineCalculator() {
        // Utility class
    }

    /**
     * Evaluates a deadline expression for a given simulation or real timestamp and site arrival time.
     *
     * @param expression  Expression from duty rule (e.g., "min(etaEarliest - 15m, now + 10m)", "now + 15 min")
     * @param now         Current time
     * @param etaEarliest Earliest flood wave arrival time at target site, or null if unknown
     * @return Calculated deadline timestamp, or null if expression is "none" or unparseable
     */
    public static Instant calculateDeadline(String expression, Instant now, Instant etaEarliest) {
        if (expression == null || expression.isBlank() || expression.equalsIgnoreCase("none")) {
            return null;
        }

        String clean = expression.trim();

        // 1. min(etaEarliest - Xm, now + Ym)
        Matcher minMatcher = MIN_EXPR_PATTERN.matcher(clean);
        if (minMatcher.find()) {
            long minusMin = Long.parseLong(minMatcher.group(1));
            long plusMin = Long.parseLong(minMatcher.group(2));
            Instant nowPlus = now.plus(Duration.ofMinutes(plusMin));
            if (etaEarliest == null) {
                return nowPlus;
            }
            Instant etaMinus = etaEarliest.minus(Duration.ofMinutes(minusMin));
            return etaMinus.isBefore(nowPlus) ? etaMinus : nowPlus;
        }

        // 2. etaEarliest - Xm
        Matcher etaMatcher = ETA_MINUS_PATTERN.matcher(clean);
        if (etaMatcher.find()) {
            long minusMin = Long.parseLong(etaMatcher.group(1));
            if (etaEarliest != null) {
                return etaEarliest.minus(Duration.ofMinutes(minusMin));
            }
            return now.plus(Duration.ofMinutes(10)); // Default safe fallback
        }

        // 3. now + Xm
        Matcher nowMatcher = NOW_PLUS_PATTERN.matcher(clean);
        if (nowMatcher.find()) {
            long plusMin = Long.parseLong(nowMatcher.group(1));
            return now.plus(Duration.ofMinutes(plusMin));
        }

        // Default fallback if unparseable
        return now.plus(Duration.ofMinutes(15));
    }

    /**
     * Calculates the compressed escalation interval based on Section 9:
     * step interval = min(5 min, (deadline − now) / 3).
     *
     * @param now             Current timestamp
     * @param deadline        Target action deadline, or null
     * @param defaultInterval Default uncompressed interval (typically 5 min)
     * @return Duration for the next escalation step
     */
    public static Duration calculateEscalationInterval(Instant now, Instant deadline, Duration defaultInterval) {
        if (deadline == null || !deadline.isAfter(now)) {
            return Duration.ofSeconds(60); // Urgent: past or at deadline
        }

        long remainingSec = Duration.between(now, deadline).toSeconds();
        long compressedSec = remainingSec / 3;
        long defaultSec = defaultInterval != null ? defaultInterval.toSeconds() : 300;

        long effectiveSec = Math.min(defaultSec, compressedSec);
        return Duration.ofSeconds(Math.max(30, effectiveSec)); // Floor at 30 seconds
    }
}
