package com.webhookplatform.endpoint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every lookup a controller can reach is keyed by tenantId as well as id. A request for another tenant's
 * row simply finds nothing and answers 404, which also avoids confirming that the id exists.
 */
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    Optional<WebhookEndpoint> findByIdAndTenantId(UUID id, UUID tenantId);

    List<WebhookEndpoint> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);

    List<WebhookEndpoint> findByTenantIdAndActiveTrue(UUID tenantId);

    long countByTenantId(UUID tenantId);
}
