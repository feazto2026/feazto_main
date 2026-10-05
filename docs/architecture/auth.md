# Authentication & Authorization

> Source of truth for auth decisions. Backend (`Spring Boot`) is authoritative;
> mobile apps and Admin Web are untrusted presentation layers (MASTER §28–§29).

## 1. Trust chain

```text
Supabase Auth (identity: proves "who" via OTP)
  → JWT (access token, short-lived ~1h)
    → Spring Security (JwtAuthenticationFilter + SupabaseJwtValidator + JWKS)
      → PlatformUser (roles/permissions loaded from OUR Postgres, never from client)
        → Role + Permission + Ownership + State (PermissionEvaluator + ApprovalGate)
```

- **Supabase Auth** owns credentials/OTP/session issuance only.
- **Spring Boot** owns every business authorization decision.
- **PostgreSQL `users` / `user_roles` / profile tables** own role truth.
  The JWT `role` claim is informational and MUST NOT be trusted.
- Frontend role checks are **UX-only**.

## 2. OTP flow (phone login)

Backend-owned throttling + Supabase Auth sessions; Redis holds only hashes.

```text
Client                      Spring API (/api/v1/auth/otp/*)              Redis / Supabase
  │ request(phone) ─────────▶ normalise E.164, resend cooldown? ───────▶│
  │                           hourly caps (5/phone, 20/IP)             │
  │                           CSPRNG 6-digit → SHA-256(code+OTP_SECRET) │
  │                           SET otp:{phone}=hash:0 EX 300s           │
  │                           SET otp:resend:{phone}=1 EX 30s          │
  │                           INCR otp:req:{phone} / otp:req:ip:{ip}   │
  │                           ─── SMS provider ───────────────────────▶│ SMS
  │ ◀── generic "if valid, code sent" (no enumeration) ─────────────────│
  │ verify(phone, code) ───▶ GET otp:{phone} (miss → 410 OTP_EXPIRED) │
  │                           attempts ≥5 → void → 429                 │
  │                           SHA-256(candidate+secret) constant-time  │
  │                           compare; wrong → attempts+1 (429 at 5)   │
  │                           success → DEL otp:{phone} (single-use)   │
  │                           ensure platform row (by phone) + CUSTOMER│
  │ ◀── platform identity {userId, roles, phoneMasked} ─────────────── │
  │     (Supabase session JWTs are minted by Supabase Auth; the client│
  │      presents the Supabase access JWT on later calls)             │
```

### Rules

| Concern | Rule |
|---|---|
| Code | 6 numeric digits from `SecureRandom`; store **SHA-256(code + OTP_SECRET) only**, never plaintext; constant-time compare |
| TTL / expiry | `otp:{phone}` = `hash:attempts` 300 s; missing/corrupt/expired fails closed as `410 OTP_EXPIRED` (covers Redis misses) |
| Attempt limits | max **5** verifications per code, then void (`429 OTP_ATTEMPTS_EXCEEDED`); counter preserved without extending TTL |
| Resend | 30 s cooldown (`otp:resend:{phone}` → `429 OTP_RESEND_TOO_SOON`); max **5 sends/phone/hour**, **20/IP/hour** (`429 RATE_LIMITED`, SMS-pumping defence); client IP from `X-Forwarded-For` → `RemoteAddr` |
| Single-use | success deletes `otp:{phone}` before any further I/O; replay yields `OTP_EXPIRED` |
| Enumeration | `request`/`resend` always return the same generic message whether or not the phone exists (`/otp/send` is a legacy alias of `/otp/request`) |
| Transport | OTP `request`/`verify`/`resend` (+ legacy `send` alias) are the only **public** auth routes + rate-limited; `/me` requires JWT; all others need JWT |
| Fail-closed | Redis unavailable on send → `500 INTERNAL` (no code minted); on verify → `OTP_EXPIRED` (never authenticate) |
| Logging | never log codes, hashes, tokens, or full phones — masked form (`•••• •••• 3210`) only |
| Lockout signal | repeated violations → suspicious-activity audit event, optional CAPTCHA/step-up later |

Redis key summary: `otp:{e164}` (`hash:attempts`, 300 s) · `otp:resend:{e164}` (30 s)
· `otp:req:{e164}` (1 h, 5/hr) · `otp:req:ip:{ip}` (1 h, 20/hr). See `OtpSecurityPolicy.java`
(`hash()`, `constantTimeEquals()`, `otpKey()`/`resendKey()`/`phoneCounterKey()`/`ipCounterKey()`)
enforced by `AuthService` (`SecureRandom`, `requestOtp`/`resendOtp`/`verifyOtp`).

> Current RN prototypes accept a hard-coded `1234` OTP locally (`authStore.ts`).
> That is **dev-only** and must be replaced by this server flow before any
> staging build — clients call `packages/mobile-shared` helpers, never local OTP checks.

## 3. Tokens & refresh

