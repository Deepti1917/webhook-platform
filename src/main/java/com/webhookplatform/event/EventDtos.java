package com.webhookplatform.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class EventDtos {

    private EventDtos() {}

    /** Event types are restricted to a safe alphabet, so they can be embedded in JSON and logs without escaping. */
    public record CreateEventRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$", message = "letters, digits, . _ - (max 100)")
                    String type,
            @NotNull JsonNode payload) {}

    public record EventResponse(UUID id, String type, Instant createdAt, int deliveries, boolean duplicate) {}
}
