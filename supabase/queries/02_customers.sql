-- ============================================================================
-- 02_customers.sql -- profile + addresses + default (USAGE-GROUPED library)
-- ============================================================================
-- Truth   : supabase/migrations/0002_customer.sql
--           (customer_profiles, addresses; hometown FK attached via 0003/0009)
-- Role    : service_role (Spring Boot only; copy into customer repository).
-- Clients : C (address frozen into orders.delivery_address_snapshot at purchase)
-- Guards  : uq_addresses_single_default (partial UNIQUE -- one default);
--           Q3 clears + sets default in ONE TX (concurrent writers serialize).
-- Sections: Q1..Q3 in execution order. Each body preserved VERBATIM.
-- History : consolidated 2026-10-03 from queries/customers/
--           01_get_customer_profile.sql + 02_list_customer_addresses.sql +
--           03_update_default_address.sql (deleted).
-- See     : supabase/queries/README.md, docs/database/schema.md Query-paths.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Q1: queries/customers/01_get_customer_profile.sql -- Usage: customer profile by platform user (0..1 rows, with hometown region)
-- (body below preserved VERBATIM from supabase/queries/customers/01_get_customer_profile.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_get_customer_profile.sql — customer profile by platform user
-- ============================================================================
-- Truth   : supabase/migrations/0002_customer.sql (customer_profiles)
--           hometown FK (fk_customer_hometown_region) attached via 0003/0009
-- Role    : service_role (Spring Boot only)
-- Clients : C
-- Params  : $1 :: uuid — platform_users.id
-- ============================================================================

SELECT cp.*,
       hr.code AS hometown_region_code,
       hr.name AS hometown_region_name
  FROM public.customer_profiles cp
  LEFT JOIN public.regions hr
    ON hr.id = cp.hometown_region_id
 WHERE cp.user_id = $1;
-- expect: 0..1 rows (user_id UNIQUE; 0 when profile not yet created).

-- ----------------------------------------------------------------------------
-- Q2: queries/customers/02_list_customer_addresses.sql -- Usage: active address book, default first (place-order requires >=1 row)
-- (body below preserved VERBATIM from supabase/queries/customers/02_list_customer_addresses.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 02_list_customer_addresses.sql — active address book, default first
-- ============================================================================
-- Truth   : supabase/migrations/0002_customer.sql (addresses)
--           Guard: uq_addresses_single_default (partial unique, one default)
-- Role    : service_role (Spring Boot only)
-- Clients : C — frozen into orders.delivery_address_snapshot at purchase
-- Params  : $1 :: uuid — customer_profiles.id
-- ============================================================================

SELECT *
  FROM public.addresses
 WHERE customer_id = $1
   AND is_active
 ORDER BY is_default DESC, created_at DESC;
-- expect: default address first; ≥1 row required before place-order.

-- ----------------------------------------------------------------------------
-- Q3: queries/customers/03_update_default_address.sql -- Usage: switch default address in ONE TX (single-default guarded)
-- (body below preserved VERBATIM from supabase/queries/customers/03_update_default_address.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 03_update_default_address.sql — switch default address (single-default guarded)
-- ============================================================================
-- Truth   : supabase/migrations/0002_customer.sql
--           Guard: uq_addresses_single_default (partial UNIQUE WHERE is_default)
-- Role    : service_role (Spring Boot only; ownership checked in API layer:
--           address.customer_id must belong to the caller)
-- Clients : C
-- Params  : $1 :: uuid — customer_profiles.id (owner)
--           $2 :: uuid — addresses.id (new default; must belong to $1)
-- Effect  : clears old default, sets new default — ONE TX (concurrent writers
--           serialize on the partial-unique guard, never two defaults)
-- ============================================================================

BEGIN;

UPDATE public.addresses
   SET is_default = false,
       updated_at = now()
 WHERE customer_id = $1
   AND is_default = true;

UPDATE public.addresses
   SET is_default = true,
       updated_at = now()
 WHERE id = $2
   AND customer_id = $1
   AND is_active;

COMMIT;
-- expect: exactly 1 row has is_default=true for the customer afterwards.
-- 0 rows updated on the second statement → wrong id / not owner → ROLLBACK.
