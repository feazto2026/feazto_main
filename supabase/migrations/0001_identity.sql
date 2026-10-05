-- ============================================================================
-- Codewild Food Platform — Migration 0001: Identity & Access
-- ============================================================================
-- Domain owner: IDENTITY (Auth / User / Roles / Permissions)
-- Principles (MASTER_PROMPT_ENHANCED.md §3, §10, §21, §28):
--   * UUID PKs, FKs, created_at/updated_at on every mutable entity
--   * Supabase Auth is the authentication mechanism; platform_users is the
--     application-level profile. Backend resolves:
--       auth_user_id -> platform_user_id -> roles + permissions + status.
--   * A valid Supabase JWT alone NEVER implies Vendor/Rider approval.
--   * No FK is placed on auth.users so this migration runs on any
--     Postgres/Supabase project (auth schema may be managed separately).
--     The backend links platform_users.auth_user_id to auth.users.id.
-- ============================================================================

-- Extensions ---------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "citext";

-- Shared updated_at trigger ------------------------------------------------
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

-- Roles --------------------------------------------------------------------
-- System roles from the master spec (§7 User types):
-- CUSTOMER, VENDOR, RIDER, ADMIN, SUPER_ADMIN, OPS_ADMIN, SUPPORT_ADMIN,
-- FINANCE_ADMIN. Roles are data (not enum) so Ops can evolve them.
CREATE TABLE IF NOT EXISTS public.roles (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  code        text        NOT NULL UNIQUE
              CHECK (code = upper(code)),
  name        text        NOT NULL,
  description text        NOT NULL DEFAULT '',
  is_system   boolean     NOT NULL DEFAULT true,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);

-- Permissions ---------------------------------------------------------------
-- Fine-grained scopes (§21), e.g. VENDOR_APPROVE instead of ADMIN=everything.
CREATE TABLE IF NOT EXISTS public.permissions (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  code        text        NOT NULL UNIQUE
              CHECK (code = upper(code)),
  module      text        NOT NULL DEFAULT 'PLATFORM',
  description text        NOT NULL DEFAULT '',
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.role_permissions (
  role_id     uuid        NOT NULL REFERENCES public.roles (id) ON DELETE CASCADE,
  permission_id uuid      NOT NULL REFERENCES public.permissions (id) ON DELETE CASCADE,
  created_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (role_id, permission_id)
);

-- Platform users ------------------------------------------------------------
-- One row per human/operator across all four clients. Auth identity lives in
-- Supabase Auth; this table carries platform status + primary role.
CREATE TABLE IF NOT EXISTS public.platform_users (
  id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  auth_user_id      uuid        UNIQUE,
  phone             text        UNIQUE,
  email             citext      UNIQUE,
  display_name      text        NOT NULL DEFAULT '',
  primary_role_id   uuid        REFERENCES public.roles (id) ON DELETE RESTRICT,
  account_status    text        NOT NULL DEFAULT 'ACTIVE'
                  CHECK (account_status IN (
                    'PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED'
                  )),
  is_phone_verified boolean     NOT NULL DEFAULT false,
  last_login_at     timestamptz,
  metadata          jsonb       NOT NULL DEFAULT '{}'::jsonb,
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now(),
  deleted_at        timestamptz,
  CONSTRAINT platform_users_contact_check
    CHECK (phone IS NOT NULL OR email IS NOT NULL OR auth_user_id IS NOT NULL)
);

-- Users may hold several roles (e.g. SUPPORT_ADMIN + OPS_ADMIN).
CREATE TABLE IF NOT EXISTS public.user_roles (
  user_id     uuid        NOT NULL REFERENCES public.platform_users (id) ON DELETE CASCADE,
  role_id     uuid        NOT NULL REFERENCES public.roles (id) ON DELETE RESTRICT,
  granted_by  uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  granted_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, role_id)
);

DROP TRIGGER IF EXISTS trg_roles_updated_at ON public.roles;
CREATE TRIGGER trg_roles_updated_at
  BEFORE UPDATE ON public.roles
  FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

DROP TRIGGER IF EXISTS trg_permissions_updated_at ON public.permissions;
CREATE TRIGGER trg_permissions_updated_at
  BEFORE UPDATE ON public.permissions
  FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

DROP TRIGGER IF EXISTS trg_platform_users_updated_at ON public.platform_users;
CREATE TRIGGER trg_platform_users_updated_at
  BEFORE UPDATE ON public.platform_users
  FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- Bootstrap roles (idempotent; safe to re-run) ------------------------------
