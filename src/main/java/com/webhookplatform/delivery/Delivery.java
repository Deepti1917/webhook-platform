package com.webhookplatform.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deliveries")
public class Delivery {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "endpoint_id", nullable = false, updatable = false)
    private UUID endpointId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "last_status_code")
    private Integer lastStatusCode;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Delivery() {}

    public Delivery(UUID eventId, UUID endpointId, UUID tenantId) {
        this.eventId = eventId;
        this.endpointId = endpointId;
        this.tenantId = tenantId;
    }

    public void recordSuccess(Instant now, int statusCode) {
        attempts++;
        status = DeliveryStatus.SUCCESS;
        lastStatusCode = statusCode;
        lastError = null;
        deliveredAt = now;
        updatedAt = now;
    }

    public void recordRetry(Instant now, Integer statusCode, String error, Instant nextAttempt) {
        attempts++;
        lastStatusCode = statusCode;
        lastError = truncate(error);
        nextAttemptAt = nextAttempt;
        updatedAt = now;
    }

    public void recordDead(Instant now, Integer statusCode, String error) {
        attempts++;
        status = DeliveryStatus.DEAD;
        lastStatusCode = statusCode;
        lastError = truncate(error);
        updatedAt = now;
    }

    /** Replay: put a dead delivery back in the queue with a fresh attempt budget. */
    public void requeue(Instant now) {
        status = DeliveryStatus.PENDING;
        attempts = 0;
        nextAttemptAt = now;
        lastError = null;
        updatedAt = now;
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_ERROR_LENGTH ? s : s.substring(0, MAX_ERROR_LENGTH);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Integer getLastStatusCode() {
        return lastStatusCode;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
