package com.webhookplatform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void allowsBurstThenBlocksThenRefills() {
        AtomicLong clock = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(60, 100, clock::get); // 1 token/second, burst 60
        for (int i = 0; i < 60; i++) {
            assertTrue(limiter.tryAcquire("a"), "request " + i);
        }
        assertFalse(limiter.tryAcquire("a"));
        assertFalse(limiter.hasCapacity("a"));

        clock.addAndGet(2 * SECOND);
        assertTrue(limiter.hasCapacity("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
    }

    @Test
    void keysAreIndependent() {
        AtomicLong clock = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(1, 100, clock::get);
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
    }

    @Test
    void neverExceedsCapacityAfterLongIdle() {
        AtomicLong clock = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(5, 100, clock::get);
        clock.addAndGet(3600 * SECOND);
        int allowed = 0;
        for (int i = 0; i < 20; i++) {
            if (limiter.tryAcquire("a")) {
                allowed++;
            }
        }
        assertEquals(5, allowed);
    }

    @Test
    void tableIsBoundedAndFailsClosedWhenFullOfActiveKeys() {
        AtomicLong clock = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(10, 3, clock::get);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
        assertTrue(limiter.tryAcquire("c"));
        assertFalse(limiter.tryAcquire("d")); // table full of recently used keys
        assertEquals(3, limiter.size());

        clock.addAndGet(60 * SECOND); // everything refills, idle entries can be evicted
        assertTrue(limiter.tryAcquire("d"));
        assertTrue(limiter.size() <= 3);
    }

    @Test
    void retryAfterIsSensible() {
        assertEquals(1, new RateLimiter(600, 10).retryAfterSeconds());
        assertEquals(60, new RateLimiter(1, 10).retryAfterSeconds());
    }
}
