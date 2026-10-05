-- ============================================================================
-- 03_marketplace.sql -- vendors + verification + menus + availability + slots
--                     + zones/serviceability (USAGE-GROUPED library)
-- ============================================================================
-- Truth   : supabase/migrations/0003_marketplace.sql (vendors, verifications,
--           documents, menus/categories/items, slots, vendor_slots,
--           service_zones, vendor_service_zones, menu_item_availability)
--           + 0008_indexes_rls.sql (trigram ix_menu_items_name_trgm, GIN tags/PINs)
-- Role    : service_role (Spring Boot only; copy into marketplace repositories).
-- Clients : C, V, A (Q2 admin queue; live prices here are DISPLAY -- orders
--           freeze them into *_snapshot; see 05_orders.sql Q1)
-- Guards  : uq_vendor_verifications_single_open; UNIQUE(menu_item,slot,date)
--           + reserved<=available CHECK; UNIQUE(vendor,slot) + capacity CHECK;
--           slots.code UNIQUE. Q8 is canonical; TX wrapper composes Q8+Q7+Q5:
--           functions/order_lifecycle.sql S3.
-- Sections: Q1..Q8 (vendors -> verification -> menus -> search -> availability
--           -> slots -> capacity -> serviceability). Bodies VERBATIM.
-- History : consolidated 2026-10-03 from queries/marketplace/vendors/01,02 +
--           menus/01,02,03 + slots/01,02 + zones/01 (deleted).
-- See     : supabase/queries/README.md, docs/database/schema.md Query-paths.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Q1: queries/marketplace/vendors/01_list_vendors.sql -- Usage: approved + active vendor discovery by city (paged)
-- (body below preserved VERBATIM from supabase/queries/marketplace/vendors/01_list_vendors.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_list_vendors.sql — approved, active vendor discovery
-- ============================================================================
-- Truth   : supabase/migrations/0003_marketplace.sql (vendors)
--           Hot index: ix_vendors_status_active
-- Role    : service_role (Spring Boot only)
-- Clients : C, V, A — discovery filters by region/cuisine codes, never
--           hard-coded city names (regions/cuisines taxonomy cached separately)
-- Params  : $1 :: text — city filter (vendors.city)
--           $2 :: int  — LIMIT (page size)
--           $3 :: int  — OFFSET
-- ============================================================================

SELECT *
  FROM public.vendors
 WHERE status = 'APPROVED'
   AND is_active
   AND deleted_at IS NULL
   AND city = $1
 ORDER BY accepts_subscriptions DESC, created_at DESC
 LIMIT $2 OFFSET $3;
-- expect: only APPROVED + active rows; index ix_vendors_status_active serves it.

-- ----------------------------------------------------------------------------
-- Q2: queries/marketplace/vendors/02_list_vendor_verification_queue.sql -- Usage: pending KYC review queue, oldest first (admin)
-- (body below preserved VERBATIM from supabase/queries/marketplace/vendors/02_list_vendor_verification_queue.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 02_list_vendor_verification_queue.sql — pending vendor KYC review queue (oldest first)
-- ============================================================================
-- Truth   : supabase/migrations/0003_marketplace.sql
--           (vendor_verifications, vendor_documents)
--           Guard: uq_vendor_verifications_single_open (one open req per type)
-- Role    : service_role (OPS/SUPPORT admin API only)
-- Clients : V (status view), A (review queue)
-- Params  : $1 :: int — LIMIT · $2 :: int — OFFSET
-- Docs    : objects via signed URLs from the `vendor-documents` bucket
--           (see supabase/config/storage.md); never public URLs for KYC.
-- ============================================================================

SELECT vv.*,
       v.display_name  AS vendor_name,
       v.city          AS vendor_city,
       (SELECT count(*)
          FROM public.vendor_documents vd
         WHERE vd.verification_id = vv.id) AS document_count
  FROM public.vendor_verifications vv
  JOIN public.vendors v ON v.id = vv.vendor_id
 WHERE vv.status = 'PENDING'
 ORDER BY vv.created_at ASC
 LIMIT $1 OFFSET $2;
-- expect: oldest PENDING first; approvals happen in Spring (grant flow in
--   queries/auth/02_grant_role_with_approval.sql + audit), never by client UPDATE.

-- ----------------------------------------------------------------------------
-- Q3: queries/marketplace/menus/01_get_vendor_menu.sql -- Usage: vendor catalogue (active items + categories)
-- (body below preserved VERBATIM from supabase/queries/marketplace/menus/01_get_vendor_menu.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_get_vendor_menu.sql — vendor catalogue (active items, active categories)
-- ============================================================================
-- Truth   : supabase/migrations/0003_marketplace.sql
--           (menus, menu_categories, menu_items)
--           Hot index: ix_menu_items_vendor_avail
-- Role    : service_role (Spring Boot only)
-- Clients : C, V — prices here are LIVE display; order creation freezes them
--           into order_items.*_snapshot (see commerce/orders/01_create_order.sql)
-- Params  : $1 :: uuid — vendors.id
-- Soft-delete: menu_items.deleted_at IS NULL rows only (history survives edits)
-- ============================================================================

SELECT mi.*,
       mc.name AS category_name,
       mc.sort_order AS category_sort
  FROM public.menu_items mi
  JOIN public.menus m
    ON m.id = mi.menu_id
  LEFT JOIN public.menu_categories mc
    ON mc.id = mi.category_id
 WHERE m.vendor_id = $1
   AND m.is_active
   AND mi.is_active
   AND mi.deleted_at IS NULL
 ORDER BY mc.sort_order NULLS LAST, mi.sort_order, mi.name;
-- expect: catalogue rows only; images served from `menu-images/{vendor}/{item}.jpg`.

-- ----------------------------------------------------------------------------
-- Q4: queries/marketplace/menus/02_search_menu_items.sql -- Usage: trigram dish search across approved vendors
-- (body below preserved VERBATIM from supabase/queries/marketplace/menus/02_search_menu_items.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 02_search_menu_items.sql — trigram dish search (vendor/item names)
-- ============================================================================
-- Truth   : supabase/migrations/0003_marketplace.sql (menu_items)
--           + supabase/migrations/0008_indexes_rls.sql
--           (ix_menu_items_name_trgm trigram, GIN tag index)
-- Role    : service_role (Spring Boot only)
-- Clients : C, A
-- Params  : $1 :: text — search term (similarity-ranked)
--           $2 :: int  — LIMIT (default 20)
-- ============================================================================

SELECT mi.*,
       v.display_name AS vendor_name,
       similarity(mi.name, $1) AS rank
  FROM public.menu_items mi
  JOIN public.menus m ON m.id = mi.menu_id
  JOIN public.vendors v ON v.id = m.vendor_id
 WHERE mi.is_active
   AND mi.deleted_at IS NULL
   AND v.status = 'APPROVED'
   AND v.is_active
   AND (mi.name % $1               -- trigram similarity (pg_trgm)
        OR mi.name ILIKE '%' || $1 || '%')
 ORDER BY rank DESC, mi.name
 LIMIT $2;
-- expect: best matches first; empty set (not error) when nothing is similar.

-- ----------------------------------------------------------------------------
-- Q5: queries/marketplace/menus/03_get_item_availability.sql -- Usage: per-day item quantities (dated row wins, else recurring fallback)
-- (body below preserved VERBATIM from supabase/queries/marketplace/menus/03_get_item_availability.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 03_get_item_availability.sql — per-day item quantities (dated row wins, else recurring)
-- ============================================================================
-- Truth   : supabase/migrations/0003_marketplace.sql (menu_item_availability)
--           Guard: UNIQUE(menu_item_id, vendor_slot_id, service_date)
--           CHECK: quantity_reserved <= quantity_available
-- Role    : service_role (Spring Boot only)
-- Clients : C, V — hold flow: Redis lock:slot:* + this DB check at order time
-- Params  : $1 :: uuid — menu_items.id
--           $2 :: date — service_date
-- ============================================================================

SELECT *
  FROM public.menu_item_availability
 WHERE menu_item_id = $1
   AND (service_date = $2 OR service_date IS NULL)
 ORDER BY service_date NULLS LAST;
-- expect: dated row (service_date=$2) takes precedence when present;
--   NULL-service_date row is the recurring fallback.
-- Remaining = quantity_available - quantity_reserved (CHECK keeps it >= 0).

-- ----------------------------------------------------------------------------
-- Q6: queries/marketplace/slots/01_list_slots.sql -- Usage: platform slot templates (cacheable)
-- (body below preserved VERBATIM from supabase/queries/marketplace/slots/01_list_slots.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_list_slots.sql — platform slot templates (Breakfast/Lunch/…)
-- ============================================================================
-- Truth   : supabase/migrations/0003_marketplace.sql (slots)
--           Guard: code UNIQUE; starts_at < ends_at
-- Role    : service_role (Spring Boot only; cacheable — templates rarely change)
-- Clients : C, V
-- Params  : none
-- ============================================================================

SELECT *
  FROM public.slots
 WHERE is_active
 ORDER BY starts_at;
-- expect: 4 rows in demo seed (Breakfast/Lunch/Snacks/Dinner pattern).

-- ----------------------------------------------------------------------------
-- Q7: queries/marketplace/slots/02_check_slot_capacity.sql -- Usage: remaining vendor-slot capacity (DB authoritative)
-- (body below preserved VERBATIM from supabase/queries/marketplace/slots/02_check_slot_capacity.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 02_check_slot_capacity.sql — remaining vendor-slot capacity (DB authoritative)
-- ============================================================================
-- Truth   : supabase/migrations/0003_marketplace.sql (vendor_slots)
--           Guard: UNIQUE(vendor_id, slot_id); vendor_slots_capacity_check
--           (capacity_reserved <= capacity_total); ix_vendor_slots_vendor
-- Role    : service_role (Spring Boot only)
-- Clients : C, V — capacity holds via Redis lock:slot:* + this DB check at
--           order creation; increments happen in the place-order TX
-- Params  : $1 :: uuid — vendors.id · $2 :: uuid — slots.id
-- ============================================================================

SELECT vendor_id,
       slot_id,
       capacity_total,
       capacity_reserved,
       (capacity_total - capacity_reserved) AS remaining,
       service_days
  FROM public.vendor_slots
 WHERE vendor_id = $1
   AND slot_id = $2;
-- expect: 1 row; remaining > 0 required to accept the order line.
-- 0 rows → vendor does not serve this slot → reject with 422.

-- ----------------------------------------------------------------------------
-- Q8: queries/marketplace/zones/01_check_zone_serviceability.sql -- Usage: PIN/zone serviceability per vendor (canonical; wrapper: functions/order_lifecycle.sql S3)
-- (body below preserved VERBATIM from supabase/queries/marketplace/zones/01_check_zone_serviceability.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_check_zone_serviceability.sql — does a vendor serve the customer's PIN/zone?
-- ============================================================================
-- Truth   : supabase/migrations/0003_marketplace.sql
--           (service_zones, vendor_service_zones)
--           Hot indexes: ix_service_zones_city, GIN ix_service_zones_pins
-- Role    : service_role (Spring Boot only)
-- Clients : C, V, A — run BEFORE cart creation / place-order
-- Params  : $1 :: uuid — vendors.id
--           $2 :: text — customer PIN code (matched against postal_codes @>)
-- TX wrapper: supabase/functions/check_serviceability_transaction.sql (step 2 of
--           the 4-step pre-write check: vendor → PIN → slot → availability).
--           This file is the canonical single-statement PIN/zone source.
-- ============================================================================

SELECT sz.*,
       vsz.delivery_fee_override_paise
  FROM public.service_zones sz
  JOIN public.vendor_service_zones vsz
    ON vsz.service_zone_id = sz.id
 WHERE vsz.vendor_id = $1
   AND sz.is_active
   AND sz.postal_codes @> ARRAY[$2];
-- expect: ≥1 row = serviceable (fee override wins when set);
--   0 rows = NOT serviceable → reject with 422 before any write.
