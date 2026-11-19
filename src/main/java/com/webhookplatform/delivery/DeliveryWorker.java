package com.webhookplatform.delivery;

import com.webhookplatform.common.Backoff;
import com.webhookplatform.config.AppProperties;
import com.webhookplatform.endpoint.WebhookEndpoint;
import com.webhookplatform.endpoint.WebhookEndpointRepository;
import com.webhookplatform.event.Event;
import com.webhookplatform.event.EventRepository;
import com.webhookplatform.security.HmacSigner;
import com.webhookplatform.security.SecretCipher;
import com.webhookplatform.security.UnsafeUrlException;
import com.webhookplatform.security.UrlGuard;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The delivery engine.
 *
 * 1. CLAIM: one UPDATE ... WHERE id IN (SELECT ... FOR UPDATE SKIP LOCKED) picks due deliveries and pushes
 *    their next_attempt_at forward by a lease. Rows locked by another worker are skipped, so several
 *    instances never take the same delivery. If a worker crashes, the lease expires and the row is retried.
 * 2. SEND: outside any DB transaction, on virtual threads, with timeouts, no redirects, and an SSRF re-check.
 * 3. RECORD: a short transaction stores the outcome: SUCCESS, retry with backoff, or DEAD.
 */
@Component
public class DeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);

    private static final String CLAIM_SQL =
            """
            UPDATE deliveries
               SET next_attempt_at = now() + make_interval(secs => ?), updated_at = now()
             WHERE id IN (
                   SELECT id FROM deliveries
                    WHERE status = 'PENDING' AND next_attempt_at <= now()
                    ORDER BY next_attempt_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED)
            RETURNING id
            """;

    private record Outcome(boolean success, boolean permanent, Integer statusCode, String error, long durationMs) {}

    private final JdbcTemplate jdbc;
    private final DeliveryRepository deliveries;
    private final DeliveryAttemptRepository attempts;
    private final EventRepository events;
    private final WebhookEndpointRepository endpoints;
    private final SecretCipher cipher;
    private final UrlGuard urlGuard;
    private final AppProperties.Delivery config;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final HttpClient http;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public DeliveryWorker(
            JdbcTemplate jdbc,
            DeliveryRepository deliveries,
            DeliveryAttemptRepository attempts,
            EventRepository events,
            WebhookEndpointRepository endpoints,
            SecretCipher cipher,
            UrlGuard urlGuard,
            AppProperties props,
            MeterRegistry metrics,
            Clock clock,
            PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.deliveries = deliveries;
        this.attempts = attempts;
        this.events = events;
        this.endpoints = endpoints;
        this.cipher = cipher;
        this.urlGuard = urlGuard;
        this.config = props.delivery();
        this.metrics = metrics;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER) // a 3xx must never bounce us to an internal address
                .connectTimeout(config.connectTimeout())
                .build();
    }

    @Scheduled(fixedDelayString = "${webhook.delivery.poll-interval-ms:1000}")
    public void poll() {
        if (!config.workerEnabled()) {
            return; // WORKER_ENABLED=false lets you run API-only instances
        }
        List<UUID> claimed;
        try {
            claimed = jdbc.query(
                    CLAIM_SQL,
                    (rs, rowNum) -> rs.getObject(1, UUID.class),
                    (double) config.lease().toSeconds(),
                    config.batchSize());
        } catch (RuntimeException e) {
            log.error("Could not claim deliveries", e);
            return;
        }
        if (claimed.isEmpty()) {
            return;
        }
        List<CompletableFuture<Void>> running = claimed.stream()
                .map(id -> CompletableFuture.runAsync(() -> processSafely(id), executor))
                .toList();
        CompletableFuture.allOf(running.toArray(new CompletableFuture<?>[0])).join();
    }

    private void processSafely(UUID deliveryId) {
        try {
            process(deliveryId);
        } catch (RuntimeException e) {
            // Leave the row alone: its lease expires and it is retried. Never log secrets or payloads.
            log.error("Unexpected error processing delivery {}", deliveryId, e);
        }
    }

    private void process(UUID deliveryId) {
        Delivery delivery = deliveries.findById(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() != DeliveryStatus.PENDING) {
            return;
        }
        Event event = events.findById(delivery.getEventId()).orElseThrow();
        WebhookEndpoint endpoint = endpoints.findById(delivery.getEndpointId()).orElseThrow();

        Outcome outcome = send(delivery, event, endpoint);
        recordOutcome(deliveryId, outcome);
    }

    private Outcome send(Delivery delivery, Event event, WebhookEndpoint endpoint) {
        long started = System.nanoTime();
        try {
            if (!endpoint.isActive()) {
                return new Outcome(false, true, null, "Endpoint is disabled", 0);
            }
            URI uri = urlGuard.validate(endpoint.getUrl()); // SSRF re-check at send time
            String secret = cipher.decrypt(endpoint.getSecretEncrypted());
            String body = "{\"id\":\"" + event.getId()
                    + "\",\"type\":\"" + event.getType()
                    + "\",\"createdAt\":\"" + event.getCreatedAt()
                    + "\",\"data\":" + event.getPayload() + "}";
            long timestamp = clock.instant().getEpochSecond();

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(config.requestTimeout())
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "WebhookPlatform/1.0")
                    .header("X-Webhook-Id", delivery.getId().toString())
                    .header("X-Webhook-Event-Id", event.getId().toString())
                    .header("X-Webhook-Timestamp", Long.toString(timestamp))
                    .header("X-Webhook-Signature", HmacSigner.sign(secret, timestamp, body))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            long ms = elapsedMs(started);
            if (code >= 200 && code < 300) {
                return new Outcome(true, false, code, null, ms);
            }
            return new Outcome(false, false, code, "HTTP " + code, ms);
        } catch (UnsafeUrlException e) {
            return new Outcome(false, false, null, "Blocked: " + e.getMessage(), elapsedMs(started));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(false, false, null, "Interrupted", elapsedMs(started));
        } catch (Exception e) {
            // Only the exception type is stored: messages can contain internal hostnames or addresses.
            return new Outcome(false, false, null, e.getClass().getSimpleName(), elapsedMs(started));
        }
    }

    private void recordOutcome(UUID deliveryId, Outcome outcome) {
        String metricOutcome = tx.execute(status -> {
            Delivery delivery = deliveries.findById(deliveryId).orElseThrow();
            Instant now = clock.instant();
            attempts.save(new DeliveryAttempt(deliveryId, now, outcome.statusCode(), outcome.error(), outcome.durationMs()));

            if (outcome.success()) {
                delivery.recordSuccess(now, outcome.statusCode());
                return "success";
            }
            int attemptsAfter = delivery.getAttempts() + 1;
            if (outcome.permanent() || attemptsAfter >= config.maxAttempts()) {
                delivery.recordDead(now, outcome.statusCode(), outcome.error());
                return "dead";
            }
            Duration wait = Backoff.delay(attemptsAfter, config.baseDelay(), config.maxDelay(), ThreadLocalRandom.current());
            delivery.recordRetry(now, outcome.statusCode(), outcome.error(), now.plus(wait));
            return "retry";
        });
        metrics.counter("webhook.deliveries", "outcome", metricOutcome).increment();
        metrics.timer("webhook.delivery.latency").record(Duration.ofMillis(outcome.durationMs()));
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
