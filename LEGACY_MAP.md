# LEGACY_MAP — canonical apps → legacy sources

> Rule: legacy folders are **read-only sources**. Do not copy screens wholesale,
> do not delete legacy UI. Migrate per-screen: inspect → map → reuse →
> refactor → integrate, verified against the real backend workflow.
>
> CONFIRMED BUILD (Oct 2026): Customer + Vendor + Rider are **native Expo apps**
> (`expo-router` + `zustand` + TS, modelled on `rider_app/FEAzTO-Rider-App`).
> Admin is a **Vite website only** (`apps/admin-web`). No WebView bundled-HTML
> remains in any canonical app. No mock `FZ-*` orders are fabricated — the
> backend state machine is authoritative.

| Canonical | Legacy (presentation source, untouched) | Integration layer (new) | Notes |
|---|---|---|---|
| `apps/customer-mobile/` | `customer_app/Feazto_Customer_page/` (`App.js`, `app.json`, `customer/`, `scripts/`) | `apps/customer-mobile/api/*.ts` → `packages/mobile-shared/src/*` + native `app/` + `src/` | Native expo-router screens: splash, auth/OTP, home, search, food-detail, cook-detail, cart, checkout, orders, tracking, profile, book-a-cook. Colors/layout preserved (cream `#fdfaeb`, ink `#111111`, yellow `#F5B700`). |
| `apps/vendor-mobile/` | `vendor_app/Feazto_cloud_page/` (`App.js`, `app.json`, `src/model.cjs`, `src/ui.js`, `src/media.js`) | `apps/vendor-mobile/api/*.ts` → `packages/mobile-shared/src/*` + native `app/` + `src/` | Native screens mirror legacy nav: Dashboard, Orders Queue, Menu, Availability, Earnings, Profile. Role-scoped transitions (accept/ready) only. |
| `apps/rider-mobile/` | `rider_app/FEAzTO-Rider-App/` (`app/`, `app/(tabs)/`, `app/(onboarding)/`, `app.json`) | `apps/rider-mobile/api/*.ts` → `packages/mobile-shared/src/*` + native `app/` + `src/` | Native screens mirror reference app: availability, accept, pickup/delivery OTP proof, earnings, support. |
| `apps/admin-web/` | *(greenfield — no legacy admin client)* | `apps/admin-web/src/api/*` direct | Vite website only: Login, Dashboard, Customers, Vendors (+Verification), Orders (+Detail), Subscriptions, Deliveries, Service Zones, Payments, Payouts, Support, Audit, Settings. `RequireScope` gates + `useServerList` server pagination. |
| `packages/mobile-shared/` | — (shared core: `src/client.ts`, `src/env.ts`, `src/auth.ts`, `src/types.ts`, `src/domains.ts`) | consumed by all three `apps/*/api/` shims | Envelopes, JWT (memory access + SecureStore refresh, single-flight), Idempotency-Key + X-Request-Id, `ApiError.kind` (NETWORK/AUTH/VALIDATION/BUSINESS/PAYMENT/NOT_FOUND/PERMISSION/SERVER). Public env vars only. |

## Shim coverage

Each canonical mobile app exposes `api/` shims wrapping `packages/mobile-shared/src`
without duplicating legacy UI:

`auth.ts, vendors.ts, menus.ts, cart.ts, orders.ts, subscriptions.ts, payments.ts, deliveries.ts, notifications.ts, support.ts`
(+ `api/_client.ts` singleton per app with auto refresh-and-retry; base URL from `EXPO_PUBLIC_API_BASE_URL`, default `http://localhost:8080`;
customer additionally exposes `api/bookings.ts` for book-a-cook).

Role guidance: customer uses all ten (+ bookings); vendor centers on auth/vendors/menus/orders/subscriptions/payments/deliveries/notifications/support;
rider centers on auth/orders/deliveries/payments/notifications/support.
Shims exist in all three apps so cross-role reads (e.g. vendor viewing a delivery)
don't require new plumbing.

## Native screen map (customer-mobile)

| Screen | Route | Backend source |
|---|---|---|
| Splash | `app/index.tsx` | `tokenStore.restore()` |
| Sign in / Verify OTP | `app/(auth)/sign-in.tsx`, `verify-otp.tsx` | `auth.requestOtp/verifyOtp` |
| Home | `app/(tabs)/index.tsx` | `vendors.list` |
| Search | `app/(tabs)/search.tsx` | `vendors.list?search` |
| Food detail | `app/food/[id].tsx` | `menus.getItem` |
| Cook detail | `app/cook/[id].tsx` | `vendors.get` + `menus.getMenu` |
| Cart | `app/(tabs)/cart.tsx` | `cart.get/updateQty/remove` |
| Checkout | `app/checkout.tsx` | `cart.addresses` + `cart.checkout` (server-priced) |
| Orders | `app/(tabs)/orders.tsx` | `orders.list` |
| Order detail | `app/orders/[id].tsx` | `orders.get/cancel` |
| Tracking | `app/tracking/[id].tsx` | `orders.get` (state machine) |
| Profile | `app/(tabs)/profile.tsx` | `auth.logout` |
| Book a cook | `app/book-a-cook.tsx` | `bookings.create/list`, `vendors.list` |
