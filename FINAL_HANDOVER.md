# FINAL_HANDOVER — Codewild Home-Food Platform (Architecture Baseline)

> Date: 2026-10-02 · Edition: Architecture v2.0 · Scope: **docs + API contract only**.
> No UI code was touched. Backend / Supabase / Admin are specified, not yet built.

## 1. Final architecture (as specified)

Four clients → one Spring Boot **modular monolith** (`/api/v1`) → Supabase
PostgreSQL/Auth/Storage + Redis + provider adapters (payment/maps/push).
Admin web is a client of the same API with scoped permissions + audit.
Business truth hierarchy: **PostgreSQL → Spring domain → Redis/clients**
(never reversed). State machines explicit; cross-domain reactions via domain
events + transactional outbox; idempotency on all money/fulfilment mutations.
Details: `docs/architecture/overview.md`, `domains.md`, `events.md`,
`adr/ADR-001..010`; repo reality in `docs/architecture/assessment.md`.

```
Customer(Expo52/WebView) · Vendor(Expo52/demo) · Rider(Expo57/router+zustand) ─┐
                                                                              ├─► Spring Boot /api/v1 ─► Supabase + Redis
Admin Web (React+Vite, TO BUILD) ─────────────────────────────────────────────┘
```

## 2. What was delivered in this pass

```
docs/architecture/overview.md · domains.md · events.md · assessment.md
docs/architecture/adr/ADR-001..010 (monolith, Supabase, Redis, outbox, payment,
  roles, subscription-tick, dispatch, versioning, DB ban)
docs/api/openapi.md · errors.md · idempotency.md
docs/workflows/order.md · subscription.md · dispatch.md · payment.md
docs/database/rls.md
packages/api-contracts/openapi.yaml (all /api/v1 areas + schemas + idempotency notes)
FINAL_HANDOVER.md (this file)
```

## 3. Target repo layout (to create — NOT yet created)

```
apps/{customer-mobile,vendor-mobile,rider-mobile,admin-web}
services/api (com.codewild.food: identity,customer,marketplace,commerce,
  subscription,fulfillment,finance,operations,notification,shared/*)
packages/{api-contracts (this YAML),shared-types,validation}
supabase/{migrations,seed,config} · infra/{docker,redis,local} · docs/… · scripts/
```

## 4. Setup — prerequisites

Java 17+, Maven wrapper, Node 20.19+, Docker + Compose, Supabase CLI,
Expo/EAS CLI, Redis 7. Accounts: Supabase project, payment provider
(Razorpay/Stripe test), Expo push, maps key.

## 5. Environment variables

```bash
# ── Server-only (NEVER in mobile/web bundles) ──
DATABASE_URL=postgresql://…            # Supabase Postgres (service_role path server-side)
SUPABASE_URL=https://xyz.supabase.co
SUPABASE_SERVICE_ROLE_KEY=…            # backend only
SUPABASE_JWT_SECRET=…                  # JWT validation
REDIS_URL=redis://localhost:6379
PAYMENT_PROVIDER=razorpay
PAYMENT_SECRET=…  PAYMENT_WEBHOOK_SECRET=…
OTP_PROVIDER_SECRET=…
# ── Public client-safe ──
API_BASE_URL=http://localhost:8080/api/v1
SUPABASE_URL=https://xyz.supabase.co
SUPABASE_ANON_KEY=…
EXPO_PROJECT_ID=…
```

Keep `.env.example` (template, no secrets) + `.env.development/.staging/.production`.

## 6. Migration instructions (when backend lands)

1. `supabase db push` chain in `supabase/migrations/` (Flyway-compatible numbering).
2. Order: users/profiles → marketplace/menu/slots/zones → commerce (orders/payments w/
   `provider_event_id`, `idempotency_key` uniques + snapshots) → subscription
   (`subscription_id,date,slot` unique) → fulfilment/finance/support/audit/outbox.
3. Enable RLS per `docs/database/rls.md` (Class A deny-all default) and verify
   with anon/authenticated keys that business writes fail.
