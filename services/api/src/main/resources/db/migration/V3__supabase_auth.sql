-- V3: Auth hardening (additive only, UUID truth, idempotent).
-- V1 already carries full Supabase parity (platform_users UUID + roles/permissions/
-- user_roles + CUSTOMER backfill). This migration adds ONLY auth-critical hardening
-- that V1 intentionally leaves out:
--   * legacy password_hash column for pre-Supabase rows (nullable; Supabase Auth is source)
--   * auth lookup indexes (sub + status + roles join)
--   * defensive CUSTOMER backfill for rows created between V1 seed and trigger deploy
-- No destructive changes; safe to re-run.

-- 1. Legacy local-auth column (nullable; unused when Supabase Auth is source) ----
ALTER TABLE public.platform_users ADD COLUMN IF NOT EXISTS password_hash text;

-- 2. Auth lookup indexes (fail-closed 401 path must be indexed) ------------------
CREATE INDEX IF NOT EXISTS ix_platform_users_auth_user_id
  ON public.platform_users (auth_user_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_platform_users_account_status
  ON public.platform_users (account_status);
CREATE INDEX IF NOT EXISTS ix_user_roles_user
  ON public.user_roles (user_id);
CREATE INDEX IF NOT EXISTS ix_user_roles_role
  ON public.user_roles (role_id);

-- 3. Defensive CUSTOMER backfill (sync trigger grants it; this covers gaps) ------
INSERT INTO public.user_roles (user_id, role_id)
SELECT u.id, r.id FROM public.platform_users u CROSS JOIN public.roles r
WHERE r.code = 'CUSTOMER'
  AND u.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM public.user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id)
ON CONFLICT DO NOTHING;
