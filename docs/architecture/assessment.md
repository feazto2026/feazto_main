# Repository Assessment (Discovery — 2026-10-02)

> Read-only inspection. **No UI folders were modified.**
> Source: `customer_app/Feazto_Customer_page`, `vendor_app/Feazto_cloud_page`,
> `rider_app/FEAzTO-Rider-App`, `MASTER_PROMPT_ENHANCED.md`.

## 1. At-a-glance

| Area | Customer | Vendor | Rider | Admin | Backend | Supabase/Redis/Infra |
|------|----------|--------|-------|-------|---------|----------------------|
| Location | `customer_app/Feazto_Customer_page` | `vendor_app/Feazto_cloud_page` | `rider_app/FEAzTO-Rider-App` | **missing** | **missing** (`services/api` greenfield) | **missing** (greenfield) |
| Expo / RN / React | Expo `^52.0.0`, RN `0.76.5`, React `18.3.1` | Expo `52.0.49`, RN `0.76.9`, React `18.3.1` | Expo `~57.0.0`, RN `0.86.3`, React `19.2.3` | — | — | — |
| Routing | none (single `App.js` shell) | none (in-component `page` state) | `expo-router ~57.0.23` (`(auth)`, `(onboarding)`, `(tabs)`) | — | — | — |
| State | none (delegated to bundled HTML) | `useState` + AsyncStorage `feazto-cloud-kitchen-v1` | `zustand` (`authStore`, `riderStore`, `orderStore`, `slotStore`, …) | — | — | — |
| API layer | none | none | `src/services/*.ts` — **mock-backed** (`auth`, `orders`, `dispatch`, `realtime`, `earnings`, …) | — | — | — |
| Auth | none | demo flag `loggedIn:true` | mock `authStore.isAuthenticated` + `verificationStatus` | — | — | — |
| Backend refs | none | none | none (no base URL / token wiring found) | — | — | — |

## 2. Per-app findings

### 2.1 Customer — WebView prototype shell (Expo 52)

- `App.js` is a **hybrid WebView wrapper**: loads `customer/bundled-html.js`
  (bundled from `prototype.js`/`prototype.css` + ~60 images in `customer/images/`)
  into `react-native-webview ^13.12.5`; on web renders an `iframe srcDoc`.
- Android back button bridged via injected `feazto-back` event; `canGoBack`
  tracked through `onMessage`. Offline/fail state is a static retry card.
- Deps: `@react-navigation/native ^7` present but **unused** (no navigators);
  `@expo/metro-runtime`, `expo-status-bar`, safe-area/screens present.
- **No** Supabase/Auth/API/storage/networking code; all screens, cart, checkout
  are HTML/JS inside the bundle — **entirely client-side mock**.
- Service seams: **none usable** — no `api/` module to swap. Integration =
  build a real `api/` client + auth session against `/api/v1` and progressively
  replace WebView screens with native ones (or keep WebView temporarily behind
  a backend-driven flag — see risks).

### 2.2 Vendor — local-demo native app (Expo 52)

- Single-component `App.js` (+ `src/ui.js`, `src/media.js`, `src/model.cjs`):
  role switch kitchen/rider, dashboard, orders queue, menu CRUD, availability
  toggle, community posts, earnings placeholders — all **in-memory + AsyncStorage**.
- `src/model.cjs`: `initialState()` (Lakshmi Kitchen demo menu/orders),
  `transitionOrder()` enforcing `NEW→PREPARING→READY→ASSIGNED→PICKED_UP→COMPLETED`,
  `validateMenu()`, `loadState()`. A useful **state-shape reference** for the
  server machine, but client-side and **must not become authority**.
- Media via `expo-image-picker`, video via `expo-av`; `node_modules/` checked
  into the vendor folder (bloat, ignore in future monorepo).
- No navigation lib, no API/auth/validation packages, no env config, no tests
  beyond `tests/*.test.cjs` (model-level).
- Service seams: `model.cjs` transitions ≈ server `OrderTransitionValidator`
  spec; persistence seam = replace `AsyncStorage(KEY)` with API repository.

