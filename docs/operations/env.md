# Environment variables

> Source of truth for local env: `.env.example`.
> Copy it to `.env` for development. **Never commit `.env` or any real secret.**

## 1. Separation: server vs client

| Scope | Prefix / vars | Where used | Secrecy |
|---|---|---|---|
| Server-only | `DATABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `PAYMENT_SECRET`, `PAYMENT_WEBHOOK_SECRET` / `WEBHOOK_SECRET`, `OTP_SECRET` | `services/api`, `infra/local/*`, `scripts/*`, CI deploy jobs | **Secret.** Never bake into any browser/mobile bundle, never log |
| Semi-public | `SUPABASE_URL`, `SUPABASE_ANON_KEY` | API server + clients (anon key is gated by RLS/policies; service-role key is NOT for clients) | URL/anon key ship to clients by design; service-role key never does |
| Client-safe | `API_BASE_URL`, `VITE_API_BASE_URL`, `EXPO_PUBLIC_*` | `apps/admin-web`, mobile apps | Public by construction (bundled). Must never hold secrets |
| Local ports | `SERVER_PORT`, `REDIS_PORT`, `ADMIN_WEB_PORT` | `docker-compose.yml` | Non-secret |

Rule of thumb: if a variable is read by code that ships to a browser or phone
(`VITE_*`, `EXPO_PUBLIC_*`), it is public. Anything that can charge money,
read another user's rows, or mint sessions stays server-side.

## 2. Variable catalogue

### Server

| Var | Required | Example (placeholder) | Notes |
|---|---|---|---|
| `DATABASE_URL` | Yes (API, seed) | `postgresql://postgres:<db-password>@db.<ref>.supabase.co:5432/postgres` | Direct/Postgres connection. Password hidden; rotate via Supabase dashboard |
| `SUPABASE_URL` | Yes | `https://<project-ref>.supabase.co` | Project URL, shared with clients |
| `SUPABASE_SERVICE_ROLE_KEY` | Yes (API) | `sb_secret_...` / legacy `eyJ...` service-role JWT | **Bypasses RLS.** API + local scripts only |
| `SUPABASE_ANON_KEY` | Yes | `sb_publishable_...` / legacy `eyJ...` anon JWT | Safe for clients under RLS; also used server-side where RLS applies |
| `REDIS_URL` | Yes | `redis://localhost:6379` (host) / `redis://redis:6379` (in compose) | Cache / locks / idempotency / rate limits / OTP throttle |
| `PAYMENT_SECRET` | Yes (payments) | `pay_test_...` | Provider secret key, server-only |
| `PAYMENT_WEBHOOK_SECRET` | Yes (payments) | `whsec_...` | Verifies webhook signatures. `WEBHOOK_SECRET` kept as alias |
| `OTP_SECRET` | Yes (auth) | 32+ random chars | HMAC/OTP hashing pepper. Generate: `openssl rand -hex 32` |
| `SPRING_PROFILES_ACTIVE` | No | `local` | `local` / `dev` / `staging` / `prod` |
| `SERVER_PORT` | No | `8080` | API listen port |

### Client

| Var | Required | Example | Notes |
|---|---|---|---|
| `API_BASE_URL` | Yes (all clients) | `http://localhost:8080/api/v1` | Single canonical base; admin-web + mobiles share it |
| `VITE_API_BASE_URL` | Yes (admin-web build) | `http://localhost:8080/api/v1` | Build arg for `apps/admin-web`; mirrors `API_BASE_URL` |
| `EXPO_PUBLIC_API_BASE_URL` | Yes (mobile, after import) | `http://localhost:8080/api/v1` | Read by Expo config in imported `apps/*-mobile` |
| `EXPO_PUBLIC_SUPABASE_URL` | As needed | `https://<ref>.supabase.co` | Only if a mobile screen talks to Supabase directly (reads) |
| `EXPO_PUBLIC_SUPABASE_ANON_KEY` | As needed | anon key | Anon key only. Never service-role |

### Test (local dummies)

`TEST_CUSTOMER_PHONE`, `TEST_VENDOR_PHONE`, `TEST_RIDER_PHONE`, `TEST_OTP_CODE`
are fake local values for smoke tests. No real PII, no real OTP bypass in
staging/prod.

## 3. Per-environment expectations

| Env | Supabase | Redis | Secrets |
|---|---|---|---|
| Local | Shared dev project or `supabase start` self-host; `DATABASE_URL` points at it | `docker compose up redis` (`infra/redis/redis.conf`) | `.env` from `.env.example`, dummy values OK |
| Dev / Staging | Dedicated Supabase project per env | Managed Redis / compose Redis with password | CI secret store / hosting provider env, never in git |
| Prod | Prod Supabase project, PITR backups on | Managed Redis with auth + TLS, eviction `allkeys-lru`, persistence on | Vault/KMS-backed; rotation runbook in `backup.md` |

## 4. Secret hygiene (mandatory)

1. **Never commit secrets.** `.env`, `*.pem`, `service-account*.json` are git-ignored.
   Verify with `git status --porcelain` and `git check-ignore .env`.
2. **Service-role key is server-only.** Grep bundles before release:
   `grep -r "SERVICE_ROLE" apps/admin-web/dist` must return nothing.
3. **Rotate on leak:** Supabase dashboard → rotate keys → update hosting env →
   restart API → verify with `scripts/verify.sh`. See `runbooks.md`.
4. **Webhook verification first:** every payment webhook handler must verify
   `PAYMENT_WEBHOOK_SECRET` before any state transition (MASTER_PROMPT §17/§28).
5. **Local `.env` stays local:** share `.env.example` updates, never `.env` contents.
