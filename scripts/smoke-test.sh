#!/usr/bin/env bash
# End-to-end security and behaviour check against a RUNNING instance.
#
#   export ADMIN_API_KEY=<the value from your .env>
#   ./scripts/smoke-test.sh [base-url]        # default http://localhost:8080
#
# Run it against a fresh instance with default settings (ALLOW_PRIVATE_WEBHOOK_TARGETS=false).
# The last section deliberately triggers the failed-login throttle, so your IP is locked out for about a
# minute afterwards.
set -uo pipefail

BASE="${1:-http://localhost:8080}"
: "${ADMIN_API_KEY:?export ADMIN_API_KEY first (same value as in .env)}"
command -v python3 >/dev/null || { echo "python3 is needed to read JSON fields" >&2; exit 2; }

pass=0; fail=0; skip=0
check() { # name expected actual
  if [ "$2" = "$3" ]; then echo "PASS  $1"; pass=$((pass + 1)); else echo "FAIL  $1  (expected $2, got $3)"; fail=$((fail + 1)); fi
}
code()  { curl -s -o /dev/null -w '%{http_code}' "$@"; }
field() { python3 -c 'import sys,json; print(json.load(sys.stdin).get(sys.argv[1],""))' "$1"; }
SUFFIX=$(date +%s)$RANDOM

echo "== availability and authentication =="
check "health is public"                       200 "$(code "$BASE/actuator/health")"
check "no key on /api -> 401"                  401 "$(code "$BASE/api/v1/endpoints")"
check "no key on /admin -> 401"                401 "$(code "$BASE/admin/tenants")"
check "wrong key -> 401"                       401 "$(code -H 'X-API-Key: not-a-real-key-not-a-real-key' "$BASE/api/v1/endpoints")"
check "metrics need admin (no key) -> 401"     401 "$(code "$BASE/actuator/metrics")"
check "env endpoint not exposed"               404 "$(code -H "X-API-Key: $ADMIN_API_KEY" "$BASE/actuator/env")"
check "heapdump not exposed"                   404 "$(code -H "X-API-Key: $ADMIN_API_KEY" "$BASE/actuator/heapdump")"
check "admin key works on /admin"              200 "$(code -H "X-API-Key: $ADMIN_API_KEY" "$BASE/admin/tenants")"

echo "== tenants and roles =="
A=$(curl -s -H "X-API-Key: $ADMIN_API_KEY" -H 'Content-Type: application/json' -d "{\"name\":\"smoke-a-$SUFFIX\"}" "$BASE/admin/tenants")
B=$(curl -s -H "X-API-Key: $ADMIN_API_KEY" -H 'Content-Type: application/json' -d "{\"name\":\"smoke-b-$SUFFIX\"}" "$BASE/admin/tenants")
KEY_A=$(echo "$A" | field apiKey); KEY_B=$(echo "$B" | field apiKey)
[ -n "$KEY_A" ] && [ -n "$KEY_B" ] && echo "PASS  created two tenants" && pass=$((pass + 1)) || { echo "FAIL  could not create tenants: $A $B"; exit 1; }
check "tenant key cannot use /admin -> 403"    403 "$(code -H "X-API-Key: $KEY_A" "$BASE/admin/tenants")"
check "admin key cannot use /api -> 403"       403 "$(code -H "X-API-Key: $ADMIN_API_KEY" "$BASE/api/v1/endpoints")"
check "bad tenant name rejected -> 400"        400 "$(code -H "X-API-Key: $ADMIN_API_KEY" -H 'Content-Type: application/json' -d '{"name":"<script>alert(1)</script>"}' "$BASE/admin/tenants")"
check "duplicate tenant name -> 409"           409 "$(code -H "X-API-Key: $ADMIN_API_KEY" -H 'Content-Type: application/json' -d "{\"name\":\"smoke-a-$SUFFIX\"}" "$BASE/admin/tenants")"

