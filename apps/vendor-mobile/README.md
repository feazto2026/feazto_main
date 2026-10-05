# Feazto Vendor / Home Cook App (canonical)

> Canonical location: `apps/vendor-mobile/`. Legacy UI lives at `vendor_app/Feazto_cloud_page/` and is **not** copied or deleted here (see root `LEGACY_MAP.md`).

## Direction

- Presentation: legacy screens in `vendor_app/Feazto_cloud_page/` stay authoritative until each screen is migrated.
- Integration: `api/*.ts` shims wrap `packages/mobile-shared` (envelopes, JWT, Idempotency-Key) against the Spring Boot API (`/api/v1/...`).
- Flow: onboarding → verification → menu → availability → order preparation → payouts.

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
