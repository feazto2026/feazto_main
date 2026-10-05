-- ============================================================================
-- sync-platform-user.sql — provision platform identity rows for new auth users
-- ============================================================================
-- Purpose : keep Supabase Auth (auth.users) and the platform's business identity
--           (public.platform_users + public.user_roles JOIN public.roles) in sync
--           with minimal logic. All role GRANTS beyond CUSTOMER happen in Spring
--           Boot approval flows (VENDOR_APPROVE / RIDER_APPROVE + audit + reason).
--
-- Apply   : Supabase SQL editor (or migration). Idempotent: safe to re-run.
-- Tables  : public.platform_users(id, auth_user_id UNIQUE, phone, email,
--             account_status, ...) — see supabase/migrations/0001_identity.sql
--           public.user_roles(user_id, role_id) JOIN public.roles(code)
--             -- server-managed only, never from client input or JWT claims
-- RLS     : revoke client writes; backend uses the service-role key (bypasses RLS).
-- Backend : SupabasePlatformUserService loads by auth_user_id (= JWT sub) with
--           ACTIVE-status check + roles join on EVERY request (fail closed).
-- ============================================================================

-- 1. Platform tables (create if the DB track has not landed them yet) --------
-- NOTE: canonical names are platform_users / roles / user_roles (0001_identity).
-- Older drafts used public.users(supabase_sub); that table is NOT used — the
-- backend queries platform_users.auth_user_id. If a legacy public.users table
-- exists from an early draft, leave it untouched (no migration of PII here).

-- Extensions required for gen_random_uuid() + citext email (no-op on Supabase).
create extension if not exists "pgcrypto";
create extension if not exists "citext";

-- Canonical updated_at helper (same body as 0001 set_updated_at; kept in sync).
create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end $$;

create table if not exists public.roles (
  id          uuid primary key default gen_random_uuid(),
  code        text not null unique check (code = upper(code)),
  name        text not null,
  description text not null default '',
  is_system   boolean not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

insert into public.roles (code, name, description, is_system) values
  ('CUSTOMER',      'Customer',        'Purchases home-made food',              true),
  ('VENDOR',        'Vendor/Home Cook','Prepares and sells food',               true),
  ('RIDER',         'Rider',           'Delivery partner',                      true),
  ('ADMIN',         'Admin',           'Legacy generic admin (avoid for new grants)', true),
  ('SUPER_ADMIN',   'Super Admin',     'Full platform control',                 true),
  ('OPS_ADMIN',     'Operations Admin','Marketplace + fulfillment operations', true),
  ('SUPPORT_ADMIN', 'Support Admin',   'Customer/vendor/rider support',         true),
  ('FINANCE_ADMIN', 'Finance Admin',   'Payments, refunds, payouts',            true)
on conflict (code) do update
  set name = excluded.name,
      description = excluded.description,
      updated_at = now();

create table if not exists public.platform_users (
  id                uuid primary key default gen_random_uuid(),
  auth_user_id      uuid unique,
  phone             text unique,
  email             citext unique,
  display_name      text not null default '',
  primary_role_id   uuid references public.roles (id) on delete restrict,
  account_status    text not null default 'ACTIVE'
    check (account_status in (
      'PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED'
    )),
  is_phone_verified boolean not null default false,
  last_login_at     timestamptz,
  metadata          jsonb not null default '{}'::jsonb,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now(),
  deleted_at        timestamptz,
  constraint platform_users_contact_check
    check (phone is not null or email is not null or auth_user_id is not null)
);

create table if not exists public.user_roles (
  user_id     uuid not null references public.platform_users (id) on delete cascade,
  role_id     uuid not null references public.roles (id) on delete restrict,
  granted_by  uuid references public.platform_users (id) on delete set null,
  granted_at  timestamptz not null default now(),
  primary key (user_id, role_id)
);

-- 2. Lock down client access: backend service-role bypasses RLS; clients get nothing
alter table public.platform_users enable row level security;
alter table public.user_roles      enable row level security;
alter table public.roles           enable row level security;

-- No permissive policies created on purpose: with RLS enabled and zero policies,
-- anon/authenticated client keys cannot read or write these tables at all.
-- Spring Boot uses SERVICE_ROLE_KEY (bypasses RLS) for all access.
-- (0008_indexes_rls.sql installs explicit DENY-ALL policies; either posture denies.)

-- 3. Provisioning function + trigger on auth.users ---------------------------
-- Minimal insert-only logic: one platform row keyed by auth_user_id + default
-- CUSTOMER grant. No business logic, no role elevation, no PII beyond phone/email.
create or replace function public.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid;
  v_phone   text;
  v_email   text;
  v_cust_role_id uuid;
begin
  -- Phone may live on auth.users.phone (phone provider) or in user metadata.
  v_phone := nullif(coalesce(new.phone, (new.raw_user_meta_data ->> 'phone')), '');
  v_email := nullif(new.email, '');

  insert into public.platform_users (auth_user_id, phone, email, account_status)
  values (new.id, v_phone, v_email::citext, 'ACTIVE')
  on conflict (auth_user_id) do update
    set phone = coalesce(excluded.phone, public.platform_users.phone),
        email = coalesce(excluded.email, public.platform_users.email),
        updated_at = now()
  returning id into v_user_id;

  -- Default role only. Vendor/Rider/Admin grants are backend approval actions
  -- (AdminService / approval workflows with VENDOR_APPROVE / RIDER_APPROVE + audit).
  select id into v_cust_role_id from public.roles where code = 'CUSTOMER';
  if v_cust_role_id is not null then
    insert into public.user_roles (user_id, role_id)
    values (v_user_id, v_cust_role_id)
    on conflict do nothing;
  end if;

  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_auth_user();

-- 4. updated_at maintenance ---------------------------------------------------
-- Reuse the canonical set_updated_at() (defined above + in 0001) so only one
-- function body exists. Drop the legacy touch_updated_at path if present.
drop trigger if exists platform_users_touch_updated_at on public.platform_users;
drop function if exists public.touch_updated_at();

drop trigger if exists trg_platform_users_updated_at on public.platform_users;

create trigger trg_platform_users_updated_at
  before update on public.platform_users
  for each row execute function public.set_updated_at();
