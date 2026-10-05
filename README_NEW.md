# Feazto — Local / Dev Setup (NEW platform layout)

> This file describes the **new modular-monolith platform** (Spring Boot API +
> Supabase + Redis + Admin Web + three mobile clients). It does NOT replace any
> `README` inside `customer_app/`, `vendor_app/`, or `rider_app/` — those legacy
> apps stay untouched until formally migrated (see `scripts/import-legacy.sh`).
>
> Canonical layout (MASTER_PROMPT_ENHANCED.md §37):
> `services/api` · `apps/admin-web` (+ later `apps/customer-mobile` etc.) ·
> `supabase/` · `packages/api-contracts` · `infra/` · `scripts/` · `docs/`

## 1. Prerequisites

- Docker Desktop (Compose v2) — for `redis` / containerised `api` / `admin-web`
- Java 21 + Maven wrapper (in `services/api`, backend track) — for host API runs
- Node.js 20+ — for `apps/admin-web` and `scripts/gen-contracts.sh`
- `psql` — for `infra/local/seed.sh`
- A Supabase project (dev) — URL + anon/service-role keys + `DATABASE_URL`
- Ports free: `6379` (Redis) · `8080` (API) · `5173` (Admin)

Verify everything:

```bash
./scripts/verify.sh
```

## 2. Environment

```bash
cp .env.example .env
# fill in SUPABASE_URL / ANON / SERVICE_ROLE / DATABASE_URL / PAYMENT_* / OTP_SECRET
source ./infra/local/supabase-env.sh   # validates required vars (no secrets printed)
```

Rules: server secrets (`SERVICE_ROLE_KEY`, `PAYMENT_*`, `OTP_SECRET`,
`DATABASE_URL`) stay server-side; `VITE_*` / `EXPO_PUBLIC_*` are public by
construction and must never hold secrets. Full catalogue:
`docs/operations/env.md`. Never commit `.env`.

## 3. Start order

### 3a. Redis (always first)

```bash
docker compose up redis
docker exec feazto-redis redis-cli ping   # expect PONG
```

`infra/redis/redis.conf` caps local memory (256 MB, `allkeys-lru`) with AOF+RDB.
Redis is cache/locks/idempotency only — Postgres is the system of record.

### 3b. Backend API (Spring Boot, authoritative business layer)

Host run (default for backend dev):

```bash
cd services/api
./mvnw spring-boot:run
curl -fsS http://localhost:8080/actuator/health || curl -fsS http://localhost:8080/api/v1/health
```

Containerised run (parity / CI smoke):

```bash
docker compose up redis api
```

The API owns order/payment/subscription/rider-assignment state, enforces RBAC +
ownership + idempotency, and verifies payment webhooks server-side.

### 3c. Legacy mobile apps (customer / vendor / rider — still the reference UX)

Run **from their current folders** (do not move them by hand):

```bash
# customer
cd customer_app && npm install && npx expo start
# vendor
cd vendor_app && npm install && npx expo start
# rider
cd rider_app && npm install && npx expo start
```

Point each app's API base at `API_BASE_URL=http://localhost:8080/api/v1`
(see `.env.example`). Formal migration into `apps/*-mobile` is copy-only:

```bash
./scripts/import-legacy.sh --check   # dry run
./scripts/import-legacy.sh           # merge-copy; originals untouched
```

### 3d. Admin Web (React + Vite control plane)

Host dev (default for frontend dev):

```bash
cd apps/admin-web
npm install
npm run dev   # VITE_API_BASE_URL=http://localhost:8080/api/v1
# open http://localhost:5173 (Vite) — containerised build serves :5173 via nginx
```

Containerised run:

```bash
docker compose up --build admin-web
curl -fsS http://localhost:5173/healthz
```

Admin calls the same API with permission scopes (`VENDOR_APPROVE`, `ORDER_REFUND`,
`PAYOUT_MANAGE`, …) — no direct business-table writes.

### 3e. Supabase + seed

```bash
./infra/local/seed.sh --check   # plan only
./infra/local/seed.sh            # apply supabase/seed/*.sql (idempotent)
```

Migrations live in `supabase/migrations/` (backend/database track). Seeds must
use `INSERT ... ON CONFLICT DO NOTHING` so re-runs are safe.

## 4. Shared contracts

```bash
./scripts/gen-contracts.sh --check   # plan only
./scripts/gen-contracts.sh            # OpenAPI -> packages/api-contracts/src/generated.ts
```

Backend publishes the OpenAPI spec; all clients consume the generated types.
See `docs/api/` (API track) for endpoint details.

## 5. Test credentials (local dummies only)

| Actor | Phone | OTP |
|---|---|---|
| Customer | `+910000000001` | `123456` |
| Vendor | `+910000000002` | `123456` |
| Rider | `+910000000003` | `123456` |

Fake values for local smoke tests only — no real PII, no prod OTP bypass.
Override via `TEST_*` vars in `.env`.

## 6. Known limitations (today)

- `services/api`, `supabase/migrations+seed`, `apps/admin-web`, and
  `packages/api-contracts` land via their respective tracks — until then,
  container builds for `api`/`admin-web` and the seed/contract scripts report
  "nothing to apply yet" instead of failing. `./scripts/verify.sh` reflects this.
- Legacy apps (`customer_app/`, `vendor_app/`, `rider_app/`) are not yet wired to
  the platform API; the end-to-end order→dispatch→delivery→finance flow lights up
  as the backend track lands (MASTER_PROMPT §43/§44).
- Redis has no password locally by default; staging/prod must set auth + TLS
  (see `infra/redis/redis.conf` comments + `docs/operations/env.md`).
- No hosted environments yet — dev/staging/prod hosting choice is still open
  (architecture stays portable per MASTER_PROMPT §33).

## 7. Deploy checklist (any env promotion)

- [ ] `./scripts/verify.sh` green (use `--strict` once service sources exist)
- [ ] `docker compose config` validates; images build clean (`--build`)
- [ ] `.env` per environment from vault — no secrets in git (`git status --porcelain`)
- [ ] Supabase migrations applied + pre-migrate `pg_dump` taken (`docs/operations/backup.md`)
- [ ] Contracts regenerated (`gen-contracts.sh`) and clients rebuilt against them
- [ ] Health probes green: API `/actuator/health`, admin `/healthz`, `redis-cli ping`
- [ ] Payment webhook signature verification smoke-tested with provider sandbox
- [ ] RBAC spot-check: customer/vendor/rider/admin can only touch their own scopes
- [ ] Idempotency spot-check: retried `POST /orders`, `/payments`, `/refunds` create once
- [ ] Finance reconciliation: orders ↔ payments ↔ payouts tie out
- [ ] Rollback plan written (restore point + migration down + traffic-reopen steps)