| Token | Lifetime | Storage (mobile) | Notes |
|---|---|---|---|
| Supabase access JWT | ~1 h | **memory only** | sent as `Authorization: Bearer` |
| Supabase refresh token | long-lived, rotating | **SecureStore** (expo-secure-store / Keychain+Keystore), never AsyncStorage/logs | single-use rotation; reuse = session theft signal → revoke |
| Backend | stateless | no server sessions | every request re-validates JWT via JWKS |

Refresh logic lives in `packages/mobile-shared/auth-refresh.ts`:

```text
401 TOKEN_EXPIRED + refresh token present
  → single-flight refresh (one network call even under concurrency)
  → retry original request once with the new access token
  → refresh failure → clear tokens → route to login
```

Additional rules:

- Access token kept in memory; rehydrated via refresh on cold start — never
  persisted to disk.
- `Idempotency-Key` (uuid v4 per logical operation) is attached by `api-client.ts`
  on mutating calls so refresh-retries never double-charge/double-order.
- Admin Web: same flow with `httpOnly` considerations per web storage guidance;
  refresh tokens never in URL/localStorage logs.
- Clock skew tolerance on the backend: 60 s for `exp`/`nbf`.

## 4. Backend validation (JWKS)

`SupabaseJwtValidator` + `SupabaseJwksSignatureVerifier` (Nimbus) + `JwtAuthenticationFilter`:

1. Extract `Bearer` token (missing → `401 UNAUTHENTICATED`).
2. Verify JWS signature against Supabase JWKS
   (`${SUPABASE_URL}/auth/v1/.well-known/jwks.json`, raw document cached 10 min
   via Caffeine, single refresh on unknown/missing `kid` then fail closed).
   Nimbus: `JWKSet.parse` → `getKeyByKeyId(kid)` → strict `kid + alg` allowlist
   (`RS256`/`ES256` only; `RSAKey`↔`RS256`, `ECKey`↔`ES256`; `oct`/OKP/unknown
   key types rejected) → `SignedJWT.parse(signingInput.signature).verify(...)`.
3. Validate claims: `sub` present, `exp` live, `nbf` respected, `iss` equals
   project issuer, `aud` matches when configured (60 s clock skew on `exp`/`nbf`).
4. Load `PlatformUser` from Postgres by `sub` via `SupabasePlatformUserService`:
   `platform_users` by `auth_user_id` constrained to `ACTIVE` +
   `user_roles JOIN roles` for codes + profile IDs/statuses for gates.
   Unknown/disabled/no-recognised-roles → `401 UNKNOWN_USER` (fail closed, never a guest).
5. Publish `PlatformUser` into `SecurityContext` (`ROLE_*` authorities from
   server-side roles) + `request.platformUser`.

Fail-closed everywhere: any crypto/parse/config error → 401, never a guest principal.
`SecurityConfig` (single chain, no `ConditionalOnProperty` gate) permits **only**
`POST /api/v1/auth/otp/request|send|verify|resend` (+ `POST /auth/refresh` hint),
`POST /api/v1/payments/webhook*` (HMAC-verified, not JWT), actuator health, and docs;
everything else is authenticated. The legacy HS256 chain (`config.SecurityConfig`
+ `JwtAuthFilter` trusting the JWT `roles` claim) has been **deleted** — Supabase
mints tokens and roles come from `user_roles` only.

## 5. Roles, permissions, ownership

Roles: `CUSTOMER · VENDOR · RIDER · ADMIN · SUPER_ADMIN · OPS_ADMIN · FINANCE_ADMIN · SUPPORT_ADMIN`
(see `Role.java`). Scopes include `VENDOR_VIEW / VENDOR_APPROVE / VENDOR_SUSPEND ·
ORDER_VIEW / ORDER_CANCEL / ORDER_REFUND · RIDER_VIEW / RIDER_APPROVE / RIDER_SUSPEND ·
FINANCE_VIEW / PAYOUT_MANAGE · SUPPORT_VIEW / SUPPORT_ASSIGN · AUDIT_VIEW / SETTINGS_MANAGE`
(see `Permission.java`; `SUPER_ADMIN` = all).

Enforcement order per request:

```text
authenticated? → has permission? → owns resource? → resource in valid state?
```

`PermissionEvaluator` (static, unit-tested) encodes ownership:

- `canAccessOrder` — owner, or privileged admin-family role with `ORDER_VIEW`.
- `canVendorAccessOrder` / `canManageVendorMenu` — **own `vendorId` + APPROVED**.
- `canRiderUpdateDelivery` — **assigned `riderId` + APPROVED**.

Use `@PreAuthorize` for coarse gates plus evaluator calls in services for ownership.

## 6. Vendor / rider APPROVED gate

No trading before verification (enforced by `VendorRiderApprovalGate`):

```text
vendor: PENDING → 403 VENDOR_NOT_APPROVED (publish menu, accept order blocked)
        SUSPENDED → 403 VENDOR_SUSPENDED
        REJECTED → 403 VENDOR_NOT_APPROVED
rider:  PENDING → 403 RIDER_NOT_APPROVED (accept/pickup/deliver blocked)
        SUSPENDED → 403 RIDER_SUSPENDED
```

