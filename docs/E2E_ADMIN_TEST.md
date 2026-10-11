# E2E Admin Test — PASS Handover (2026-10-09)

> Status: **PASS** — backend `http://localhost:8082` UP, Redis `PONG`, Supabase pooler `6543`.
> Test admin: `feazto26@gmail.com` — password is **NOT** in this file (see §1).

## 1. Test admin identity (masked)

| Field | Value |
|---|---|
| Email | `feazto26@gmail.com` |
| Password | `<password>` — test-only value shared separately (rotate after handover) |
| Supabase `auth.users.id` | `645ca7a2-a73c-4aa7-b6f8-6f97b8c54b80` |
| Platform `users.id` | `546091be-b7ac-44f1-abe4-c72f540ef7f1` |
| Roles (server-side `user_roles`) | `ADMIN`, `CUSTOMER`, `SUPER_ADMIN` |
| Status | `ACTIVE` |
| Auth model | Supabase Auth is password truth. `POST /api/v1/admin/auth/login` calls GoTrue `${SUPABASE_URL}/auth/v1/token?grant_type=password` with `apikey: SUPABASE_ANON_KEY`. Never use `SUPABASE_SERVICE_ROLE_KEY` in request paths. See `services/api/src/main/resources/application-security.example.yml`. |

Credential hygiene:

- Do **NOT** commit `.env`, `backend-start-8082.cmd` secrets, or real passwords. `.env.example` + `apps/admin-web/.env.example` are templates only.
- All secrets below are shown as `<placeholder>`. Real values live only in local env / password manager.
- Rotate test password after handover. Revoke/rotate `DB_PASSWORD`, `SUPABASE_*`, `JWT_SECRET`, `OTP_SECRET`, `PAYMENTS_WEBHOOK_SECRET` if they were shared in chat/logs.
- Redis throttle (fail-closed): `admin:login:req:ip:{ip}` 30/h + `admin:login:req:email:{lower}` 10/h, 1 h TTL.

## 2. Infra snapshot (verified this run)

- Backend: `http://localhost:8082` — PID `11436` — `/actuator/health` `UP`.
- Redis: `docker exec feazto-redis redis-cli ping` → `PONG` (`redis:7-alpine`, `infra/redis/redis.conf`, host `6379`).
- Supabase project: `tznqnfqrighkjocpprir` (`https://tznqnfqrighkjocpprir.supabase.co`).
- DB via pooler: `aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0` with user `postgres.<project-ref>`. (`RUNBOOK.md` previously noted `aws-1-ap-south-1`; current boot uses `aws-0-ap-southeast-1` — either pooler host works, keep `?sslmode=require&prepareThreshold=0`.)
- Why 8082: `8080` taken (`httpd.exe`), `8081` reserved for Expo — do not use. `SERVER_PORT=8082`.

## 3. Setup

```powershell
# prerequisites: Java 17.0.11, Maven 3.9.11, Docker, Node 18+
java -version; mvn -version; docker --version

# redis
docker compose up -d redis
docker exec feazto-redis redis-cli ping  # PONG

# backend build
cd D:\feazto_main\services\api
mvn -B -DskipTests clean package
```

## 4. Env — pooler + Flyway baseline (masked, do not paste secrets)

Backend reads `application.yml` + `application-security.example.yml` via env (see `services/api/src/main/resources/`):

```cmd
REM backend-start-8082.cmd — template only, fill from vault
set "DATABASE_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0"
set "DB_USER=postgres.<project-ref>"
set "DB_PASSWORD=<db-password>"
set "REDIS_HOST=localhost"
set "REDIS_PORT=6379"
set "SUPABASE_URL=https://<project-ref>.supabase.co"
set "SUPABASE_ANON_KEY=<publishable-key>"
set "SUPABASE_SERVICE_ROLE_KEY=<secret-server-only>"
set "SUPABASE_JWKS_URL=https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json"
set "SUPABASE_JWT_ISSUER=https://<project-ref>.supabase.co/auth/v1"
set "SUPABASE_JWT_AUDIENCE=authenticated"
set "OTP_SECRET=<32+-chars-random>"
set "JWT_SECRET=<32+-bytes-random>"
set "PAYMENTS_WEBHOOK_SECRET=<hmac-secret>"
set "PAYMENT_SECRET=<placeholder>"
set "PAYMENT_WEBHOOK_SECRET=<placeholder>"
set "SERVER_PORT=8082"
set "SPRING_PROFILES_ACTIVE=local"
set "SPRING_FLYWAY_BASELINE_ON_MIGRATE=true"
set "SPRING_FLYWAY_BASELINE_VERSION=0"
set "CORS_ALLOWED_ORIGINS=http://localhost:5173"
java -jar services/api/target/food-platform-api-0.1.0-SNAPSHOT.jar --server.port=8082
```

