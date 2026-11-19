CREATE TABLE events (
    id               UUID         PRIMARY KEY,
    tenant_id        UUID         NOT NULL REFERENCES tenants (id),
    type             VARCHAR(100) NOT NULL,
    payload          TEXT         NOT NULL CHECK (char_length(payload) <= 256000),
    request_hash     VARCHAR(64)  NOT NULL,
    idempotency_key  VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Idempotency: the same key from the same tenant can only ever create one event.
    CONSTRAINT uq_events_tenant_idempotency UNIQUE (tenant_id, idempotency_key)
);

-- The outbox: one row per (event, endpoint). The delivery worker polls this table.
CREATE TABLE deliveries (
    id                UUID         PRIMARY KEY,
    event_id          UUID         NOT NULL REFERENCES events (id),
    endpoint_id       UUID         NOT NULL REFERENCES endpoints (id),
    tenant_id         UUID         NOT NULL REFERENCES tenants (id),
    status            VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'DEAD')),
    attempts          INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_status_code  INTEGER,
    last_error        VARCHAR(500),
    delivered_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_deliveries_event_endpoint UNIQUE (event_id, endpoint_id)
);

-- Partial index: only rows the worker can pick up, so polling stays fast as history grows.
CREATE INDEX idx_deliveries_due ON deliveries (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_deliveries_tenant ON deliveries (tenant_id);

-- One row per HTTP attempt (audit trail for "why did this fail?").
CREATE TABLE delivery_attempts (
    id            BIGSERIAL    PRIMARY KEY,
    delivery_id   UUID         NOT NULL REFERENCES deliveries (id),
    attempted_at  TIMESTAMPTZ  NOT NULL,
    status_code   INTEGER,
    error         VARCHAR(500),
    duration_ms   BIGINT       NOT NULL
);

CREATE INDEX idx_attempts_delivery ON delivery_attempts (delivery_id);
