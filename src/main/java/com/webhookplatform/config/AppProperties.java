package com.webhookplatform.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** All configuration is validated at startup, so a bad or missing value stops the app immediately. */
@ConfigurationProperties(prefix = "webhook")
@Validated
public record AppProperties(
        @NotBlank @Size(min = 32, max = 256) String adminApiKey,
        @NotBlank String encryptionKey,
        @DefaultValue @Valid Cors cors,
        @DefaultValue @Valid Ssrf ssrf,
        @DefaultValue @Valid RateLimit rateLimit,
        @DefaultValue @Valid Delivery delivery) {

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }

    public record Ssrf(@DefaultValue("false") boolean allowPrivateNetworks) {}

    public record RateLimit(
            @DefaultValue("300") @Min(1) int ipPerMinute,
            @DefaultValue("600") @Min(1) int tenantPerMinute,
            @DefaultValue("10") @Min(1) int authFailuresPerMinute) {}

    public record Delivery(
            @DefaultValue("true") boolean workerEnabled,
            @DefaultValue("50") @Min(1) @Max(500) int batchSize,
            @DefaultValue("1000") @Min(100) long pollIntervalMs,
            @DefaultValue("6") @Min(1) @Max(20) int maxAttempts,
            @DefaultValue("10s") Duration baseDelay,
            @DefaultValue("1h") Duration maxDelay,
            @DefaultValue("5s") Duration connectTimeout,
            @DefaultValue("10s") Duration requestTimeout,
            @DefaultValue("60s") Duration lease) {}
}
