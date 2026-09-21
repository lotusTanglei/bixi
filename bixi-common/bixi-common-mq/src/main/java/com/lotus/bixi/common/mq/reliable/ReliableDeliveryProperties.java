package com.lotus.bixi.common.mq.reliable;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/** Immutable settings; constructing this value does not register beans or start polling. */
public record ReliableDeliveryProperties(Duration pollInterval, Duration leaseDuration, Duration sendTimeout,
        int batchSize, int maxAttempts) {

    private static final int[] BACKOFF_SECONDS = {1, 2, 4, 8, 16, 32, 60, 120, 240, 300};

    public ReliableDeliveryProperties {
        requirePositive(pollInterval, "pollInterval");
        requirePositive(leaseDuration, "leaseDuration");
        requirePositive(sendTimeout, "sendTimeout");
        if (sendTimeout.compareTo(Duration.ofSeconds(5)) > 0 || leaseDuration.compareTo(sendTimeout) <= 0) {
            throw new IllegalArgumentException("Send timeout must be at most five seconds and shorter than the lease");
        }
        if (batchSize < 1 || batchSize > 20 || maxAttempts < 1 || maxAttempts > 12) {
            throw new IllegalArgumentException("Batch size must be 1..20 and max attempts 1..12");
        }
    }

    public static ReliableDeliveryProperties defaults() {
        return new ReliableDeliveryProperties(Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofSeconds(5), 20, 12);
    }

    /** Attempt is the persisted number of sends already claimed, starting at one. */
    public Duration retryDelay(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("Attempt must be positive");
        }
        long micros = BACKOFF_SECONDS[Math.min(attempt - 1, BACKOFF_SECONDS.length - 1)] * 1_000_000L;
        return Duration.ofNanos((micros + ThreadLocalRandom.current().nextLong(micros / 5 + 1)) * 1000);
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero() || value.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException(name + " must be positive and at most one day");
        }
    }
}
