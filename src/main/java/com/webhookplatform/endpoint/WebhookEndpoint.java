package com.webhookplatform.endpoint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "endpoints")
public class WebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 2048)
    private String url;

    /** AES-256-GCM encrypted signing secret (see SecretCipher). The plaintext is never stored. */
    @Column(name = "secret_encrypted", nullable = false, length = 512)
    private String secretEncrypted;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected WebhookEndpoint() {}

    public WebhookEndpoint(UUID tenantId, String url, String secretEncrypted) {
        this.tenantId = tenantId;
        this.url = url;
        this.secretEncrypted = secretEncrypted;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getUrl() {
        return url;
    }

    public String getSecretEncrypted() {
        return secretEncrypted;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void replaceSecret(String secretEncrypted) {
        this.secretEncrypted = secretEncrypted;
    }
}