Notes from actual code:

- `spring.datasource.url=${DATABASE_URL}`, `username=${DB_USER}`, `password=${DB_PASSWORD}` (`application.yml`).
- Flyway: `enabled:true`, `locations:classpath:db/migration`. Baseline flags above are required on existing Supabase DBs or migrate fails.
- Redis: code uses `REDIS_HOST`/`REDIS_PORT` (`spring.data.redis.host/port`). `REDIS_URL` alone is ignored — set both.
- CORS: `security.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:}` (`application-security.example.yml` → `SecurityConfig.buildCors`). Empty = no explicit origins (see §7). Set `CORS_ALLOWED_ORIGINS=http://localhost:5173` for admin-web dev.
- JWT trust: Supabase JWKS only (`SupabaseJwtValidator` + `SupabaseJwksSignatureVerifier`). Legacy HS256 minting deleted — roles loaded server-side from `user_roles`, never from token claim.

Run + verify:

```powershell
java -jar services/api/target/food-platform-api-0.1.0-SNAPSHOT.jar --server.port=8082
curl.exe -s http://localhost:8082/actuator/health
# {"status":"UP"}
curl.exe -s -o NUL -w '%{http_code}\n' http://localhost:8082/v3/api-docs            # 200
curl.exe -s -o NUL -w '%{http_code}\n' http://localhost:8082/swagger-ui/index.html  # 200
```

## 5. Login curl (masked)

```powershell
# admin login — GoTrue password grant behind the API
curl.exe -s -X POST http://localhost:8082/api/v1/admin/auth/login `
  -H 'Content-Type: application/json' `
  -d '{"email":"feazto26@gmail.com","password":"<password>"}'

# PASS evidence this run: 200, token len ~850, SUPER_ADMIN, 15 scopes, reqId 3bb75ba8
# save token:
# $TOKEN = '<jwt-from-login>'

# me / auth checks
curl.exe -s http://localhost:8082/api/v1/admin/auth/me -H "Authorization: Bearer $TOKEN"
# 200 PASS

curl.exe -s http://localhost:8082/api/v1/auth/roles -H "Authorization: Bearer $TOKEN"
# roles PASS

curl.exe -s http://localhost:8082/api/v1/customers/aec645a9 -H "Authorization: Bearer $TOKEN"
# customer lookup PASS (id prefix aec645a9)

# OTP public probe (rate-limited, no auth)
curl.exe -s -X POST http://localhost:8082/api/v1/auth/otp/request `
  -H 'Content-Type: application/json' -d '{"phone":"+919999999999"}'
# 200 {"success":true,...} PASS
```

PowerShell quoting: use `curl.exe` (not `curl` alias) and backtick line continuations as above.

## 6. E2E curl chain — vendor → order → delivery → payment (PASS evidence inline)

Base: `$BASE=http://localhost:8082/api/v1`, `$TOKEN` from §5. Idempotency-Key required on `POST /orders, /payments, /deliveries/*/pickup|complete`.

```powershell
# 1) vendors — 1 row: Anbu, phone 7777...771, APPROVED
curl.exe -s "$BASE/vendors" -H "Authorization: Bearer $TOKEN"

# 2) menu — 4 items, first 888...885, 189.00
curl.exe -s "$BASE/vendors/<vendorId>/menu" -H "Authorization: Bearer $TOKEN"

# 3) admin decide — 200 reqId d8d3cb88
curl.exe -s -X POST "$BASE/admin/vendors/decide" `
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' `
  -d '{"vendorId":"<vendorId>","decision":"APPROVE","reason":"e2e probe"}'

# 4) create order — id a0be5187, PAYMENT_PENDING, 189.00, reqId bb3ef33e
curl.exe -s -X POST "$BASE/orders" `
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' `
  -H 'Idempotency-Key: e2e-<uuid>' `
  -d '{"vendorId":"<vendorId>","items":[{"menuItemId":"888...885","qty":1}],"addressId":"<addressId>"}'

# 5) delivery for order — id 26873ed9, AVAILABLE, reqId 12a84eb0
curl.exe -s -X POST "$BASE/deliveries/for-order/<orderId>" `
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' `
  -H 'Idempotency-Key: e2e-<uuid>' -d '{}'

