# Feazto Rider / Delivery Partner App (canonical)

> Canonical location: `apps/rider-mobile/`. Legacy UI lives at `rider_app/FEAzTO-Rider-App/` and is **not** copied or deleted here (see root `LEGACY_MAP.md`).

## Direction

- Presentation: legacy screens in `rider_app/FEAzTO-Rider-App/` stay authoritative until each screen is migrated.
- Integration: `api/*.ts` shims wrap `packages/mobile-shared` (envelopes, JWT, Idempotency-Key) against the Spring Boot API (`/api/v1/...`).
- Flow: onboarding → availability → assignment → pickup → delivery proof → earnings.

## Shims

| file | domain |
|---|---|
| `api/auth.ts` | `authApi` |
| `api/vendors.ts` | `vendorsApi` |
| `api/menus.ts` | `menusApi` |
| `api/cart.ts` | `cartApi` |
| `api/orders.ts` | `ordersApi` |
| `api/subscriptions.ts` | `subscriptionsApi` |
| `api/payments.ts` | `paymentsApi` |
| `api/deliveries.ts` | `deliveriesApi` |
| `api/notifications.ts` | `notificationsApi` |
| `api/support.ts` | `supportApi` |

`api/_client.ts` owns the shared client singleton (base URL from `EXPO_PUBLIC_API_BASE_URL`, default `http://localhost:8080`).

## Use in (legacy or new) screens

```ts
import { cart } from './api/cart.js';
const page = await cart.get();
```

## Migration rule

Inspect → map → reuse → refactor → integrate. Delete legacy only when the migrated screen is verified against the real backend workflow.
