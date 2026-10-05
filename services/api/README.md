# Codewild Food Platform — Backend (services/api)

Spring Boot 3 (Java 17) modular monolith. Authoritative business layer for customer/vendor/rider/admin clients.

## Quick start

```bash
cd services/api
# Postgres + Redis (local)
docker run -d --name food-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=food_platform -p 5432:5432 postgres:16
docker run -d --name food-redis -p 6379:6379 redis:7

export DATABASE_URL=jdbc:postgresql://localhost:5432/food_platform DB_USER=postgres DB_PASSWORD=postgres
export JWT_SECRET='<32+ byte secret>'
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Swagger: http://localhost:8080/swagger-ui.html · Health: `/actuator/health`

## Conventions

- Base path `/api/v1/*`. Success envelope `{success:true,data,message,requestId}`; error `{success:false,error:{code,message},requestId}` with stable `ErrorCodes`.
- Auth: Supabase/JWT Bearer → `JwtAuthFilter` → roles. Ownership enforced server-side via `SecurityUtils.requireOwnerOrAdmin(...)`; admin routes require `ROLE_ADMIN`.
- State machines: `OrderStateMachine`, `DeliveryStateMachine` — invalid transitions (e.g. `COMPLETED->PREPARING`) throw `INVALID_STATE_TRANSITION`. Never accept raw `status` from clients; use action endpoints (`/accept`, `/prepare`, `/ready`, …).
- Idempotency: send `Idempotency-Key` on `POST /orders, /payments, /refunds, /subscriptions, /deliveries/*/pickup|complete`. Redis pre-check + DB unique constraints (backstop). Webhooks/generators are idempotent (re-delivery safe; `sub:{id}:{date}` generator keys).
- Events/outbox: services persist `OutboxEvent` in the same transaction, publish via `EventBus`; `OutboxService` scheduler re-publishes pending. Notifications subscribe to semantic events, never block business state.
- Modules: `identity customer marketplace commerce subscription fulfillment finance operations notification serviceability` under `com.codewild.food.*`, each with `api/application/domain/infrastructure`. Shared kernel in `shared/` (security, events, errors, idempotency, audit, observability).

## Key routes

| Method | Path | Notes |
|---|---|---|
| POST | /api/v1/auth/otp/send, /otp/verify, /login | public |
| GET/PUT | /api/v1/customers/me | owner |
| POST/GET | /api/v1/vendors, /vendors/{id}/menu | vendor owner check on write |
| POST | /api/v1/orders (Idempotency-Key) | CREATED→PAYMENT_PENDING |
| POST | /api/v1/orders/{id}/accept|prepare|ready|cancel|complete | transition-validated |
| POST | /api/v1/payments, /payments/webhook, /refunds | idempotent |
| POST | /api/v1/subscriptions, …/pause|cancel|generate | idempotent generator |
| POST | /api/v1/deliveries/for-order/{orderId}, /{id}/assign|pickup|complete | rider-owner + QR stub |
| POST | /api/v1/serviceability/check | public, explains unavailability |
| POST | /api/v1/admin/vendors/decide | ADMIN only + audited |
| GET | /api/v1/notifications/me | owner |
| POST | /api/v1/finance/payouts | ADMIN/FINANCE_ADMIN |
