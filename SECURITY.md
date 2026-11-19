# Security checklist and audit notes

This maps each item on the original security request to what the project does, **how it was checked**, and what is left
for you. Nothing can honestly be called "no mistakes" without running it, so the last column is explicit.

Legend: ✅ implemented and verified here · 🔶 implemented, but not executed in the build environment (verify with the
steps shown) · ➖ not applicable to this project · ⚠️ needs action from you

| # | Request | What was done | Status |
|---|---|---|---|
| 1 | **Hide API keys** | No secret exists in the source. All come from environment variables with no defaults (`application.yml`); `.env` is git-ignored; `.env.example` holds placeholders only; compose refuses to start if one is missing. Tenant API keys are stored as SHA-256 hashes and shown once. Webhook signing secrets are AES-256-GCM encrypted in the DB. | ✅ crypto + hashing tested · ✅ tree scanned for hardcoded secrets |
| 2 | **Check environment variables** | `AppProperties` is validated at startup (admin key ≥ 32 chars, encryption key must decode to exactly 32 bytes, CORS wildcards rejected). A bad value stops the app with a clear message. `scripts/generate-secrets.sh` produces valid values. | ✅ generator output validated · 🔶 startup validation (run the app with a short key to see it refuse) |
| 3 | **Protect admin routes** | `/admin/**` and non-health `/actuator/**` require `ROLE_ADMIN`, granted only for the `ADMIN_API_KEY` (compared as hashes in constant time). Tenant keys get 403 there; the admin key gets 403 on `/api/**`. Everything not listed is `denyAll`. | 🔶 `smoke-test.sh` checks each case |
| 4 | **Proper authentication** | Stateless `X-API-Key` authentication, 256-bit random keys, hashed at rest, rotation endpoints, deactivation, failed-attempt throttling (10/min/IP, keys are not even checked while throttled). No default Spring user or generated password is created. | ✅ primitives tested · 🔶 filter via smoke test |
| 5 | **Users only access their own data** | Tenant id is taken from the authenticated principal, never from the URL or body. Every repository method reachable from a controller filters by `tenantId`; foreign ids return 404 (no existence leak). | 🔶 smoke test checks cross-tenant read/modify/list. ⚠️ Add Spring integration tests before relying on this. |
| 6 | **Sanitize forms / input** | There are no HTML forms. All input is JSON validated with Bean Validation and **allowlist** patterns (tenant name, event type, idempotency key). Unknown/oversized/malformed bodies are rejected; request body capped at 1 MiB, event payload at 256 KB (also a DB `CHECK`). All SQL is parameterised (JPA / `JdbcTemplate` placeholders). | ✅ SQL constraints tested · 🔶 validation via smoke test |
| 7 | **Protect against XSS** | The API only returns JSON, never HTML. `Content-Security-Policy: default-src 'none'`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`. Error messages never echo input. ⚠️ If you add a web UI, escape everything you render, especially event payloads (they are arbitrary tenant-supplied JSON). | 🔶 headers via smoke test |
| 8 | **Rate limiting** | Three token-bucket limiters: per IP (300/min, before auth), per tenant (600/min, after auth), and failed logins per IP (10/min). Memory is bounded; the table fails closed. | ✅ limiter logic tested · ⚠️ per-instance only (see limits) |
| 9 | **Check CORS settings** | No origin is allowed unless listed in `CORS_ALLOWED_ORIGINS`; wildcards are rejected at startup; credentials are never allowed; only `/api/**` can be CORS-enabled, never `/admin/**`. | 🔶 smoke test checks an unlisted origin |
| 10 | **Security headers** | CSP, HSTS (1 year, includeSubDomains; sent only over HTTPS), X-Frame-Options, nosniff, Referrer-Policy, Permissions-Policy, COOP, `Cache-Control: no-store`. They are also present on 401/413/429 responses. | 🔶 verify: `curl -sI -H "X-API-Key: $KEY" localhost:8080/api/v1/endpoints` |
| 11 | **Turn off debug mode** | No devtools; no debug flag; `show-sql: false`; error details (`message`, `stacktrace`, `exception`, `binding-errors`) set to `never`; whitelabel error page off; only `health`, `info`, `metrics` exposed by Actuator, and only health is public; health details hidden. Unexpected errors return a generic 500 and are logged server-side only. | 🔶 smoke test checks that errors leak nothing and that `/actuator/env` and `/actuator/heapdump` are absent |
| 12 | **Update dependencies** | Spring Boot **4.0.9** (the 3.5 line reached end of open-source support in mid-2026). Dependabot is configured for Maven, Docker and GitHub Actions. ⚠️ Before deploying, bump to the newest 4.x patch (check start.spring.io) and run a vulnerability scan (`mvn dependency-check:check` needs an NVD API key; or enable GitHub Dependabot alerts). | ⚠️ not scanned here (the build environment could not reach Maven Central) |
| 13 | **Remove anything not needed** | Only the starters in use. No devtools, Swagger UI, H2 console, or static resources (`add-mappings: false`). Docker image contains only the jar. | ✅ |
| 14 | **Check for exposed files** | Static file serving disabled; `.dockerignore` excludes `.env`, `.git`, sources; compose publishes ports on `127.0.0.1` only; container runs as a non-root user with a read-only filesystem and all capabilities dropped. | ✅ configuration reviewed · 🔶 container hardening (run `docker compose up`) |
| 15 | **Secure my database** | Two roles: the owner (migrations only) and a runtime user limited to SELECT/INSERT/UPDATE/DELETE. Verified against PostgreSQL 16: the runtime user **cannot** DROP, ALTER, TRUNCATE, CREATE tables, create roles, or drop the database. CHECK and UNIQUE constraints enforce idempotency, valid statuses and payload size. Signing secrets are encrypted. The DB port is bound to localhost. | ✅ tested on a real database · ⚠️ production: use TLS to the DB (`sslmode=verify-full`), a managed DB, and a non-superuser owner |
| 16 | **Hash passwords properly** | ➖ There are no user passwords. Credentials are high-entropy API keys, correctly hashed with SHA-256 (see README for why bcrypt/argon2 would be the wrong tool here). If you later add human passwords, use Spring's `BCryptPasswordEncoder` or Argon2. | ✅ |
| 17 | **Check Git history for leaked secrets** | ⚠️ Your real repository is not in this workspace, so its history could not be scanned. `scripts/scan-secrets.sh` (uses gitleaks if installed) and a CI job with full-history gitleaks are included. **Run it on your repo before pushing.** If a real secret was ever committed, rotate it first; deleting the file later does not remove it from history. | ⚠️ action required |
| 18 | **Full security audit** | A design-level review plus the checks above. It is **not** a penetration test. Known residual risks are listed below. | see below |

## What was actually verified in the build environment

- **Run and passing (22 unit tests):** API-key generation/hashing, AES-GCM round-trip + tamper/wrong-key detection, HMAC signing
  (cross-checked byte-for-byte against OpenSSL and a Python implementation), SSRF guard (private/loopback/link-local/metadata/
  CGNAT/IPv6/mapped-IPv4/credentials/non-https), rate limiter (burst, refill, bounded table), backoff (bounds, cap, overflow).
- **Run on real PostgreSQL 16:** the init script (including a password containing a quote and a space), both migrations, the
  least-privilege restrictions, constraints, and the worker's claim query: **8 parallel workers over 1,000 rows claimed each row
  exactly once**, leases prevent re-claiming, expired leases are reclaimed, and `SKIP LOCKED` does not block on locked rows.
- **Independently reviewed:** a second reviewer, given only the code (not my conclusions), read the whole Spring layer for
  compile and startup problems. It found one real bug that would have crashed startup (`server.error.include-exception` takes a
  boolean, not `never`); that is fixed. Most of its "looks fine" calls were from memory of the Boot 4 APIs rather than from
  compiling, so they lower the risk but do not replace a real build.
- **Syntax-parsed only:** all 50 Java files. The Spring layer (controllers, filters, JPA mappings, Security config) was **not**
  compiled or started, because the workspace blocked Maven Central. Boot 4 changed starter names and several package
  locations; I used the documented ones, but treat the first `mvn verify` as the real compile check. If something fails,
  paste the first error; it will be a small fix.

## Residual risks and limits (honest list)

1. **DNS rebinding.** The SSRF check resolves the host, then the HTTP client resolves it again to connect. An attacker who
   controls DNS could answer differently the second time. Mitigations present: re-check immediately before each send, no
   redirects, 10 s timeout, discarded response bodies (no data comes back to the attacker). For a hard guarantee, run the
   workers in a network segment that cannot reach internal services.
2. **Rate limits are per instance.** Several instances multiply the effective limit; move to Redis-backed buckets when you scale out.
3. **Client IP trust.** `FORWARD_HEADERS_STRATEGY=native` must only be enabled behind a proxy that overwrites `X-Forwarded-For`.
   Otherwise clients can spoof their IP and dodge per-IP limits.
4. **TLS** is not terminated by the app. Put it behind a reverse proxy (Caddy/nginx/cloud load balancer) with HTTPS, and make
   sure the proxy does not log the `X-API-Key` header.
5. **No API-key expiry, no admin audit log, one static admin key.** Rotate the admin key by changing the environment variable
   and restarting; tenant keys via `rotate-key`.
6. **Encryption key loss = signing secrets unreadable.** Back up `SECRET_ENCRYPTION_KEY` in a secrets manager. There is no
   key-rotation tool for it yet.
7. **No Spring integration tests yet**, notably for tenant isolation and the idempotency race. These are the first tests to add.
8. **Retention:** events, deliveries and attempts are kept forever. Add a cleanup job before production volumes.

## Before you deploy: 10-minute checklist

- [ ] `mvn verify` passes; `./scripts/smoke-test.sh` shows 0 failures
- [ ] Bumped Spring Boot to the latest 4.x patch; no known CVEs reported by Dependabot
- [ ] `./scripts/scan-secrets.sh` is clean on your real repo (and any leaked secret has been rotated)
- [ ] `.env` is not tracked: `git ls-files | grep -E '^\.env$'` prints nothing
- [ ] `ALLOW_PRIVATE_WEBHOOK_TARGETS` is `false`
- [ ] HTTPS in front of the app; DB connection uses TLS; DB not reachable from the internet
- [ ] Encryption key and admin key stored in a secrets manager, backed up
