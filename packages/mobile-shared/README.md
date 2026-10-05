# @feazto/mobile-shared

Shared networking + auth helpers for the Customer, Vendor, and Rider React Native
apps. **Adopt without rewriting screens**: point existing service calls at
`createApiClient` and wire `tokenStore` once at the app root.

## Install

```bash
# from the app directory
npm install expo-secure-store   # refresh-token storage (Keychain/Keystore)
```

Then import via a relative path (or workspace alias `@feazto/mobile-shared`):

```ts
import { env, createApiClient, tokenStore, refreshAccessToken, configureRefresh } from '../../packages/mobile-shared';
```

## Wire-up (once per app, e.g. `App.js` / root layout)

```ts
import { env } from '@feazto/mobile-shared/env';
import { createApiClient } from '@feazto/mobile-shared/api-client';
import { tokenStore, configureRefresh, refreshAccessToken } from '@feazto/mobile-shared/auth-refresh';

configureRefresh({ supabaseUrl: env.SUPABASE_URL, supabaseAnonKey: env.SUPABASE_ANON_KEY });

export const api = createApiClient({ getAccessToken: () => tokenStore.accessToken });

// After Supabase OTP verify returns a session:
await tokenStore.setSession(session.access_token, session.refresh_token, session.expires_in);

// On cold start (restore persisted refresh token → fresh access token):
await tokenStore.restore();

// Per request — auto refresh-and-retry once on expired tokens:
const orders = await api.get('/orders', { onUnauthorized: () => refreshAccessToken() });
```

## Conventions

- **Base URL**: `env.API_BASE_URL` + `/api/{API_VERSION}` (see `env.ts`).
- **JWT**: injected from `tokenStore.accessToken` (memory only).
- **Idempotency**: every `POST/PUT/PATCH/DELETE` sends `Idempotency-Key`
  (caller-supplied or generated) — required for orders, payments, refunds,
  pickup/delivery confirmations.
- **Errors**: all failures throw `ApiError` with `kind` =
  `NETWORK | AUTH | VALIDATION | BUSINESS | PAYMENT | NOT_FOUND | PERMISSION | SERVER`,
  plus the backend's stable `code` (e.g. `VENDOR_NOT_APPROVED`, `OTP_EXPIRED`)
  and `requestId` for support.
- **Migration note**: existing prototype `authStore.ts` files that accept a
  hard-coded `1234` OTP are dev-only. Replace their `sendOtp`/`verifyOtp` bodies
  with Supabase `signInWithOtp`/`verifyOtp` + the helpers above; keep the screen
  components untouched.

## Env vars (Expo extra or process.env)

| Var | Required | Purpose |
|---|---|---|
| `API_BASE_URL` / `EXPO_PUBLIC_API_BASE_URL` | yes | Spring Boot origin |
| `SUPABASE_URL` / `EXPO_PUBLIC_SUPABASE_URL` | yes | Supabase project URL (public) |
| `SUPABASE_ANON_KEY` / `EXPO_PUBLIC_SUPABASE_ANON_KEY` | yes | Publishable key (public) |
| `API_VERSION` | no | default `v1` |

> Never add `SERVICE_ROLE`, webhook, or SMS secrets here — backend-env-only.

## Relationship to `src/` domain helpers

A sibling `src/` folder (added by the API-contracts track: `client.ts`,
`domains.ts`, `types.ts`, `createClient`) provides typed domain endpoints over
the same envelope. Use both layers together:

- `api-client.ts` (this file's level) — transport: baseURL, JWT, idempotency,
  `X-Request-Id`, `ApiError.kind` mapping, refresh-retry hook.
- `src/domains.ts` — endpoint shapes (`ordersApi`, `paymentsApi`, …).
- `auth-refresh.ts` — token lifecycle (`tokenStore`, single-flight refresh).

If `src/client.ts`'s `TokenStore` is already wired in an app, keep it and feed
its token into `createApiClient({ getAccessToken })` — or migrate the token to
`tokenStore` so refresh is single-flight and the refresh token lives in
SecureStore rather than memory/AsyncStorage.