### 2.3 Rider — most complete shell (Expo 57, expo-router + zustand, mock services)

- Real native structure: `app/(auth)/sign-in,otp,verification-status`,
  `app/(onboarding)/personal-details,licence,documents,vehicle,bank-upi,review`,
  `app/(tabs)/orders,earnings,profile`, plus `delivery,support,slots,safety,incentives,demo`.
- `src/services/` (`dispatch.ts`, `realtime.ts`, `notifications.ts`, …) +
  `src/store/*` + `src/mocks/*` + `src/types/*` = **the cleanest seam in the
  repo**: swap each service's mock for an HTTP implementation against
  `packages/api-contracts/openapi.yaml` without touching screens.
- Newest stack (Expo 57 / RN 0.86 / React 19 / `react-native-reanimated 4`,
  `gesture-handler`, `svg`, `worklets`) — **diverged from customer/vendor
  (Expo 52 / React 18)**. `zustand ^5` only here.
- No backend URL, no token refresh, no Supabase dependency, no E2E tests.

## 3. Expo 52 / 57 divergence

| Risk | Detail | Mitigation |
|------|--------|------------|
| Dual SDKs (52 vs 57) | Different RN/React/new-arch behaviour; `expo-router 57` + Reanimated 4 untested on 52 apps | Align all three on **one SDK** (recommend 57 if EAS/nightly verified, else pin 52 + backport rider) after spike; single `.nvmrc`/CI matrix |
| React 18 vs 19 | Shared `packages/*` code must compile under both until aligned | Keep shared packages lowest-common-denominator; no React-19-only APIs in shared code |
| Navigation split | react-navigation (unused) vs ad-hoc state vs expo-router | Standardise on **expo-router** (already proven in rider) during migration |
| WebView vs native | Customer is WebView; others native → inconsistent auth storage, push, deep links | Treat WebView as **throwaway**: native discovery/cart/checkout per master spec; share `api/auth` helpers |

## 4. WebView vs native verdict

Customer WebView preserves prototype CSS/animation but **cannot satisfy**
server-authoritative cart/pricing/payment/dispatch, secure token storage, push
handling, or offline honesty. Keep it only as a **visual reference + interim
fallback**; the production path is native screens consuming `/api/v1`
(mirror vendor/rider feature modules). Never inject secrets or business logic
into bundled HTML.

## 5. Gaps vs target (nothing to reuse as backend)

- No `services/api`, `apps/*`, `packages/*`, `supabase/`, `infra/`,
  `docker-compose.yml`, `.env.example`, OpenAPI, migrations, admin-web.
- No auth (Supabase Auth/Spring Security), no RBAC, no RLS, no idempotency,
  no outbox, no payment/dispatch/subscription logic anywhere — all demo/mock.
- Vendor `transitions` map is a **spec input**, not an implementation.

## 6. Top risks

1. **Mock-to-real cliff** — three apps assume local success; first real-API
   wiring will surface missing loading/empty/error/retry/token-refresh states.
2. **Price/status forgery** — current clients compute totals and advance
   statuses; backend must recompute and reject (tests in §8 of master spec).
3. **Capacity oversell** — no slot locking; implement DB-guard + Redis lock first.
4. **Expo split** — mixed SDKs break shared code and CI; align early.
5. **WebView auth/push** — tokens in WebView storage are weaker; move auth
   native before handling real payments.
6. **node_modules + images committed** — repo bloat; add ignores, LFS/CDN for assets.
7. **No audit/RLS** — admin and KYC flows must wait for backend + private buckets.

## 7. Migration order (mirrors master Phases 0–13)

1. Consolidate repo (`apps/*`, `services/api`, `packages/api-contracts`,
   `supabase/`, `infra/`, `docs/`) without moving UI code yet.
2. Align Expo SDK + introduce shared `api/auth/error` package; rider services
   first (cleanest seam), then vendor `model.cjs → API`, then customer native.
3. Backend Phases 2–6 (auth → profiles → vendor/menu → discovery/cart →
   payment/order machine) before touching dispatch/subscriptions/finance.
4. Keep WebView readable but **write no new WebView features**; delete when
   native parity + acceptance scenario pass.
