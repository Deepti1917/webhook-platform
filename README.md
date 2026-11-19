# Webhook Platform

A multi-tenant service that delivers events to customers' HTTP endpoints **reliably and securely**: signed payloads,
retries with exponential backoff, a dead-letter state with replay, idempotent ingestion, and per-tenant isolation.
It is a small version of what Stripe, Shopify and GitHub run internally.

Java 21 · Spring Boot 4 · PostgreSQL · Spring Data JPA · Flyway · Spring Security · Docker

```
                 X-API-Key                       one DB transaction (transactional outbox)
 tenant ──────────────────────▶ POST /api/v1/events ─────────────────────────────────────┐
                                  Idempotency-Key                                         ▼
                                                                         events  +  deliveries (PENDING)
                                                                                           │  claimed with
 tenant's server  ◀── signed POST (HMAC, retries, backoff) ◀── DeliveryWorker ◀────────────┘  FOR UPDATE SKIP LOCKED

```

## Quick start (Docker on Windows PowerShell)

```
Copy-Item .env.example .env
# Generate secrets and paste the output over the CHANGE\_ME lines in .env:
\& "C:\\Program Files\\Git\\bin\\bash.exe" ./scripts/generate-secrets.sh
docker compose up -d --build         # app on http://localhost:8080 (this machine only)

```

First build downloads dependencies, so allow a few minutes. If the app fails to start, read the **first** error in the log:
a missing or malformed secret is reported by name (the app refuses to start rather than run with a weak default).

### Build \& startup fixes resolved

|Symptom|Fix|
|-|-|
|Corrupted local Maven cache artifact (`org.ow2.asm:asm:9.8`)|Cleared the corrupted cache folder and ran `mvn clean verify`|
|`Parameter 3 of method securityFilterChain in SecurityConfig required a single bean, but 2 were found` (`corsConfigurationSource` vs. `mvcHandlerMappingIntrospector`)|Added `@Qualifier("corsConfigurationSource")` to the `CorsConfigurationSource` parameter in `SecurityConfig.java`|
|`Binding to target ... failed` / missing secrets at startup|Populated `ADMIN\_API\_KEY` (≥ 32 chars) and `SECRET\_ENCRYPTION\_KEY` (32-byte Base64) in `.env`|

### Run it without Docker for the app (faster edit loop)

```
docker compose up -d postgres
Get-Content .env | ForEach-Object {
    if ($\_ -match '^(\[^#]\[^=]+)=(.\*)$') { \[Environment]::SetEnvironmentVariable($matches\[1], $matches\[2], 'Process') }
}
mvn spring-boot:run

```

### Verify everything works (verified)

```
# 1. Run unit tests (22/22 passing: BackoffTest, CryptoTest, RateLimiterTest, UrlGuardTest)
mvn verify

# 2. Load ADMIN\_API\_KEY and expose python3 for Git Bash on Windows, then run the 49-point smoke test
$env:ADMIN\_API\_KEY = (Get-Content .env | Where-Object { $\_ -match '^ADMIN\_API\_KEY=' }) -replace '^ADMIN\_API\_KEY=', ''
$shimDir = "$env:TEMP\\py3shim"
New-Item -ItemType Directory -Force -Path $shimDir | Out-Null
\[System.IO.File]::WriteAllText("$shimDir\\python3", "#!/bin/sh`nexec python `"`$@`"`n")
$env:PATH = "$shimDir;$env:PATH"
\& "C:\\Program Files\\Git\\bin\\bash.exe" ./scripts/smoke-test.sh

```

The smoke test verified **49/49 checks** (`49 passed, 0 failed, 0 skipped`) across availability and authentication,
tenants and roles, SSRF protection on webhook URLs, tenant isolation, idempotent event ingestion, input validation,
security headers and CORS, and failed-login throttling.

## Using the API (Windows PowerShell)

```
# 1. Admin creates a tenant. The API key is shown ONCE.
$tenant = curl.exe -s -H "X-API-Key: $env:ADMIN\_API\_KEY" -H "Content-Type: application/json" `
     -d '{\\"name\\":\\"my-store\\"}' http://localhost:8080/admin/tenants | ConvertFrom-Json
$env:TENANT\_KEY = $tenant.apiKey

# 2. Tenant registers an endpoint (https only). The signing secret is shown ONCE.
curl.exe -s -H "X-API-Key: $env:TENANT\_KEY" -H "Content-Type: application/json" `
     -d '{\\"url\\":\\"https://webhook.site/<your-endpoint-id>\\"}' http://localhost:8080/api/v1/endpoints

# 3. Tenant sends an event. Retrying with the same Idempotency-Key returns 200 with "duplicate": true.
$event = curl.exe -s -H "X-API-Key: $env:TENANT\_KEY" -H "Content-Type: application/json" `
     -H "Idempotency-Key: order-1001" `
     -d '{\\"type\\":\\"order.created\\",\\"payload\\":{\\"orderId\\":1001,\\"total\\":49.9}}' http://localhost:8080/api/v1/events | ConvertFrom-Json

# 4. Inspect delivery status (verified: status "SUCCESS", lastStatusCode 200 on attempt 1)
curl.exe -s -H "X-API-Key: $env:TENANT\_KEY" "http://localhost:8080/api/v1/events/$($event.id)/deliveries"

