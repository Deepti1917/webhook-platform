package com.webhookplatform.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One row per HTTP attempt: the audit trail behind "why did this delivery fail?". */
@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(length = 500)
    private String error;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    protected DeliveryAttempt() {}

    public DeliveryAttempt(UUID deliveryId, Instant attemptedAt, Integer statusCode, String error, long durationMs) {
        this.deliveryId = deliveryId;
        this.attemptedAt = attemptedAt;
        this.statusCode = statusCode;
        this.error = error == null ? null : (error.length() <= 500 ? error : error.substring(0, 500));
        this.durationMs = durationMs;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getError() {
        return error;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
