-- Tenants authenticate with an API key. Only its SHA-256 hash is stored.
CREATE TABLE tenants (
    id              UUID         PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    api_key_hash    VARCHAR(64)  NOT NULL UNIQUE,
    api_key_prefix  VARCHAR(12)  NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- secret_encrypted holds the AES-256-GCM encrypted signing secret (never plaintext).
CREATE TABLE endpoints (
    id                UUID          PRIMARY KEY,
    tenant_id         UUID          NOT NULL REFERENCES tenants (id),
    url               VARCHAR(2048) NOT NULL,
    secret_encrypted  VARCHAR(512)  NOT NULL,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_endpoints_tenant ON endpoints (tenant_id);
