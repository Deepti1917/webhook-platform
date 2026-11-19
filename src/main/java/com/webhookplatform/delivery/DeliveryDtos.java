package com.webhookplatform.delivery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DeliveryDtos {

    private DeliveryDtos() {}

    public record DeliveryResponse(
            UUID id,
            UUID eventId,
            UUID endpointId,
            String status,
            int attempts,
            Integer lastStatusCode,
            String lastError,
            Instant nextAttemptAt,
            Instant deliveredAt,
            Instant createdAt) {
        static DeliveryResponse from(Delivery d) {
            return new DeliveryResponse(
                    d.getId(),
                    d.getEventId(),
                    d.getEndpointId(),
                    d.getStatus().name(),
                    d.getAttempts(),
                    d.getLastStatusCode(),
                    d.getLastError(),
                    d.getStatus() == DeliveryStatus.PENDING ? d.getNextAttemptAt() : null,
                    d.getDeliveredAt(),
                    d.getCreatedAt());
        }
    }

    public record AttemptResponse(Instant attemptedAt, Integer statusCode, String error, long durationMs) {
        static AttemptResponse from(DeliveryAttempt a) {
            return new AttemptResponse(a.getAttemptedAt(), a.getStatusCode(), a.getError(), a.getDurationMs());
        }
    }

    public record DeliveryDetailResponse(DeliveryResponse delivery, List<AttemptResponse> attemptHistory) {}
}
