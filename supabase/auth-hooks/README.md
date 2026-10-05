# Supabase Auth hooks & platform sync

Supabase is the **identity provider** (OTP issuance, session JWTs). Our Postgres
(`auth.users` → `platform_users`) and Spring Boot own **authorization**.

Source of truth for identity tables: `supabase/migrations/0001_identity.sql`
(`platform_users`, `roles`, `user_roles`, `permissions`, `role_permissions`).
Do **not** create legacy `public.users` / `user_roles(role TEXT)` tables — the
hook in this directory targets `platform_users(auth_user_id)` + `roles(code)`.

## Recommended hooks

### 1. `auth.users` → `platform_users` provisioning (database webhook / trigger)

On every new `auth.users` row, upsert the platform row keyed by `auth_user_id`
(UUID, UNIQUE). Default role is `CUSTOMER` (looked up from `roles.code`);
vendor/rider roles are granted only by the approval workflow — never by client
input. See `sync-platform-user.sql` in this directory.

Options (pick one):

- **A. Postgres trigger** (`supabase/auth-hooks/sync-platform-user.sql`) — simplest,
  runs inside Supabase; keep it minimal (insert-only, no business logic).
- **B. Supabase Auth Hook (HTTP) → Spring `POST /api/v1/internal/auth-events`**
  — preferred once the backend exists, because role/profile creation rules stay
  in Spring Boot. Protect with a shared hook secret header.

Apply order: `0001..0009` migrations first, then this hook. The hook raises a
clear error if `platform_users` is missing.

### 2. Before-user-created / MFA hooks

- Leave Supabase's OTP/SMS config as the enforcement point for rate limits at
  the provider level; our Redis `otp:*` throttles (see `docs/architecture/auth.md`)
  sit in front at the Spring layer for platform-wide abuse caps.
- Do **not** put menu/order/payment logic in Edge Functions — Spring Boot is the
  business authority (MASTER §3.1). Edge Functions may only do auth-adjacent
  glue (e.g. attaching `user_id` claims) if needed.

## RLS posture (defence in depth)

Clients use the **anon key** and are subject to RLS, but RLS is *not* the
business authorization layer — every privileged mutation must still go through
Spring Boot (`Database is not the API`, MASTER §3.2, ADR-010):

- Business tables (all 56): **deny-all for `anon`/`authenticated`**
  (`deny_all_client_access`, 0008/0009); backend reads/writes with `service_role`.
- `user_roles` / `role_permissions`: readable only by the backend service role;
  never by anon/authenticated (prevents privilege self-inspection/escalation probing).
- `platform_users.phone` / `.email`: restricted; clients see masked values via
  API responses (`CUSTOMER_VIEW` scope).
- Storage: public buckets only for menu/vendor display images; KYC/rider
  docs/PoD in **private buckets**, accessed via signed URLs minted by the
  backend after authz (see `supabase/config/storage.md`).

## Secret handling

| Secret | Lives in | Ships to clients? |
|---|---|---|
| `SUPABASE_URL`, `ANON_KEY` | mobile `env.ts`, Admin Web | yes (public) |
| `SERVICE_ROLE_KEY` | Spring Boot env only | **never** |
| Auth-hook shared secret | Supabase hook config + Spring env | **never** |
| Payment webhook signing secret | Spring env only | **never** |

## Local verification

```sql
-- after applying sync-platform-user.sql, in Supabase SQL editor:
-- 1. create a test user via Authentication panel,
-- 2. confirm:
select id, auth_user_id, phone, email, display_name, account_status
from public.platform_users order by created_at desc limit 5;
select ur.user_id, r.code as role
from public.user_roles ur join public.roles r on r.id = ur.role_id
where ur.user_id = '<new-platform-user-id>';
-- expect: one row, role = CUSTOMER
```
