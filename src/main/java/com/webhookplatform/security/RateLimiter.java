package com.webhookplatform.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory token bucket, one bucket per key (IP address or tenant id).
 * Capacity = perMinute, refilled continuously. The number of tracked keys is bounded so an attacker
 * cannot exhaust memory by inventing keys.
 *
 * NOTE: state is per application instance. With several instances behind a load balancer the effective
 * limit is multiplied; move to Redis (e.g. Bucket4j + Redis) when you scale out.
 */
public final class RateLimiter {

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefillNanos = now;
        }
    }

    private final int capacity;
    private final double refillPerNano;
    private final int maxKeys;
    private final LongSupplier nanoClock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int perMinute, int maxKeys) {
        this(perMinute, maxKeys, System::nanoTime);
    }

    public RateLimiter(int perMinute, int maxKeys, LongSupplier nanoClock) {
        if (perMinute < 1 || maxKeys < 1) {
            throw new IllegalArgumentException("perMinute and maxKeys must be >= 1");
        }
        this.capacity = perMinute;
        this.refillPerNano = perMinute / 60_000_000_000.0;
        this.maxKeys = maxKeys;
        this.nanoClock = nanoClock;
    }

    /** Consumes one token. Returns false when the key is over its limit. */
    public boolean tryAcquire(String key) {
        long now = nanoClock.getAsLong();
        Bucket bucket = bucketFor(key, now);
        if (bucket == null) {
            return false; // table full of active keys: fail closed
        }
        synchronized (bucket) {
            refill(bucket, now);
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    /** Non-consuming check. */
    public boolean hasCapacity(String key) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            return true;
        }
        long now = nanoClock.getAsLong();
        synchronized (bucket) {
            refill(bucket, now);
            return bucket.tokens >= 1.0;
        }
    }

    public int retryAfterSeconds() {
        return (int) Math.max(1, Math.ceil(60.0 / capacity));
    }

    int size() {
        return buckets.size();
    }

    private Bucket bucketFor(String key, long now) {
        Bucket existing = buckets.get(key);
        if (existing != null) {
            return existing;
        }
        if (buckets.size() >= maxKeys) {
            evictIdle(now);
            if (buckets.size() >= maxKeys) {
                return null;
            }
        }
        return buckets.computeIfAbsent(key, k -> new Bucket(capacity, now));
    }

    private void evictIdle(long now) {
        buckets.entrySet().removeIf(entry -> {
            Bucket b = entry.getValue();
            synchronized (b) {
                refill(b, now);
                return b.tokens >= capacity; // fully refilled: carries no information
            }
        });
    }

    private void refill(Bucket b, long now) {
        long elapsed = now - b.lastRefillNanos;
        if (elapsed > 0) {
            b.tokens = Math.min(capacity, b.tokens + elapsed * refillPerNano);
            b.lastRefillNanos = now;
        }
    }
}
