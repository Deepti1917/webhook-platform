package com.webhookplatform.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

class BackoffTest {

    private static final Duration BASE = Duration.ofSeconds(10);
    private static final Duration MAX = Duration.ofHours(1);

    @Test
    void growsExponentiallyWithinJitterBounds() {
        for (int attempt = 1; attempt <= 6; attempt++) {
            long full = BASE.toMillis() * (1L << (attempt - 1));
            for (int i = 0; i < 200; i++) {
                long ms = Backoff.delay(attempt, BASE, MAX, ThreadLocalRandom.current()).toMillis();
                assertTrue(ms >= full / 2 && ms <= full, "attempt " + attempt + " gave " + ms);
            }
        }
    }

    @Test
    void isCappedAndNeverOverflows() {
        for (int attempt : new int[] {12, 40, 1000, Integer.MAX_VALUE}) {
            long ms = Backoff.delay(attempt, BASE, MAX, ThreadLocalRandom.current()).toMillis();
            assertTrue(ms >= MAX.toMillis() / 2 && ms <= MAX.toMillis(), "attempt " + attempt + " gave " + ms);
        }
        long huge = Backoff.delay(31, Duration.ofDays(365_000), MAX, ThreadLocalRandom.current()).toMillis();
        assertTrue(huge >= 0 && huge <= MAX.toMillis());
    }

    @Test
    void treatsZeroAndNegativeAsFirstAttempt() {
        long ms = Backoff.delay(0, BASE, MAX, ThreadLocalRandom.current()).toMillis();
        assertTrue(ms >= 5_000 && ms <= 10_000);
        assertEquals(0, Backoff.delay(1, Duration.ZERO, MAX, ThreadLocalRandom.current()).toMillis());
    }
}