echo "== SSRF protection on webhook URLs =="
post_ep() { curl -s -o /dev/null -w '%{http_code}' -H "X-API-Key: $KEY_A" -H 'Content-Type: application/json' -d "{\"url\":\"$1\"}" "$BASE/api/v1/endpoints"; }
check "http:// rejected"                       400 "$(post_ep 'http://example.com/hook')"
check "https://localhost rejected"             400 "$(post_ep 'https://localhost/hook')"
check "https://127.0.0.1 rejected"             400 "$(post_ep 'https://127.0.0.1/hook')"
check "cloud metadata IP rejected"             400 "$(post_ep 'https://169.254.169.254/latest/meta-data')"
check "private 10.x rejected"                  400 "$(post_ep 'https://10.0.0.5/hook')"
check "IPv6 loopback rejected"                 400 "$(post_ep 'https://[::1]/hook')"
check "credentials in URL rejected"            400 "$(post_ep 'https://user:pass@example.com/hook')"

echo "== tenant isolation =="
EP=$(curl -s -H "X-API-Key: $KEY_A" -H 'Content-Type: application/json' -d '{"url":"https://example.com/webhook"}' "$BASE/api/v1/endpoints")
EP_ID=$(echo "$EP" | field id)
if [ -n "$EP_ID" ]; then
  echo "PASS  tenant A created a public https endpoint (secret returned once)"; pass=$((pass + 1))
  check "secret is returned on create"         true "$([ -n "$(echo "$EP" | field signingSecret)" ] && echo true || echo false)"
  check "tenant B cannot read A's endpoint -> 404" 404 "$(code -H "X-API-Key: $KEY_B" "$BASE/api/v1/endpoints/$EP_ID")"
  check "tenant B cannot modify A's endpoint -> 404" 404 "$(code -X PATCH -H "X-API-Key: $KEY_B" -H 'Content-Type: application/json' -d '{"active":false}' "$BASE/api/v1/endpoints/$EP_ID")"
  check "tenant B's list does not contain A's endpoint" false "$(curl -s -H "X-API-Key: $KEY_B" "$BASE/api/v1/endpoints" | grep -q "$EP_ID" && echo true || echo false)"
  check "later GET does not leak the secret"   "" "$(curl -s -H "X-API-Key: $KEY_A" "$BASE/api/v1/endpoints/$EP_ID" | field signingSecret)"
else
  echo "SKIP  example.com not resolvable from here (offline?); isolation checks on endpoints skipped"; skip=$((skip + 1))
fi

echo "== idempotent event ingestion =="
IDEM="smoke-$SUFFIX"
ev() { curl -s -w '\n%{http_code}' -H "X-API-Key: $KEY_A" -H 'Content-Type: application/json' -H "Idempotency-Key: $IDEM" -d "$1" "$BASE/api/v1/events"; }
R1=$(ev '{"type":"order.created","payload":{"id":1}}'); R2=$(ev '{"type":"order.created","payload":{"id":1}}')
check "first request accepted -> 202"          202 "$(echo "$R1" | tail -1)"
check "same key + same body -> 200 (duplicate)" 200 "$(echo "$R2" | tail -1)"
check "same event id returned both times"      "$(echo "$R1" | head -1 | field id)" "$(echo "$R2" | head -1 | field id)"
check "same key + different body -> 409"       409 "$(ev '{"type":"order.created","payload":{"id":2}}' | tail -1)"
check "missing Idempotency-Key -> 400"         400 "$(code -H "X-API-Key: $KEY_A" -H 'Content-Type: application/json' -d '{"type":"a","payload":{}}' "$BASE/api/v1/events")"
EV_ID=$(echo "$R1" | head -1 | field id)
check "tenant B cannot list A's event deliveries -> 404" 404 "$(code -H "X-API-Key: $KEY_B" "$BASE/api/v1/events/$EV_ID/deliveries")"

