# Runbooks (local / dev operations)

## 1. Start / stop

```bash
# Redis only (default backend dev: API via Maven on host, admin via Vite)
docker compose up redis

# Redis + containerised API
docker compose up redis api

# Full stack (redis + api + admin-web)
docker compose up --build

# Detached + follow logs
docker compose up -d && docker compose logs -f

# Stop (keeps volumes) / full reset of containers only
docker compose stop
docker compose down          # containers + network, keeps redis-data volume
```

> `docker compose down -v` **deletes** the `redis-data` volume. Redis holds only
> reconstructable support state (see `backup.md`), but prefer `down` without `-v`.

## 2. Service URLs (local defaults)

| Service | URL | Notes |
|---|---|---|
| API health | `http://localhost:8080/actuator/health` (fallback `/api/v1/health`) | `SERVER_PORT` in `.env` |
| API base | `http://localhost:8080/api/v1` | `API_BASE_URL` / `VITE_API_BASE_URL` |
| Admin Web | `http://localhost:5173/` (`/healthz` probe) | `ADMIN_WEB_PORT` in `.env` |
| Redis | `localhost:6379` / `redis://redis:6379` in-compose | `REDIS_PORT` / `REDIS_URL` |

## 3. Logs & health

```bash
docker compose ps
docker compose logs -f redis
docker compose logs -f api
docker compose logs -f admin-web
docker exec feazto-redis redis-cli ping            # expect PONG
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:5173/healthz
./scripts/verify.sh
```

## 4. Supabase env & seed

```bash
cp .env.example .env            # once; fill in real values (never commit .env)
source ./infra/local/supabase-env.sh   # validates SUPABASE_* + DATABASE_URL
./infra/local/seed.sh --check    # plan only
./infra/local/seed.sh            # apply supabase/seed/*.sql (idempotent)
```

Seeds must be idempotent (`INSERT ... ON CONFLICT DO NOTHING`). This script
never wipes data; there is intentionally no `--reset` flag.

## 5. Contracts (shared API types)

```bash
./scripts/gen-contracts.sh --check   # plan only
./scripts/gen-contracts.sh            # OpenAPI -> packages/api-contracts/src/generated.ts
```

Run after any backend API change; admin-web and mobile apps consume the
generated types instead of hand-duplicated shapes.

## 6. Legacy import (copy-only)

```bash
./scripts/import-legacy.sh --check   # dry run
./scripts/import-legacy.sh           # customer_app->apps/customer-mobile etc. (merge, source untouched)
```

Uses `cp`/`rsync` merge semantics. Never `mv`/`rm` the legacy dirs — they stay
as the reference until the migration is formally retired.

## 7. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `port is already allocated` on 6379/8080/5173 | Another service using the port | `lsof -i :6379` / change `REDIS_PORT` / `SERVER_PORT` / `ADMIN_WEB_PORT` in `.env` |
| `docker daemon not reachable` | Docker Desktop not running | Start Docker Desktop / `dockerd`; re-run `./scripts/verify.sh` |
| `api` unhealthy, boot loop | Missing `DATABASE_URL` / Supabase keys, or `services/api` not yet implemented | `source infra/local/supabase-env.sh`; `docker compose logs api` |
| `admin-web` 404 on refresh | Missing SPA fallback | `infra/docker/admin.nginx.conf` handles it; rebuild: `docker compose up --build admin-web` |
| `redis` `MISCONF` / appendonly errors | Corrupt `/data` after unclean stop | `docker compose stop redis && docker compose up redis`; last resort `down -v` (cache only, reconstructable) |
| `psql: could not connect` in seed | Wrong `DATABASE_URL` / IP allow-list | Check Supabase project status, password, and DB network settings |
| Real secret suspected in git | Accidental `git add .env` | Rotate the leaked key immediately, purge history, re-verify with `git status` |

## 8. Secret leak / rotation (summary)

1. Revoke/rotate the key at the provider (Supabase / payment dashboard).
2. Update hosting/CI env (never commit the new value).
3. Restart API: `docker compose up -d --force-recreate api`.
4. Verify: `./scripts/verify.sh` + one payment-webhook + one OTP smoke test.
5. Full backup/rotation detail: `backup.md`.
