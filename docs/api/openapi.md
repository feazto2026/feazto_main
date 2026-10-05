# API Conventions (`/api/v1`)

> Machine contract: `packages/api-contracts/openapi.yaml`.
> Human rules: this file + `errors.md` + `idempotency.md`.

## 1. Base & versioning

- Base: `/api/v1`. Auth: `Authorization: Bearer <Supabase JWT>` except
  `POST /auth/otp/*` and `POST /payments/webhooks/{provider}` (HMAC-signed).
- Additive changes stay in-version; breaking → `/api/v2` + sunset window.
- `requestId` returned on every response (`X-Request-Id` echoed).

## 2. Envelopes

```json
{ "success": true, "data": {}, "message": "Order created successfully", "requestId": "..." }
{ "success": false, "error": { "code": "ORDER_SLOT_FULL", "message": "The selected meal slot is full.", "details": {} }, "requestId": "..." }
```

- `message` is human-readable and localisable; `code` is stable (see `errors.md`).
- Use HTTP codes correctly: `200/201` success, `400` validation, `401` auth,
  `403` permission/ownership, `404` not found, `409` state conflict / duplicate,
  `422` business rule, `429` rate limit, `5xx` server.

## 3. Per-mutation contract (required in OpenAPI descriptions)

`authorization · request · response · validation · business errors · idempotency`.

Example: `POST /orders` requires `CUSTOMER`, recomputes price server-side,
`409 ORDER_SLOT_FULL`, `Idempotency-Key` **required**.

## 4. Pagination / filtering / search

- `GET` lists: `?page=&size=&sort=` (server-side; no unbounded lists).
- Admin tables add `?q=&status=&from=&to=&vendorId=&riderId=`.
- Discovery search: `GET /vendors/search?q=&region=&cuisine=&mealType=` backed by
  Postgres trigram/filters (no Elasticsearch in Phase 1).

## 5. Roles per area

| Prefix | Caller |
|--------|--------|
| `/auth`, `/customers/me`, `/addresses`, `/cart`, `/orders` (own), `/subscriptions` (own), `/reviews` (own) | `CUSTOMER` |
| `/vendors/me`, `/menus` (own), vendor order transitions | `VENDOR` (approved) |
| `/riders/me`, `/deliveries/*` (assigned) | `RIDER` (approved, online) |
| `/admin/*` | scoped admin permissions (ADR-006) |
| `/serviceability/check`, `/slots`, `/menus`, `/vendors` (read) | any authenticated role |

## 6. Module index

`/auth /users /customers /vendors /riders /menus /cart /orders /payments
/subscriptions /deliveries /addresses /serviceability /slots /notifications
/reviews /support /admin` — full paths, schemas, examples in `openapi.yaml`.