# 6) payment with Idempotency-Key 2f10dd12 — CREATED
curl.exe -s -X POST "$BASE/payments" `
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' `
  -H 'Idempotency-Key: 2f10dd12-<uuid>' `
  -d '{"orderId":"a0be5187","amount":189.00,"method":"UPI"}'

# 7) webhook without signature — 401 gate PASS (HMAC-verified, no JWT)
curl.exe -s -o NUL -w '%{http_code}\n' -X POST "$BASE/payments/webhook" `
  -H 'Content-Type: application/json' -d '{"orderId":"a0be5187"}'
# expect 401

# 8) notifications — [] PASS
curl.exe -s "$BASE/notifications/me" -H "Authorization: Bearer $TOKEN"
```

Envelope: success `{success:true,data,message,requestId}`; error `{success:false,error:{code,message},requestId}`. Always log `requestId` (`3bb75ba8`, `d8d3cb88`, `bb3ef33e`, `12a84eb0` this run).

State machines: use action endpoints (`/orders/{id}/accept|prepare|ready|cancel|complete`, `/deliveries/{id}/assign|pickup|complete`). Never PUT raw `status` — `INVALID_STATE_TRANSITION` otherwise. See `services/api/README.md`.

## 7. admin-web dev — port 5173 + proxy fix to 8082 + CORS

Actual code today (`apps/admin-web/vite.config.ts`):

```ts
server: { port: 5174, proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } } }
```

That is stale for this handover (API is on **8082**, standard admin-web port is **5173**). Fix before `npm run dev`:

```ts
// apps/admin-web/vite.config.ts
server: {
  port: 5173,
  proxy: { '/api': { target: 'http://localhost:8082', changeOrigin: true } }
},
preview: { port: 5173 }
```

```bash
# apps/admin-web/.env.local (never commit)
VITE_API_BASE_URL=http://localhost:8082
# or relative via proxy: VITE_API_BASE_URL=/api
```

```powershell
cd apps/admin-web
npm install
npm run dev  # http://localhost:5173
```

CORS (`services/api/.../shared/security/SecurityConfig.java`):

- `CORS_ALLOWED_ORIGINS` (comma-separated, e.g. `http://localhost:5173`) → `security.cors.allowed-origins`. Empty = no allowed origins configured (dev proxy avoids CORS entirely — prefer proxy over `*`).
- JWTs ride in `Authorization` headers, never cookies (`allowCredentials=false`).
- Mobile apps use non-browser networking — CORS list only governs Admin Web + docs.

## 8. Known gaps (do not treat as prod-ready)

| # | Gap | Evidence / repro | Action |
|---|---|---|---|
| 1 | Payments without `Idempotency-Key` → `500` (should be `400`/`422` envelope) | `POST /payments` no-key `500` | Add validation filter; return `error.code=MISSING_IDEMPOTENCY_KEY` |
| 2 | Malformed JSON → `500` (should be `400`) | Bad body `500` | Add `HttpMessageNotReadableException` handler → `400 INVALID_REQUEST_BODY` |
| 3 | OTP/login throttle → `429` on repeat probes | Rapid `otp/request` `429` | Expected defence (`otp:req:{phone}` 5/h, `otp:req:ip:{ip}` 20/h); back off, use distinct phones in scripts |
| 4 | Webhook with dummy secret → `500` on HMAC path; without signature correctly `401` | §6 step 7 | Set real `PAYMENTS_WEBHOOK_SECRET` in prod; keep fail-closed |
| 5 | Vite defaults stale (`5174`→`8080`) | `vite.config.ts`, `.env.example` | Apply §7 fix; update `.env.example` to `8082` |
| 6 | Placeholder `OTP/JWT/WEBHOOK` secrets in dev | `backend-start-8082.cmd` dummies | Rotate to vault values before any shared/prod env |
| 7 | `REDIS_URL` ignored | Uses `REDIS_HOST/PORT` | Set `REDIS_HOST/PORT`; don't rely on `REDIS_URL` alone |

Also carried from `services/api/RUNBOOK.md`: serviceability endpoint returns `401` vs older README `public`; `/api/*` filter may over-match — verify before exposing new public routes.

## 9. Stop / restart

```powershell
# stop backend (Ctrl+C in java window, or:)
taskkill /PID 11436 /F
# redis
docker compose stop redis
netstat -ano | findstr "8082 6379"
# restart
docker compose up -d redis
java -jar services/api/target/food-platform-api-0.1.0-SNAPSHOT.jar --server.port=8082
```

---
*Generated 2026-10-09. Secrets masked — test password shared separately. Evidence requestIds: login `3bb75ba8`, decide `d8d3cb88`, order `bb3ef33e`, delivery `12a84eb0`, payment key `2f10dd12`.*