```

|Method \& path|Who|What|
|-|-|-|
|`POST /admin/tenants` · `GET /admin/tenants`|admin|create / list tenants|
|`POST /admin/tenants/{id}/rotate-key` · `PATCH /admin/tenants/{id}`|admin|rotate a tenant's key / deactivate|
|`POST /api/v1/endpoints` · `GET` · `GET /{id}` · `PATCH /{id}`|tenant|manage your webhook endpoints|
|`POST /api/v1/endpoints/{id}/rotate-secret`|tenant|new signing secret (shown once)|
|`POST /api/v1/events`|tenant|ingest an event (`202` new, `200` duplicate)|
|`GET /api/v1/events/{id}/deliveries`|tenant|delivery status per endpoint|
|`GET /api/v1/deliveries/{id}`|tenant|one delivery with every attempt|
|`POST /api/v1/deliveries/{id}/replay`|tenant|re-queue a `DEAD` delivery|
|`GET /actuator/health`|public|liveness/readiness|
|`GET /actuator/metrics/webhook.deliveries`|admin|success / retry / dead counters|

### What the receiver gets

`POST` with JSON `{"id","type","createdAt","data":{...}}` and headers `X-Webhook-Id`, `X-Webhook-Event-Id`,
`X-Webhook-Timestamp`, `X-Webhook-Signature: v1=<hex>`. Verify it like this (the receiver must do this, or anyone can forge events):

```
import hmac, hashlib, time
def verify(secret, timestamp, body: bytes, signature):
    if abs(time.time() - int(timestamp)) > 300:            # reject replays
        return False
    expected = "v1=" + hmac.new(secret.encode(), timestamp.encode() + b"." + body, hashlib.sha256).hexdigest()
    return hmac.compare\_digest(expected, signature)         # constant-time compare

```

## Configuration

Everything secret comes from environment variables (see `.env.example`). Nothing secret has a default.

|Variable|Purpose|
|-|-|
|`DB\_NAME`, `DB\_OWNER\_USER/PASSWORD`|schema owner, used only by Flyway migrations|
|`APP\_DB\_USER/PASSWORD`|runtime DB user, limited to SELECT/INSERT/UPDATE/DELETE|
|`ADMIN\_API\_KEY`|authenticates `/admin/\*\*` and metrics (≥ 32 chars)|
|`SECRET\_ENCRYPTION\_KEY`|base64 of 32 bytes; encrypts webhook signing secrets at rest. **Back it up.**|
|`CORS\_ALLOWED\_ORIGINS`|exact origins allowed to call `/api/\*\*` from a browser (empty = none)|
|`FORWARD\_HEADERS\_STRATEGY`|`native` only behind a reverse proxy you control|
|`WORKER\_ENABLED`|`false` to run API-only instances|
|`ALLOW\_PRIVATE\_WEBHOOK\_TARGETS`|**dev only**, never in production|

Retry/rate-limit tuning lives under `webhook.\*` in `application.yml`.

## Project layout (read in this order)

```
com.webhookplatform
├── tenant/      Tenant entity, repository, service, admin controller      ← start here: the basic layers
├── endpoint/    webhook endpoints (tenant-scoped, secrets encrypted)
├── event/       idempotent ingestion + transactional outbox write          ← the key design idea
├── delivery/    DeliveryWorker (claim → send → record), replay, history    ← the core
├── security/    api-key auth filter, rate limiters, SSRF guard, crypto     ← pure Java, unit-tested
├── config/      SecurityConfig (headers, CORS, rules), typed properties
└── common/      error handling (RFC 7807), exceptions, backoff

```

## Design decisions 

* **PostgreSQL as the queue (transactional outbox + `SKIP LOCKED`).** The event and its delivery rows commit atomically, so an
event is never accepted-but-lost. Workers claim rows with `FOR UPDATE SKIP LOCKED`, so several instances never take the same
row and never block each other. *Trade-off:* a broker (Kafka/RabbitMQ) scales further; Postgres is simpler and plenty for
thousands of deliveries per second per node.
* **At-least-once delivery + idempotency.** A receiver can see the same event twice (timeouts, crashes), so every delivery carries
a stable `X-Webhook-Event-Id`. Exactly-once over HTTP is not possible; at-least-once plus dedupe is the honest design.
* **Claim with a lease.** Claiming pushes `next\_attempt\_at` forward (60 s). If a worker dies mid-send, the row simply becomes
due again. The HTTP call happens *outside* any DB transaction so slow receivers never hold connections or locks.
* **Backoff with jitter** (`Backoff.java`): half to full of `10s · 2^n`, capped at 1 h, so retries from many failures don't
stampede a recovering receiver together.
* **API keys are hashed with SHA-256, not bcrypt.** They are 256 random bits, so they cannot be guessed; slow hashes protect
*human-chosen* passwords. Webhook signing secrets must be recoverable (to sign), so they are AES-256-GCM encrypted instead.
* **`ddl-auto: validate` + Flyway.** Versioned migrations own the schema; Hibernate only checks it matches. The runtime DB user
cannot alter tables at all.
* **Tenant id comes from the authenticated principal, never from the URL.** Every repository lookup is `findByIdAndTenantId`;
another tenant's id behaves exactly like a nonexistent one (404).

## Known limits

* Rate limits are per instance (in memory); behind several instances the effective limit multiplies.
* DNS-rebinding window between the SSRF check and the connection (see `UrlGuard` javadoc and `SECURITY.md`).
* TLS is terminated by a reverse proxy, not by the app. No built-in admin audit log or API-key expiry.
* No Spring integration tests are included yet; the 22 unit tests cover the security primitives and the 49-check smoke test
covers the HTTP layer.

See **SECURITY.md** for the full security checklist and what was verified.

