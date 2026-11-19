package com.webhookplatform.tenant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByApiKeyHashAndActiveTrue(String apiKeyHash);

    boolean existsByName(String name);
}
