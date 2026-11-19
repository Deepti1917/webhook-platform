package com.webhookplatform.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;

/** API shapes. Entities are never returned directly, so internal columns (like the key hash) cannot leak. */
public final class TenantDtos {

    private TenantDtos() {}

    public record CreateTenantRequest(
            @NotBlank
                    @Pattern(regexp = "^[A-Za-z0-9 _.-]{1,100}$", message = "1-100 characters: letters, digits, space, . _ -")
                    String name) {}

    public record SetActiveRequest(@NotNull Boolean active) {}

    public record TenantResponse(UUID id, String name, boolean active, String apiKeyPrefix, Instant createdAt) {
        static TenantResponse from(Tenant t) {
            return new TenantResponse(t.getId(), t.getName(), t.isActive(), t.getApiKeyPrefix(), t.getCreatedAt());
        }
    }

    /** Returned exactly once, on create and on key rotation. */
    public record TenantCredentialsResponse(UUID id, String name, String apiKey, String note) {
        static TenantCredentialsResponse from(TenantService.Created created) {
            return new TenantCredentialsResponse(
                    created.tenant().getId(),
                    created.tenant().getName(),
                    created.apiKey(),
                    "Store this key now. It is not stored in recoverable form and cannot be shown again.");
        }
    }
}