INSERT INTO public.roles (code, name, description, is_system) VALUES
  ('CUSTOMER',      'Customer',       'Purchases home-made food',              true),
  ('VENDOR',        'Vendor/Home Cook','Prepares and sells food',               true),
  ('RIDER',         'Rider',          'Delivery partner',                      true),
  ('ADMIN',         'Admin',          'Legacy generic admin (avoid for new grants)', true),
  ('SUPER_ADMIN',   'Super Admin',    'Full platform control',                 true),
  ('OPS_ADMIN',     'Operations Admin','Marketplace + fulfillment operations', true),
  ('SUPPORT_ADMIN', 'Support Admin',  'Customer/vendor/rider support',         true),
  ('FINANCE_ADMIN', 'Finance Admin',  'Payments, refunds, payouts',            true)
ON CONFLICT (code) DO UPDATE
  SET name = EXCLUDED.name,
      description = EXCLUDED.description,
      updated_at = now();

-- Bootstrap permissions (idempotent) -----------------------------------------
INSERT INTO public.permissions (code, module, description) VALUES
  ('VENDOR_VIEW',      'MARKETPLACE',  'View vendor profiles'),
  ('VENDOR_APPROVE',   'MARKETPLACE',  'Approve / reject / request changes on vendors'),
  ('VENDOR_SUSPEND',   'MARKETPLACE',  'Suspend / reactivate vendors'),
  ('MENU_VIEW',        'MARKETPLACE',  'Inspect menus, items, slots, capacity'),
  ('MENU_MANAGE',      'MARKETPLACE',  'Modify menu on behalf of vendor (audited)'),
  ('CUSTOMER_VIEW',    'CUSTOMER',     'View customer profiles and history'),
  ('CUSTOMER_SUSPEND', 'CUSTOMER',     'Suspend / reactivate customer accounts'),
  ('ORDER_VIEW',       'COMMERCE',     'View orders and timelines'),
  ('ORDER_CANCEL',     'COMMERCE',     'Cancel orders per policy (audited)'),
  ('ORDER_REFUND',     'COMMERCE',     'Issue / approve refunds (audited)'),
  ('SUBSCRIPTION_MANAGE','SUBSCRIPTION','Pause / cancel / fix subscriptions (audited)'),
  ('RIDER_VIEW',       'FULFILLMENT',  'View rider profiles and deliveries'),
  ('RIDER_APPROVE',    'FULFILLMENT',  'Approve rider onboarding'),
  ('RIDER_SUSPEND',    'FULFILLMENT',  'Suspend / reactivate riders'),
  ('DELIVERY_MANAGE',  'FULFILLMENT',  'Reassign / escalate deliveries'),
  ('FINANCE_VIEW',     'FINANCE',      'View payments, commissions, payouts'),
  ('PAYOUT_MANAGE',    'FINANCE',      'Schedule / approve payouts (audited)'),
  ('SUPPORT_VIEW',     'OPERATIONS',   'View support tickets'),
  ('SUPPORT_ASSIGN',   'OPERATIONS',   'Assign / resolve support tickets'),
  ('AUDIT_VIEW',       'OPERATIONS',   'Read audit logs'),
  ('SETTINGS_MANAGE',  'OPERATIONS',   'Change zones, slots, platform settings (audited)')
ON CONFLICT (code) DO UPDATE
  SET module = EXCLUDED.module,
      description = EXCLUDED.description,
      updated_at = now();

-- Default role -> permission mapping (idempotent) ----------------------------
-- SUPER_ADMIN gets everything; other roles get least-privilege scopes.
INSERT INTO public.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM public.roles r
JOIN public.permissions p ON (
  (r.code = 'SUPER_ADMIN')
  OR (r.code = 'OPS_ADMIN' AND p.code IN (
        'VENDOR_VIEW','VENDOR_APPROVE','VENDOR_SUSPEND','MENU_VIEW',
        'ORDER_VIEW','ORDER_CANCEL','SUBSCRIPTION_MANAGE',
        'RIDER_VIEW','RIDER_APPROVE','RIDER_SUSPEND','DELIVERY_MANAGE',
        'CUSTOMER_VIEW','SUPPORT_VIEW','SUPPORT_ASSIGN'))
  OR (r.code = 'FINANCE_ADMIN' AND p.code IN (
        'FINANCE_VIEW','PAYOUT_MANAGE','ORDER_VIEW','ORDER_REFUND','AUDIT_VIEW'))
  OR (r.code = 'SUPPORT_ADMIN' AND p.code IN (
        'SUPPORT_VIEW','SUPPORT_ASSIGN','ORDER_VIEW','CUSTOMER_VIEW',
        'VENDOR_VIEW','RIDER_VIEW'))
  OR (r.code = 'ADMIN' AND p.code IN (
        'VENDOR_VIEW','ORDER_VIEW','RIDER_VIEW','CUSTOMER_VIEW','SUPPORT_VIEW'))
)
ON CONFLICT DO NOTHING;

COMMENT ON TABLE public.platform_users IS
  'Application-level user. Auth handled by Supabase Auth; approval/status enforced server-side by Spring Boot.';
