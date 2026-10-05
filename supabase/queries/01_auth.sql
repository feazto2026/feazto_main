-- ============================================================================
-- 01_auth.sql -- resolve roles + grant approval (USAGE-GROUPED query library)
-- ============================================================================
-- Truth   : supabase/migrations/0001_identity.sql
--           (platform_users, roles, user_roles, permissions, role_permissions)
--           + supabase/migrations/0007_operations.sql (audit_logs, for Q2)
-- Role    : service_role (Spring Boot only; RLS denies anon/authenticated).
--           Copy the SQL into the auth repository/service -- do not point
--           clients at this file and do not apply it as a migration.
-- Clients : C, V, R, A -- Q1 runs on EVERY request (JWT sub -> identity).
-- Guards  : PK(user_id, role_id) makes Q2 idempotent; Q2 writes grant + audit
--           in ONE TX; Q1 fails closed (0 rows -> 401, non-ACTIVE -> reject).
-- Sections: Q1..Q2 in execution order. Each body preserved VERBATIM.
-- History : consolidated 2026-10-03 from queries/auth/01_resolve_user_roles.sql
--           + queries/auth/02_grant_role_with_approval.sql (deleted). Old
--           in-body cross-references to fragmented paths kept verbatim.
-- See     : supabase/queries/README.md, docs/database/schema.md Query-paths.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Q1: queries/auth/01_resolve_user_roles.sql -- Usage: resolve auth_user_id -> platform user + roles[] + permissions[] (every request, fail closed)
-- (body below preserved VERBATIM from supabase/queries/auth/01_resolve_user_roles.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_resolve_user_roles.sql — auth_user_id → platform user + roles + permissions
-- ============================================================================
-- Truth   : supabase/migrations/0001_identity.sql
--           (platform_users, roles, user_roles, permissions, role_permissions)
-- Role    : service_role (Spring Boot only; RLS denies anon/authenticated)
-- Clients : C, V, R, A — run on EVERY request (JWT sub → platform identity,
--           fail closed on missing row or non-ACTIVE account_status)
-- Params  : $1 :: uuid  — auth_user_id (= Supabase Auth JWT sub)
-- Returns : one row per user: profile + roles[] + permissions[]
-- See     : docs/database/schema.md Query-paths row 1; auth hook provisions the
--           platform_users row (supabase/auth-hooks/sync-platform-user.sql)
-- ============================================================================

SELECT pu.*,
       COALESCE(array_agg(DISTINCT r.code) FILTER (WHERE r.code IS NOT NULL),
              '{}') AS roles,
       COALESCE(array_agg(DISTINCT p.code) FILTER (WHERE p.code IS NOT NULL),
              '{}') AS permissions
  FROM public.platform_users pu
  LEFT JOIN public.user_roles ur
    ON ur.user_id = pu.id
  LEFT JOIN public.roles r
    ON r.id = ur.role_id
  LEFT JOIN public.role_permissions rp
    ON rp.role_id = r.id
  LEFT JOIN public.permissions p
    ON p.id = rp.permission_id
 WHERE pu.auth_user_id = $1
   AND pu.deleted_at IS NULL
 GROUP BY pu.id;
-- expect: 1 row when provisioned (roles contains at least 'CUSTOMER'),
--   0 rows when unknown → backend rejects with 401 (fail closed).
-- Backend must additionally require pu.account_status = 'ACTIVE'.

-- ----------------------------------------------------------------------------
-- Q2: queries/auth/02_grant_role_with_approval.sql -- Usage: approval role grant (VENDOR/RIDER/ADMIN) + audit_logs row in ONE TX
-- (body below preserved VERBATIM from supabase/queries/auth/02_grant_role_with_approval.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 02_grant_role_with_approval.sql — approval role grant (VENDOR/RIDER/ADMIN) + audit
-- ============================================================================
-- Truth   : supabase/migrations/0001_identity.sql (user_roles, roles)
--           + supabase/migrations/0007_operations.sql (audit_logs)
-- Role    : service_role (Spring Boot approval workflow only)
-- Clients : V, R, A — NEVER from client input; requires VENDOR_APPROVE /
--           RIDER_APPROVE (or ADMIN_GRANT) permission + verification outcome
-- Params  : $1 :: uuid  — platform_users.id (grantee)
--           $2 :: text  — roles.code to grant (e.g. 'VENDOR')
--           $3 :: uuid  — platform_users.id (approver, changed_by)
--           $4 :: text  — reason (stored in audit_logs.detail)
-- Effect  : inserts user_roles row (idempotent) + audit_logs row in ONE TX
-- ============================================================================

BEGIN;

-- Resolve the role id from the canonical code (never trust a client-sent id).
-- expect: exactly 1 row; 0 rows → unknown code, ROLLBACK.
SELECT id, code FROM public.roles WHERE code = $2;

-- Idempotent grant (re-approval is a no-op, never duplicates PK).
INSERT INTO public.user_roles (user_id, role_id, granted_by)
SELECT $1, r.id, $3
  FROM public.roles r
 WHERE r.code = $2
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Every privileged write leaves an audit trail (same TX as the grant).
INSERT INTO public.audit_logs
  (actor_id, actor_role, action, entity_type, entity_id, detail)
VALUES
  ($3, 'ADMIN', 'ROLE_GRANTED', 'platform_users', $1,
   jsonb_build_object('granted_role', $2, 'reason', $4));

COMMIT;
-- expect: user_roles gained ≤1 row; audit_logs gained exactly 1 row.
