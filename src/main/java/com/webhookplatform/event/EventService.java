package com.webhookplatform.event;

import com.webhookplatform.common.BadRequestException;
import com.webhookplatform.common.ConflictException;
import com.webhookplatform.delivery.Delivery;
import com.webhookplatform.delivery.DeliveryRepository;
import com.webhookplatform.endpoint.WebhookEndpoint;
import com.webhookplatform.endpoint.WebhookEndpointRepository;
import com.webhookplatform.security.ApiKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class EventService {

    static final int MAX_PAYLOAD_CHARS = 256_000;
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{1,100}$");
    private static final Pattern EVENT_TYPE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$");

    public record IngestResult(Event event, int deliveries, boolean duplicate) {}

    private final EventRepository events;
    private final WebhookEndpointRepository endpoints;
    private final DeliveryRepository deliveries;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public EventService(
            EventRepository events,
            WebhookEndpointRepository endpoints,
            DeliveryRepository deliveries,
            ObjectMapper mapper,
            PlatformTransactionManager txManager) {
        this.events = events;
        this.endpoints = endpoints;
        this.deliveries = deliveries;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Idempotent ingestion + transactional outbox.
     *
     * The event row and one PENDING delivery row per active endpoint are written in ONE transaction, so
     * an event can never exist without its deliveries (or the reverse). The worker later picks the
     * deliveries up. Retrying with the same Idempotency-Key returns the original event instead of
     * creating a second one; reusing the key with a different body is rejected.
     */
    public IngestResult ingest(UUID tenantId, String idempotencyKey, String type, JsonNode payload) {
        // Defense in depth: the controller validates these too, but the service never trusts its caller.
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new BadRequestException("Idempotency-Key must be 1-100 characters: letters, digits, _ -");
        }
        if (type == null || !EVENT_TYPE.matcher(type).matches()) {
            throw new BadRequestException("type must be letters, digits, . _ - (max 100)");
        }
        if (payload == null || !payload.isObject()) {
            throw new BadRequestException("payload must be a JSON object");
        }
        String body = mapper.writeValueAsString(payload);
        if (body.length() > MAX_PAYLOAD_CHARS) {
            throw new BadRequestException("payload is too large (max " + MAX_PAYLOAD_CHARS + " characters)");
        }
        String requestHash = ApiKeys.sha256Hex(type + "\n" + body);

        Optional<Event> existing = events.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            return duplicate(existing.get(), requestHash);
        }

        try {
            return tx.execute(status -> {
                Event event = events.saveAndFlush(new Event(tenantId, type, body, requestHash, idempotencyKey));
                List<WebhookEndpoint> targets = endpoints.findByTenantIdAndActiveTrue(tenantId);
                for (WebhookEndpoint endpoint : targets) {
                    deliveries.save(new Delivery(event.getId(), endpoint.getId(), tenantId));
                }
                return new IngestResult(event, targets.size(), false);
            });
        } catch (DataIntegrityViolationException e) {
            // Two concurrent requests with the same key: the loser lands here. Return the winner's event.
            Event winner = events.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey).orElseThrow(() -> e);
            return duplicate(winner, requestHash);
        }
    }

    private IngestResult duplicate(Event existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ConflictException("This Idempotency-Key was already used with a different request");
        }
        return new IngestResult(existing, (int) deliveries.countByEventId(existing.getId()), true);
    }
}
