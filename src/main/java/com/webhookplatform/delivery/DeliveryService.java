package com.webhookplatform.delivery;

import com.webhookplatform.common.ConflictException;
import com.webhookplatform.common.NotFoundException;
import com.webhookplatform.delivery.DeliveryDtos.AttemptResponse;
import com.webhookplatform.delivery.DeliveryDtos.DeliveryDetailResponse;
import com.webhookplatform.delivery.DeliveryDtos.DeliveryResponse;
import com.webhookplatform.event.EventRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read/replay side of deliveries. Everything is scoped to the authenticated tenant. */
@Service
public class DeliveryService {

    private final DeliveryRepository deliveries;
    private final DeliveryAttemptRepository attempts;
    private final EventRepository events;
    private final Clock clock;

    public DeliveryService(
            DeliveryRepository deliveries, DeliveryAttemptRepository attempts, EventRepository events, Clock clock) {
        this.deliveries = deliveries;
        this.attempts = attempts;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> listForEvent(UUID tenantId, UUID eventId) {
        if (!events.existsByIdAndTenantId(eventId, tenantId)) {
            throw new NotFoundException("Event not found");
        }
        return deliveries.findByEventIdAndTenantIdOrderByCreatedAtAsc(eventId, tenantId).stream()
                .map(DeliveryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeliveryDetailResponse get(UUID tenantId, UUID id) {
        Delivery delivery = find(tenantId, id);
        List<AttemptResponse> history = attempts.findByDeliveryIdOrderByAttemptedAtAsc(id).stream()
                .map(AttemptResponse::from)
                .toList();
        return new DeliveryDetailResponse(DeliveryResponse.from(delivery), history);
    }

    @Transactional
    public DeliveryResponse replay(UUID tenantId, UUID id) {
        Delivery delivery = find(tenantId, id);
        if (delivery.getStatus() != DeliveryStatus.DEAD) {
            throw new ConflictException("Only DEAD deliveries can be replayed");
        }
        delivery.requeue(clock.instant());
        return DeliveryResponse.from(delivery);
    }

    private Delivery find(UUID tenantId, UUID id) {
        return deliveries.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException("Delivery not found"));
    }
}