Status lives on `vendor_profiles.status` / `rider_profiles.status`, mutated only
through approval workflows (`VENDOR_APPROVE` / `RIDER_APPROVE` + audit + reason).
Client "approved" flags are ignored.

## 7. Privacy & data minimization

- `PlatformUser` carries IDs + roles + masked phone only. Full PII (addresses,
  documents, payout details) is loaded per-use-case, never embedded in tokens.
- Phone: full E.164 only in memory where needed; logs/errors use masked form.
- JWTs carry `sub` + expiry (+ Supabase defaults) — no addresses, menus, or payouts.
- PII responses are scoped to owner/admin-with-permission; list endpoints paginate
  and redact.
- Audit security events: logins, OTP abuse, approval/suspend with reason + actor,
  payout/refund actions — actor, action, target id, request id; never secrets.
- Secrets: `SUPABASE_SERVICE_ROLE_KEY`, webhook signing secrets, SMS keys are
  server-env-only; mobile ships `SUPABASE_URL` + `ANON_KEY` (see `env.ts`).

## 8. Standard envelopes

Success: `{ success, data, message, requestId }`.
Error: `{ success:false, error:{ code, message, details }, requestId }`
with stable codes: `UNAUTHENTICATED · TOKEN_EXPIRED · TOKEN_INVALID ·
FORBIDDEN · VENDOR_NOT_APPROVED · VENDOR_SUSPENDED · RIDER_NOT_APPROVED ·
RIDER_SUSPENDED · OTP_EXPIRED · OTP_ATTEMPTS_EXCEEDED · OTP_RESEND_TOO_SOON ·
UNKNOWN_USER · AUTH_MISCONFIGURED`.

Every response (including 401/403) carries `X-Request-Id`.

## 9. Backend track — done (AuthAgent)

1. ~~Wire Nimbus signature verification (`SupabaseJwksSignatureVerifier` TODO)~~ —
   DONE: Nimbus `JWKSet.parse` + `kid` lookup + `RS256`/`ES256` allowlist
   (`RSAKey`↔`RS256`, `ECKey`↔`ES256`), 10-min Caffeine cache, single refresh on
   `UnknownKid`, fail-closed otherwise.
2. ~~Back `PlatformUserService` with repositories + `users`/`user_roles` migration~~ —
   DONE: `PlatformUserRepository.findByAuthUserId` / `findActiveByAuthUserId`
   (ACTIVE-only) / `findRoleCodesByUserId` (roles join) + `UserRoleRepository` /
   `RoleRepository`; `@Primary SupabasePlatformUserService` maps entity →
   principal per request with status check (unknown/suspended/no-roles → empty →
   401); Flyway `V3__supabase_auth` (roles/permissions/user_roles + backfill).
3. ~~Add Redis-backed OTP controller + rate-limit filter per this doc~~ —
   DONE: `AuthService.requestOtp`/`resendOtp`/`verifyOtp` enforces `OtpSecurityPolicy`
   (SHA-256(code+OTP_SECRET), 300 s `otp:{phone}=hash:attempts`, 5 attempts then void,
   30 s `otp:resend`, 5/hr per phone + 20/hr per IP via `otp:req*`, `SecureRandom`,
   delete-on-success, fail-closed `OTP_EXPIRED` on Redis miss); `AuthController`
   exposes `POST /otp/request|send|verify|resend` (generic messages, IP-aware) + `GET /me`.
4. HMAC verification on `POST /api/v1/payments/webhook` + idempotency record.
   (Payment track: webhook must verify provider HMAC + use provider event id as
   idempotency key; the route stays public-by-HMAC, never JWT.)
5. ~~Supabase Auth hook → `sync-platform-user.sql` to auto-provision the platform row~~ —
   DONE: trigger targets `platform_users(auth_user_id, phone, email, ACTIVE)` +
   `user_roles` CUSTOMER grant via `roles` join, RLS locked (no client policies),
   idempotent re-runnable.
6. ~~**Consolidate to one `SecurityFilterChain`**~~ — DONE: the sibling
   `config.SecurityConfig` (HS256 shared-secret, trusts JWT `roles` claim) and
   `shared/security/JwtAuthFilter` have been **deleted**; `shared/security/SecurityConfig`
   is now the unconditional single chain (public OTP + webhook + health + docs,
   everything else authenticated). `SecurityUtils` now expects a `PlatformUser`
   principal (never `String`); ownership/permission/state flow through
   `PermissionEvaluator` + `VendorRiderApprovalGate` in services
   (`MarketplaceService`, `OrderService`, `DeliveryAssignmentService`,
   `AdminService` permission gates, `FinanceService` permission gates).
   `shared/security/PlatformUser` (principal) and `identity/domain/PlatformUser`
   (JPA entity) remain intentionally distinct layers — the service maps
   entity → principal per request so role changes apply on the next call.
