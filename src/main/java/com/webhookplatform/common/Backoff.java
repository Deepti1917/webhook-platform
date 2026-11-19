package com.webhookplatform.common;

import java.time.Duration;
import java.util.random.RandomGenerator;

/** Exponential backoff with jitter: the result is between half of and the full capped delay. */
public final class Backoff {

    private Backoff() {}

    /**
     * @param attemptsMade number of attempts already made (1 = the first attempt just failed)
     */
    public static Duration delay(int attemptsMade, Duration base, Duration max, RandomGenerator random) {
        int n = Math.max(attemptsMade, 1);
        long baseMs = base.toMillis();
        long maxMs = max.toMillis();
        int shift = Math.min(n - 1, 30);
        long multiplier = 1L << shift;
        long exp = baseMs > Long.MAX_VALUE / multiplier ? maxMs : baseMs * multiplier;
        long capped = Math.min(exp, maxMs);
        long half = capped / 2;
        long jitter = half == 0 ? 0 : random.nextLong(half + 1);
        return Duration.ofMillis(half + jitter);
    }
}
