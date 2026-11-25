#!/usr/bin/env bash
# End-to-end validation of pg-cache against the docker compose stack.
# Usage (from the repository root, with `docker compose up -d --build` running):
#   bash example-app/validate.sh
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
PASS=0
FAIL=0

ok()   { echo "  [PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "  [FAIL] $1"; FAIL=$((FAIL + 1)); }
check() { if [ "$2" = "$3" ]; then ok "$1 ($2)"; else fail "$1: expected '$3', got '$2'"; fi; }
psql_q() { docker compose exec -T postgres psql -U pgcache -d pgcache -tAc "$1" | tr -d '[:space:]'; }
json_field() { sed -nE "s/.*\"$1\":\"?([^\",}]*)\"?.*/\1/p"; }
status_of() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
wait_for_app() {
  for _ in $(seq 1 60); do
    curl -sf "$BASE_URL/actuator/health" >/dev/null && return 0
    sleep 1
  done
  return 1
}

echo "== 0. Application is up"
wait_for_app && ok "actuator health UP" || { fail "application did not start"; exit 1; }
curl -s -X DELETE "$BASE_URL/products/42" >/dev/null

echo "== 1. Cache-aside: first call misses, second call hits"
r1=$(curl -s "$BASE_URL/products/42")
r2=$(curl -s "$BASE_URL/products/42")
echo "     1st: $r1"
echo "     2nd: $r2"
check "1st call source" "$(echo "$r1" | json_field source)" "database"
check "2nd call source" "$(echo "$r2" | json_field source)" "cache"
t1=$(echo "$r1" | json_field elapsedMs); t2=$(echo "$r2" | json_field elapsedMs)
[ "$t1" -ge 2000 ] && [ "$t2" -lt 200 ] && ok "latency ${t1}ms -> ${t2}ms" || fail "latency ${t1}ms -> ${t2}ms"

echo "== 2. The entry really lives in PostgreSQL"
check "row for product:42 exists" "$(psql_q "SELECT count(*) FROM pg_cache WHERE key = 'product:42'")" "1"
check "value stored as JSONB" "$(psql_q "SELECT value->>'name' FROM pg_cache WHERE key = 'product:42'")" "Product42"
check "TTL = default-ttl (30s)" "$(psql_q "SELECT EXTRACT(EPOCH FROM expires_at - created_at)::int FROM pg_cache WHERE key = 'product:42'")" "30"
check "table is UNLOGGED" "$(psql_q "SELECT relpersistence FROM pg_class WHERE relname = 'pg_cache'")" "u"
check "indexes created" "$(psql_q "SELECT count(*) FROM pg_indexes WHERE tablename = 'pg_cache' AND indexname LIKE 'idx_pg_cache_%'")" "3"

echo "== 3. Explicit eviction"
check "DELETE /products/42" "$(status_of -X DELETE "$BASE_URL/products/42")" "204"
check "next call goes back to source" "$(curl -s "$BASE_URL/products/42" | json_field source)" "database"

echo "== 4. Set / get / overwrite / delete arbitrary JSON"
check "PUT user:1" "$(status_of -X PUT -H 'Content-Type: application/json' -d '{"name":"Ana","age":30}' "$BASE_URL/cache/user:1?ttl=never")" "204"
check "GET user:1 name" "$(curl -s "$BASE_URL/cache/user:1" | json_field name)" "Ana"
curl -s -X PUT -H 'Content-Type: application/json' -d '{"name":"Bia","age":31}' "$BASE_URL/cache/user:1?ttl=never" >/dev/null
check "overwrite user:1" "$(curl -s "$BASE_URL/cache/user:1" | json_field name)" "Bia"
check "never-expiring entry has NULL expires_at" "$(psql_q "SELECT expires_at IS NULL FROM pg_cache WHERE key = 'user:1'")" "t"
check "DELETE user:1" "$(status_of -X DELETE "$BASE_URL/cache/user:1")" "204"
check "GET after delete" "$(status_of "$BASE_URL/cache/user:1")" "404"
check "DELETE missing key" "$(status_of -X DELETE "$BASE_URL/cache/user:1")" "404"

echo "== 5. Expiration (TTL 2s) + manual cleanup"
curl -s -X PUT -H 'Content-Type: application/json' -d '"temporary"' "$BASE_URL/cache/temp:1?ttl=2s" >/dev/null
check "GET before expiry" "$(status_of "$BASE_URL/cache/temp:1")" "200"
sleep 3
check "GET after expiry (hidden)" "$(status_of "$BASE_URL/cache/temp:1")" "404"
check "expired row still in table" "$(psql_q "SELECT count(*) FROM pg_cache WHERE key = 'temp:1'")" "1"
echo "     stats: $(curl -s "$BASE_URL/cache/stats")"
removed=$(curl -s -X POST "$BASE_URL/cache/cleanup" | json_field removed)
[ "${removed:-0}" -ge 1 ] && ok "POST /cache/cleanup removed $removed" || fail "cleanup removed '${removed}'"
check "row gone after cleanup" "$(psql_q "SELECT count(*) FROM pg_cache WHERE key = 'temp:1'")" "0"

echo "== 6. Background cleanup (cleanup-interval 20s)"
curl -s -X PUT -H 'Content-Type: application/json' -d '"bg"' "$BASE_URL/cache/temp:2?ttl=1s" >/dev/null
echo "     waiting 25s for the scheduler..."
sleep 25
check "expired row removed by scheduler" "$(psql_q "SELECT count(*) FROM pg_cache WHERE key = 'temp:2'")" "0"

echo "== 7. Cache survives an application restart (shared state lives in PostgreSQL)"
curl -s -X PUT -H 'Content-Type: application/json' -d '{"v":"persisted"}' "$BASE_URL/cache/persist:1?ttl=10m" >/dev/null
docker compose restart app >/dev/null 2>&1
wait_for_app && ok "app restarted" || fail "app did not restart"
check "value read after restart" "$(curl -s "$BASE_URL/cache/persist:1" | json_field v)" "persisted"
curl -s -X DELETE "$BASE_URL/cache/persist:1" >/dev/null

echo
echo "Result: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