4. Seed demo zones/slots/cuisines only (`supabase/seed/`).

## 7. Service startup (target commands — adapt to real package managers)

```bash
# infra
docker compose up -d redis
# backend
cd services/api && ./mvnw spring-boot:run        # → http://localhost:8080/api/v1
# mobile (after consolidation under apps/)
cd apps/customer-mobile && npm install && npm run start
cd apps/vendor-mobile   && npm install && npm run start
cd apps/rider-mobile    && npm install && npm run start   # Expo 57
# admin (to build)
cd apps/admin-web && npm install && npm run dev
# contract check
npx @redocly/cli lint packages/api-contracts/openapi.yaml
```

Supabase local: `supabase start`; link: `supabase link --project-ref <ref>` then
`supabase db push`. Redis: `redis-cli -u $REDIS_URL ping` → `PONG`.

## 8. Test credentials (local/dev ONLY — no real PII/secrets)

- OTP: use Supabase test phone numbers / fixed dev OTP documented in backend README
  (never log OTPs; throttle enforced).
- Seeded logins (to create in dev seed): `customer@test.feazto / vendor@test.feazto /
  rider@test.feazto / ops-admin@test.feazto` with vendor `Lakshmi Kitchen` (approved)
  + lunch slot capacity 20 + Anna Nagar zone. Payment: provider **sandbox** keys +
  test UPI/cards; webhooks via provider CLI forwarding with test secret.
- These accounts exist only after Phase 2–4 implementation; today there are no
  backends to log into (apps are demo/mock).

## 9. API docs location

- Machine: `packages/api-contracts/openapi.yaml` (import into Swagger UI/Redoc;
  generate client types per app).
- Human: `docs/api/openapi.md` (conventions), `docs/api/errors.md` (stable codes),
  `docs/api/idempotency.md` (protocol), `docs/workflows/*.md` (flows + machines).

## 10. Known limitations (this baseline)

- No backend, migrations, Supabase project, Redis config, or Admin app yet.
- Customer app is a WebView mock; vendor is local-demo; rider services are mocks —
  none speak to any API (see `assessment.md`).
- Expo split (52 vs 57) + React 18 vs 19 unresolved; align before shared packages.
- OpenAPI is a **contract-first spec** awaiting implementation + CI diff.
- RLS SQL, outbox relay, dispatch ranking, provider adapters are specified, not coded.

## 11. Production checklist (before any live order)

Security: JWT validation, RBAC+ownership tests, webhook HMAC, RLS deny verified,
service_role absent from bundles, CORS/headers, rate-limit + OTP throttle live,
private KYC buckets, audit on all admin writes. Reliability: idempotency tests
(double webhook/tick/tap), state-machine rejection tests, outbox relay + DLQ
monitored, capacity lock under load, scheduler catch-up runbook, backups/restore
drilled, secrets rotation. Ops: requestId/trace logging (no OTP/secrets),
health `/actuator/health` (api/db/redis), dashboards (latency, payment success,
assignment rate, churn), on-call + incident runbooks, sunset/version policy.

## 12. Acceptance scenarios → spec mapping

**A. One-time order (Coimbatore customer in Chennai)** — `docs/workflows/order.md`
+ `payment.md` + `dispatch.md`; contract `openapi.yaml#/orders|/payments|/deliveries`;
codes in `errors.md`; idempotency in `idempotency.md`. Pass = steps 1–43 of master
§62 execute on real backend (vendor approve → discover → cart → pay → verify →
accept → prepare → ready → assign → QR pickup → PoD → complete → review →
commission/payout/audit visible in Admin). Any mocked hop = fail.

**B. Subscription** — `docs/workflows/subscription.md` (+ ADR-007). Pass = activate →
tick creates exactly one daily order (`MEAL_ALREADY_GENERATED` on re-fire) →
vendor/rider fulfil → pause stops future meals without touching history →
cancel/refund per policy; payment-failure path tested.
