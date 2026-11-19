package com.webhookplatform.endpoint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class EndpointDtos {

    private EndpointDtos() {}

    public record CreateEndpointRequest(@NotBlank @Size(max = 2048) String url) {}

    public record SetActiveRequest(@NotNull Boolean active) {}

    public record EndpointResponse(UUID id, String url, boolean active, Instant createdAt) {
        static EndpointResponse from(WebhookEndpoint e) {
            return new EndpointResponse(e.getId(), e.getUrl(), e.isActive(), e.getCreatedAt());
        }
    }

    /** Returned exactly once, on create and on secret rotation. The secret is what receivers verify signatures with. */
    public record EndpointSecretResponse(UUID id, String url, boolean active, Instant createdAt, String signingSecret) {
        static EndpointSecretResponse from(EndpointService.WithSecret created) {
            WebhookEndpoint e = created.endpoint();
            return new EndpointSecretResponse(e.getId(), e.getUrl(), e.isActive(), e.getCreatedAt(), created.secret());
        }
    }
}
