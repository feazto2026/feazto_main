# Architecture Overview — Codewild Home-Food Platform

> Source of truth: `MASTER_PROMPT_ENHANCED.md` (Architecture Edition v2.0).
> Status: **baseline (pre-implementation)** — no backend, Supabase project, or Admin app exists yet.
> Date: 2026-10-02.

## 1. One-line architecture

**Four clients, one platform, one source of business truth**: three React Native
mobile apps + one React+Vite Admin web, all speaking to a single Spring Boot
**modular monolith** backed by **Supabase PostgreSQL/Auth/Storage** and **Redis**.

```
                          CODEWILD FOOD PLATFORM
                                   │
           ┌───────────────────────┼────────────────────────┐
           │                       │                        │
           ▼                       ▼                        ▼
   Customer Mobile         Vendor/Home-Cook Mobile     Rider Mobile
   Expo 52 / WebView       Expo 52 / native demo       Expo 57 / expo-router
           │                       │                        │
           └───────────────────────┬────────────────────────┘
                                   │ HTTPS /api/v1 + JWT
                                   ▼
                          Spring Boot API (TO BUILD)
                          MODULAR MONOLITH
                                   │
              ┌────────────────────┼─────────────────────┐
              │                    │                     │
              ▼                    ▼                     ▼
         Supabase DB            Redis              External Providers
         Auth/Storage        Cache/Locks/Events     Payment/Maps/Push
              │
              ▼
         Admin Web (TO BUILD)
         React + Vite
```

## 2. Mandatory principles

| # | Principle | What it forbids |
|---|-----------|-----------------|
| 1 | **One business truth** — Spring Boot owns order, payment, subscription, vendor approval, dispatch, pricing, serviceability, slot capacity, commission, refunds, permissions | Clients computing totals, statuses, eligibility |
| 2 | **Database is not the API** — clients never mutate business tables directly | Direct PostgREST writes to `orders`, `payments`, etc. |
| 3 | **Separate domain from transport** — Controller → Application Service → Domain → Repository | Business logic in `@RestController` |
| 4 | **Explicit state machines** — `POST /orders/{id}/accept`, not `PATCH {status: COMPLETED}` | Free-form status writes |
| 5 | **Events for cross-domain reactions** — internal domain events + outbox where durable | Sync cross-module service calls for side effects |
| 6 | **Server-side security** — frontend role checks are UX only | Relying on hidden screens for authz |
| 7 | **Idempotency for money & fulfilment** — order, payment, webhook, refund, subscription tick, pickup, delivery | Double-charge / double-order on retry |

## 3. C4 container view

| Container | Tech (target) | Responsibility |
|-----------|---------------|----------------|
| Customer app | Expo 52 RN (existing WebView prototype) | Discovery, cart, checkout, tracking, reviews |
| Vendor app | Expo 52 RN (existing local-demo app) | Onboarding, menu, slots/capacity, accept→ready |
| Rider app | Expo 57 RN + expo-router + zustand (existing mock-backed app) | Availability, assignment accept, QR pickup, PoD |
| Admin web | React + Vite + TS (**greenfield**) | Verification, orders, dispatch escalation, finance, support, audit |
| API | Spring Boot 3, Java 17+, Security, Validation, Data JPA, Flyway (**greenfield**) | All business authority; see `domains.md` |
| DB/Auth/Storage | Supabase (Postgres + Auth + Storage) (**greenfield**) | System of record; JWT issuer; private buckets for KYC |
| Supporting infra | Redis (**greenfield**) | Rate limits, OTP throttle, idempotency, locks, cache, dispatch scratch |
| Providers | Razorpay/Stripe adapter, FCM/Expo Push, SMS, Maps | Hidden behind backend interfaces |

## 4. Trust boundary

```
UNTRUSTED: customer/vendor/rider apps, admin browser, payment webhooks
─────────────────────────────────────────────────
TRUST BOUNDARY: Spring Boot JWT validation + RBAC + ownership + state checks
─────────────────────────────────────────────────
PROTECTED: Supabase (service_role), Redis, payment secrets, admin mutations
```

Admin browser is **untrusted** — every privileged call re-authorises server-side
and writes an `audit_logs` row with reason.

## 5. Request path (happy path)

```
Client → POST /api/v1/... (Bearer JWT, Idempotency-Key where required)
  → Spring Security (Supabase JWT verify → platform user/role load)
  → Controller (DTO + validation only)
  → Application Service (transaction: validate → mutate → snapshot → outbox row)
  → Domain events → handlers (notification, finance, realtime, analytics)
  → { success:true, data, message, requestId }
```

## 6. Evolution path (do not pre-extract)

```
PHASE 1  Modular monolith (now)
   ↓
PHASE 2  Async workers for subscription tick / notifications / dispatch retry
   ↓
PHASE 3  Extract ONLY on measured load: dispatch, notifications, search, payments, analytics
```

Extraction trigger = operational metric (p95, queue depth, failure rate), never
"it has its own folder".

## 7. Quality gates (integration ≠ screens compile)

- State machines enforced + history rows written.
- Payment confirmed only via server verification/webhook.
- RBAC + ownership tests green (customer↔customer, vendor↔vendor, rider↔rider isolation).
- Idempotency tests green (double webhook, double tick, double tap).
- RLS deny-by-default; no `service_role` in clients.
- OpenAPI in `packages/api-contracts/openapi.yaml` matches implementation.
- End-to-end scenario in `FINAL_HANDOVER.md` § acceptance passes on real backend.

## 8. Doc map

- Domains & ownership → `domains.md`
- Events & outbox → `events.md`
- Decisions → `adr/ADR-001..010`
- Repo reality & migration risks → `assessment.md`
- Contracts → `../api/openapi.md`, `../api/errors.md`, `../api/idempotency.md`
- Flows → `../workflows/*.md`
- RLS → `../database/rls.md`
- Machine contract → `../../../packages/api-contracts/openapi.yaml`
