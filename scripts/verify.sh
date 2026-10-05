#!/usr/bin/env bash
# Local/dev verification: Docker, env, Redis, API, Admin Web, Supabase reachability.
# Read-only and non-destructive: never writes data, never resets volumes.
# Usage:
#   ./scripts/verify.sh
#   ./scripts/verify.sh --strict   # fail if services/api or apps/admin-web source is absent
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

STRICT=0
if [[ "${1:-}" == "--strict" ]]; then STRICT=1; fi

pass=0; fail=0; warn=0
ok()   { pass=$((pass+1)); echo "  [PASS] $1"; }
bad()  { fail=$((fail+1)); echo "  [FAIL] $1"; }
info() { echo "  [INFO] $1"; }
warn() { warn=$((warn+1)); echo "  [WARN] $1"; }

echo "== feazto verify =="
echo "Repo: $REPO_ROOT"
echo ""

echo "-- 1. Required files --"
for f in docker-compose.yml .env.example infra/docker/api.Dockerfile infra/docker/admin.Dockerfile infra/docker/admin.nginx.conf infra/redis/redis.conf infra/local/seed.sh infra/local/supabase-env.sh scripts/verify.sh scripts/gen-contracts.sh scripts/import-legacy.sh docs/operations/env.md docs/operations/runbooks.md docs/operations/backup.md README_NEW.md; do
  if [[ -f "$f" ]]; then ok "$f exists"; else bad "$f MISSING"; fi
done
echo ""

echo "-- 2. Canonical service paths (warn-only unless --strict) --"
for d in services/api supabase apps/admin-web; do
  if [[ -d "$d" ]]; then ok "$d/ exists";
  else
    if [[ "$STRICT" -eq 1 ]]; then bad "$d/ MISSING (strict)"; else warn "$d/ not yet created by backend/frontend tracks — compose build of that service will wait on it"; fi
  fi
done
echo ""

echo "-- 3. Legacy apps untouched (must exist, must NOT have been moved) --"
for d in customer_app vendor_app rider_app; do
  if [[ -d "$d" ]]; then ok "$d/ still in place (legacy preserved)"; else bad "$d/ MISSING — legacy app dir must not be deleted/moved"; fi
done
echo ""

echo "-- 4. Env template sanity (no secrets committed) --"
if grep -Eq '^[A-Z_]+=.+password|^[A-Z_]+=sk-live|^[A-Z_]+=eyJ[A-Za-z0-9_-]{20,}' .env.example 2>/dev/null; then
  bad ".env.example appears to contain a real secret value"
else
  ok ".env.example has no obvious real secret values"
fi
if [[ -f ".env" ]]; then warn ".env exists locally (expected for dev) — confirm it is git-ignored and never committed";
else info ".env not present (copy from .env.example for local dev)"; fi
echo ""

echo "-- 5. Docker availability --"
if command -v docker >/dev/null 2>&1; then
  ok "docker CLI present ($(docker --version 2>/dev/null | head -n1))"
  if docker compose version >/dev/null 2>&1; then ok "docker compose v2 present ($(docker compose version --short 2>/dev/null))";
  else warn "docker compose v2 not available — install Docker Desktop / compose plugin"; fi
  if docker info >/dev/null 2>&1; then ok "docker daemon reachable";
  else warn "docker daemon not reachable — start Docker Desktop / dockerd (infra checks below will be skipped)"; fi
else
  warn "docker not installed — skipping container health checks (install Docker Desktop to run redis/api/admin-web)"
fi
echo ""

echo "-- 6. Compose config + running services (best-effort) --"
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  if docker compose config --quiet 2>/dev/null; then ok "docker compose config validates";
  else bad "docker compose config FAILED — run 'docker compose config' for details"; fi
  for svc in redis api admin-web; do
    cid="$(docker compose ps -q "$svc" 2>/dev/null || true)"
    if [[ -n "$cid" ]]; then
      state="$(docker inspect -f '{{.State.Health.Status}} {{.State.Status}}' "$cid" 2>/dev/null || docker inspect -f '{{.State.Status}}' "$cid" 2>/dev/null || echo unknown)"
      info "$svc container $cid -> $state"
      if [[ "$state" == *healthy* ]] || [[ "$state" == *running* ]]; then ok "$svc running ($state)"; else warn "$svc present but state=$state"; fi
    else
      info "$svc not running (start with: docker compose up $svc / docker compose up --build)"
    fi
  done
  echo ""
  echo "-- 7. Endpoint probes (best-effort, local defaults) --"
  if curl -fsS --max-time 3 http://localhost:6379 >/dev/null 2>&1; then info "redis port probe unexpected-HTTP (fine)"; else info "redis speaks RESP not HTTP (expected) — use: docker exec feazto-redis redis-cli ping"; fi
  if docker exec feazto-redis redis-cli ping 2>/dev/null | grep -qi pong; then ok "redis PING -> PONG";
  else info "redis PING not available (container not running?) — start with: docker compose up redis"; fi
  if curl -fsS --max-time 5 http://localhost:8080/actuator/health >/dev/null 2>&1 || curl -fsS --max-time 5 http://localhost:8080/api/v1/health >/dev/null 2>&1; then ok "api health endpoint reachable";
  else info "api health not reachable (container not running or backend track not yet implemented?)"; fi
  if curl -fsS --max-time 5 http://localhost:5173/ >/dev/null 2>&1; then ok "admin-web reachable on :5173";
  else info "admin-web not reachable on :5173 (container not running or frontend track not yet implemented?)"; fi
else
  info "Docker daemon unavailable — config/container/endpoint checks skipped."
fi
echo ""

echo "-- 8. Shell syntax --"
syntax_ok=1
for sh in infra/local/seed.sh infra/local/supabase-env.sh scripts/verify.sh scripts/gen-contracts.sh scripts/import-legacy.sh; do
  if bash -n "$sh" 2>/dev/null; then ok "bash -n $sh"; else bad "syntax error in $sh"; syntax_ok=0; fi
done
echo ""

echo "== result: $pass passed, $warn warnings, $fail failed =="
if [[ "$fail" -gt 0 ]]; then exit 1; fi
exit 0