echo "== input validation =="
check "bad event type -> 400"                  400 "$(code -H "X-API-Key: $KEY_A" -H 'Content-Type: application/json' -H 'Idempotency-Key: v1' -d '{"type":"a b<c>","payload":{}}' "$BASE/api/v1/events")"
check "non-object payload -> 400"              400 "$(code -H "X-API-Key: $KEY_A" -H 'Content-Type: application/json' -H 'Idempotency-Key: v2' -d '{"type":"a.b","payload":"text"}' "$BASE/api/v1/events")"
check "malformed JSON -> 400"                  400 "$(code -H "X-API-Key: $KEY_A" -H 'Content-Type: application/json' -H 'Idempotency-Key: v3' -d '{not json' "$BASE/api/v1/events")"
check "invalid UUID in path -> 400"            400 "$(code -H "X-API-Key: $KEY_A" "$BASE/api/v1/endpoints/not-a-uuid")"
check "unknown UUID -> 404"                    404 "$(code -H "X-API-Key: $KEY_A" "$BASE/api/v1/endpoints/00000000-0000-0000-0000-000000000000")"
head -c 2000000 /dev/zero | tr '\0' 'a' > /tmp/smoke-big.$$ 2>/dev/null
check "2 MB body -> 413"                       413 "$(code -H "X-API-Key: $KEY_A" -H 'Content-Type: application/json' -H 'Idempotency-Key: big' --data-binary @/tmp/smoke-big.$$ "$BASE/api/v1/events")"
rm -f /tmp/smoke-big.$$
BODY=$(curl -s -H "X-API-Key: $KEY_A" -H 'Content-Type: application/json' -H 'Idempotency-Key: v4' -d '{not json' "$BASE/api/v1/events")
check "error body leaks no internals"          false "$(echo "$BODY" | grep -qiE 'exception|stacktrace|at com\.|org\.springframework|jackson' && echo true || echo false)"

echo "== security headers and CORS =="
H=$(curl -s -D - -o /dev/null -H "X-API-Key: $KEY_A" "$BASE/api/v1/endpoints" | tr -d '\r' | tr 'A-Z' 'a-z')
has() { echo "$H" | grep -q "^$1" && echo true || echo false; }
check "X-Content-Type-Options: nosniff"        true "$(has 'x-content-type-options: nosniff')"
check "X-Frame-Options: DENY"                  true "$(has 'x-frame-options: deny')"
check "Content-Security-Policy present"        true "$(has 'content-security-policy:')"
check "Referrer-Policy present"                true "$(has 'referrer-policy:')"
check "Permissions-Policy present"             true "$(has 'permissions-policy:')"
check "Cache-Control no-store"                 true "$(echo "$H" | grep -q '^cache-control:.*no-store' && echo true || echo false)"
check "no X-Powered-By header"                 false "$(has 'x-powered-by:')"
check "no Server header leaking versions"      false "$(echo "$H" | grep -qE '^server:.*(tomcat|coyote|[0-9])' && echo true || echo false)"
CORS=$(curl -s -D - -o /dev/null -X OPTIONS -H 'Origin: https://evil.example' -H 'Access-Control-Request-Method: GET' "$BASE/api/v1/endpoints" | tr -d '\r' | tr 'A-Z' 'a-z')
check "CORS: unlisted origin gets no allow header" false "$(echo "$CORS" | grep -q '^access-control-allow-origin:' && echo true || echo false)"

echo "== failed-login throttle (locks this IP for ~1 minute) =="
GOT429=false
for i in $(seq 1 25); do
  [ "$(code -H 'X-API-Key: aaaaaaaaaaaaaaaaaaaaaaaaaaaa' "$BASE/api/v1/endpoints")" = 429 ] && GOT429=true && break
done
check "repeated bad keys eventually -> 429"    true "$GOT429"

echo
echo "$pass passed, $fail failed, $skip skipped"
[ "$fail" -eq 0 ]
