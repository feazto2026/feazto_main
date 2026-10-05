-- ============================================================================
-- 04_cart.sql -- active cart + lines + coupon (USAGE-GROUPED library)
-- ============================================================================
-- Truth   : supabase/migrations/0004_commerce.sql
--           (carts, cart_items, coupons, coupon_usages)
-- Role    : service_role (Spring Boot only; copy into cart repository).
-- Clients : C (single-vendor carts; second vendor starts a new cart)
-- Guards  : uq_carts_single_active (one ACTIVE per customer+vendor);
--           UNIQUE(cart_id, menu_item_id, customization_hash);
--           coupon_usages.order_id UNIQUE (redemption written at place-order).
-- Sections: Q1..Q3 in execution order. Each body preserved VERBATIM.
-- History : consolidated 2026-10-03 from queries/commerce/cart/
--           01_get_active_cart.sql + 02_list_cart_lines.sql +
--           03_validate_coupon_code.sql (deleted).
-- See     : supabase/queries/README.md, docs/database/schema.md Query-paths.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Q1: queries/commerce/cart/01_get_active_cart.sql -- Usage: the single ACTIVE cart per (customer, vendor)
-- (body below preserved VERBATIM from supabase/queries/commerce/cart/01_get_active_cart.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_get_active_cart.sql — the single ACTIVE cart per (customer, vendor)
-- ============================================================================
-- Truth   : supabase/migrations/0004_commerce.sql (carts)
--           Guard: uq_carts_single_active (partial unique, one ACTIVE each)
-- Role    : service_role (Spring Boot only)
-- Clients : C — carts are single-vendor; a second vendor starts a new cart
-- Params  : $1 :: uuid — customer_profiles.id · $2 :: uuid — vendors.id
-- ============================================================================

SELECT *
  FROM public.carts
 WHERE customer_id = $1
   AND vendor_id = $2
   AND status = 'ACTIVE';
-- expect: 0..1 rows (partial unique); 0 → create via INSERT with the same
--   guard (concurrent creates collapse on uq_carts_single_active).

-- ----------------------------------------------------------------------------
-- Q2: queries/commerce/cart/02_list_cart_lines.sql -- Usage: cart lines with live item context (reprice/drop before place-order)
-- (body below preserved VERBATIM from supabase/queries/commerce/cart/02_list_cart_lines.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 02_list_cart_lines.sql — cart lines with live item context
-- ============================================================================
-- Truth   : supabase/migrations/0004_commerce.sql (cart_items)
--           Guard: UNIQUE(cart_id, menu_item_id, customization_hash)
-- Role    : service_role (Spring Boot only)
-- Clients : C — line prices here are a snapshot per line; place-order
--           re-validates against live menu + availability before freezing
-- Params  : $1 :: uuid — carts.id
-- ============================================================================

SELECT ci.*,
       mi.name AS live_item_name,
       mi.price_paise AS live_price_paise,
       mi.is_active AS live_is_active
  FROM public.cart_items ci
  JOIN public.menu_items mi ON mi.id = ci.menu_item_id
 WHERE ci.cart_id = $1
 ORDER BY ci.created_at;
-- expect: n rows; backend drops/reprices lines whose live item changed
--   (is_active=false, deleted, or price drift) before place-order.

-- ----------------------------------------------------------------------------
-- Q3: queries/commerce/cart/03_validate_coupon_code.sql -- Usage: coupon eligibility check (window + caps; math stays server-side)
-- (body below preserved VERBATIM from supabase/queries/commerce/cart/03_validate_coupon_code.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 03_validate_coupon_code.sql — coupon eligibility check (validity window + caps)
-- ============================================================================
-- Truth   : supabase/migrations/0004_commerce.sql (coupons, coupon_usages)
--           Hot index: ix_coupons_validity · Guard: coupon_usages.order_id
--           UNIQUE (one redemption row per order)
-- Role    : service_role (Spring Boot only)
-- Clients : C — amount math stays server-side (PERCENT caps, min-order, vendor
--           scope); redemption is written at place-order, never by the client
-- Params  : $1 :: text — coupons.code (e.g. 'WELCOME50')
-- ============================================================================

SELECT c.*,
       (SELECT count(*) FROM public.coupon_usages cu
         WHERE cu.coupon_id = c.id) AS times_used
  FROM public.coupons c
 WHERE c.code = $1
   AND c.is_active
   AND (c.valid_from IS NULL OR c.valid_from <= now())
   AND (c.valid_until IS NULL OR c.valid_until >= now());
-- expect: 1 row = usable (backend still checks min_order_value_paise,
--   usage_limit, per-user limit, vendor scope); 0 rows = invalid/expired.
