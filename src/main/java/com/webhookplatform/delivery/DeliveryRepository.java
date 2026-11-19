package com.webhookplatform.delivery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    Optional<Delivery> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Delivery> findByEventIdAndTenantIdOrderByCreatedAtAsc(UUID eventId, UUID tenantId);

    long countByEventId(UUID eventId);
}
