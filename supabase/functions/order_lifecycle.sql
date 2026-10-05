-- ============================================================================
-- order_lifecycle.sql -- BACKEND TX WRAPPERS: place order + payment webhook +
--                       serviceability (NOT a migration; run from Spring)
-- ============================================================================
-- Role    : service_role, Spring Boot (multi-statement ONE-TX wrappers that
--           compose the canonical single-statement queries -- they never
--           duplicate query SQL, they CALL it).
-- Canonical sources:
--           S1 (place order)  <- queries/05_orders.sql Q1
--           S2 (webhook)      <- queries/06_payments_refunds.sql Q1, Q2
--           S3 (serviceability) <- queries/03_marketplace.sql Q8, Q7, Q5
--           (steps 2-4 compose zone + slot + availability reads; no writes)
-- Sections: S1..S3 below. Bodies preserved VERBATIM (no SQL change).
-- History : consolidated 2026-10-03 from functions/
--           create_order_transaction.sql + verify_payment_webhook_transaction.sql
--           + check_serviceability_transaction.sql (deleted). Old in-body
--           cross-references to fragmented paths kept verbatim.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- S1: functions/create_order_transaction.sql -- Usage: place-order TX wrapper: idempotency claim -> snapshots -> history + outbox -> capacity holds + cart close
-- (body below preserved VERBATIM from supabase/functions/create_order_transaction.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- create_order_transaction.sql — BACKEND FUNCTION PATTERN (not a migration)
-- ============================================================================
-- Flow    : customer places a single-vendor order (cart → frozen snapshots)
-- Truth   : 0002 addresses · 0003 vendors/slots/availability/zones ·
--           0004 carts/cart_items/orders/order_items/history/coupon_usages ·
--           0007 outbox_events/idempotency_keys · 0006 deliveries (created
--           AVAILABLE downstream after payment confirm)
-- Role    : service_role, Spring Boot (OrderService.placeOrder — ONE TX for the
--           DB writes; provider charge + Redis locks surround it)
-- Clients : C (subscription generator reuses this shape — see
--           generate_subscription_order_transaction.sql)
-- Canonical single-statement source: queries/commerce/orders/01_create_order.sql
--   (core order + history + outbox form); this file is the multi-statement TX
--   wrapper (idempotency + snapshots + capacity holds + cart close).
-- Order of operations:
--   A. idempotency_claim (scope 'create_order') — duplicate → return existing
--   B. serviceability_check (vendor/PIN/slot/lines — 422/409 before writes)
--   C. validate_coupon + compute money server-side (totals must satisfy
--      orders_total_consistency_check — never trust client totals)
--   D. INSERT orders (snapshot columns frozen) + order_items (per-line
--      snapshots) + coupon_usages + order_status_history(CREATED) +
--      outbox_events(order.created) — ONE TX (@see
--      ../queries/commerce/orders/01_create_order.sql for the core form)
--   E. bump vendor_slots.capacity_reserved + availability.quantity_reserved
--      (same TX; CHECKs keep them <= totals), mark cart CONVERTED
-- Params  : $1 order_number · $2 idempotency_key · $3 customer_id ·
--           $4 vendor_id · $5 cart_id · $6 address_id · $7 slot_id ·
--           $8 service_date · $9 coupon_code (nullable) · $10 changed_by
-- ============================================================================

BEGIN;

-- A. claim (concurrent double-taps collapse here) ------------------------------
INSERT INTO public.idempotency_keys (scope, key, expires_at)
VALUES ('create_order', $2, now() + interval '24 hours')
ON CONFLICT (scope, key) DO NOTHING;
-- 0 rows → retry: SELECT id, order_number FROM orders WHERE idempotency_key=$2
-- and return it WITHOUT executing below.

-- D1. order header (money pre-computed server-side; total derived in SQL) -----
-- (binds $11..$17 = subtotal, discount, tax, delivery_fee, platform_fee,
--  packaging_fee, commission inputs; coupon/address snapshots bound from the
--  server's validated reads, NOT client JSON)
INSERT INTO public.orders
  (order_number, idempotency_key, customer_id, vendor_id, address_id, slot_id,
   service_date, subtotal_paise, discount_paise, tax_paise,
   delivery_fee_paise, platform_fee_paise, packaging_fee_paise, total_paise,
   commission_bps_snapshot, platform_commission_paise_snapshot,
   vendor_payout_paise_snapshot, coupon_code_snapshot,
   delivery_address_snapshot, status, payment_status)
SELECT $1, $2, $3, $4, $6, $7, $8,
       $11, $12, $13, $14, $15, $16,
       ($11 - $12 + $13 + $14 + $15 + $16),
       v.commission_bps_default,                       -- frozen at creation
       (($11 - $12) * v.commission_bps_default) / 10000,
       (($11 - $12) - ((($11 - $12) * v.commission_bps_default) / 10000)),
       $9, a.snapshot, 'CREATED', 'PENDING'
  FROM public.vendors v,
       LATERAL (SELECT row_to_json(ad.*)::jsonb AS snapshot
                  FROM public.addresses ad
                 WHERE ad.id = $6 AND ad.customer_id = $3) a
 WHERE v.id = $4
ON CONFLICT (idempotency_key) DO NOTHING;
-- NOTE: column `vendors.commission_bps_default` is illustrative — bind the
--   platform's current bps for the vendor from its canonical source and freeze
--   it here; reports must never re-derive it from live config.

-- D2. frozen lines (one row per cart line; live prices re-read, not trusted) --
INSERT INTO public.order_items
  (order_id, menu_item_id, vendor_id, item_name_snapshot,
   item_description_snapshot, unit_price_paise_snapshot, mrp_paise_snapshot,
   quantity, discount_paise_snapshot, tax_paise_snapshot, line_total_paise,
   cuisine_tags_snapshot, customization_snapshot)
SELECT o.id, mi.id, $4, mi.name, mi.description, mi.price_paise, mi.mrp_paise,
       ci.quantity, 0, 0, (mi.price_paise * ci.quantity),
       mi.cuisine_tags, ci.customization
  FROM public.orders o
  JOIN public.cart_items ci ON ci.cart_id = $5
  JOIN public.menu_items mi ON mi.id = ci.menu_item_id
 WHERE o.idempotency_key = $2
ON CONFLICT DO NOTHING;  -- re-run safe (no unique target: guarded by D1's key)

-- D3. coupon redemption (one row per order — UNIQUE blocks double redeem) -----
-- INSERT INTO public.coupon_usages (coupon_id, order_id, customer_id, discount_paise)
-- SELECT c.id, o.id, $3, $12 FROM public.coupons c, public.orders o
--  WHERE c.code = $9 AND o.idempotency_key = $2 AND $9 IS NOT NULL
-- ON CONFLICT (order_id) DO NOTHING;

-- D4. history + outbox (same TX — no order without trail/event) ---------------
INSERT INTO public.order_status_history
  (order_id, from_status, to_status, changed_by, actor_role,
   change_reason, idempotency_key)
SELECT id, NULL, 'CREATED', $10, 'CUSTOMER', 'order placed', $2 || ':created'
  FROM public.orders WHERE idempotency_key = $2
ON CONFLICT (idempotency_key) DO NOTHING;

INSERT INTO public.outbox_events
  (aggregate_type, aggregate_id, event_type, payload, idempotency_key)
SELECT 'orders', id::text, 'order.created',
       jsonb_build_object('order_id', id, 'order_number', order_number), $2
  FROM public.orders WHERE idempotency_key = $2
ON CONFLICT (idempotency_key) DO NOTHING;

-- E. capacity holds + cart close ----------------------------------------------
-- UPDATE public.vendor_slots SET capacity_reserved = capacity_reserved + <qty>
--  WHERE vendor_id=$4 AND slot_id=$7;  -- CHECK guards overflow → 409 SLOT_FULL
-- UPDATE public.menu_item_availability SET quantity_reserved = quantity_reserved + <qty>
--  WHERE …;                            -- CHECK guards oversell
-- UPDATE public.carts SET status='CONVERTED' WHERE id=$5 AND status='ACTIVE';

COMMIT;
-- expect: 1 order + n lines + 1 history + 1 outbox; replay with same $2 = no-op.

-- ----------------------------------------------------------------------------
-- S2: functions/verify_payment_webhook_transaction.sql -- Usage: payment webhook TX wrapper: replay guard -> log attempt -> flip SUCCESS -> confirm order -> open AVAILABLE delivery
-- (body below preserved VERBATIM from supabase/functions/verify_payment_webhook_transaction.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- verify_payment_webhook_transaction.sql — BACKEND FUNCTION PATTERN (not a migration)
-- ============================================================================
-- Flow    : provider payment webhook → verified SUCCESS → order confirm
-- Truth   : 0004 payments/payment_attempts/orders/history · 0007 outbox ·
--           0006 deliveries (created AVAILABLE on confirm)
-- Role    : service_role, Spring Boot webhook handler (signature-verified
--           BEFORE any SQL — signing secret lives in Spring env only)
-- Clients : C, A — frontend callbacks are hints; THIS path is the truth
-- Params  : $1 provider (RAZORPAY/CASHFREE/…) · $2 provider_payment_id ·
--           $3 provider_order_id · $4 amount_paise (must equal payments row) ·
--           $5 webhook payload JSONB · $6 event idempotency_key
-- Replay  : same event twice → step A finds status=SUCCESS → ACK, no writes
-- Canonical single-statement sources:
--   queries/commerce/payments/01_lookup_payment_for_verification.sql (step A
--     replay-safe lookup) · queries/commerce/payments/02_log_payment_attempt.sql
--     (step B append-only attempt log). This file is the multi-statement TX
--     wrapper (lookup → log attempt → flip SUCCESS → confirm order → open delivery).
-- ============================================================================

BEGIN;

-- A. replay guard — the event (or an earlier retry) already settled this ------
SELECT id, status, amount_paise FROM public.payments
 WHERE provider_payment_id = $2 FOR UPDATE;
-- status='SUCCESS' → COMMIT (no-op) + ACK 200 (absorb replay, no double-credit).
-- 0 rows → create the payments row first (INITIATED, idempotency_key=$6),
--   then continue below. amount mismatch ($4 <> row) → 422 + alert (tamper).

-- B. log the attempt (append-only; every delivery counts) --------------------
-- INSERT INTO public.payment_attempts
--   (payment_id, attempt_no, provider_reference, status,
--    request_payload, response_payload, idempotency_key)
-- VALUES (<id from A>, <next no>, $2, 'SUCCESS', '{}', $5, $6 || ':attempt')
-- ON CONFLICT (idempotency_key) DO NOTHING;

-- C. flip to SUCCESS + confirm the order + emit events (ONE TX) ---------------
-- UPDATE public.payments SET status='SUCCESS', verified_at=now(),
--   provider_payment_id=$2, provider_order_id=$3, webhook_payload=$5
--  WHERE id=<id> AND status <> 'SUCCESS';
-- UPDATE public.orders SET payment_status='SUCCESS', status='PLACED'
--  WHERE id=(SELECT order_id FROM public.payments WHERE id=<id>);
-- INSERT INTO public.order_status_history (order_id, from_status, to_status,
--   actor_role, change_reason, idempotency_key)
-- VALUES (<order>, 'PAYMENT_PENDING', 'PLACED', 'SYSTEM',
--   'webhook verified ' || $1, $6 || ':placed')
-- ON CONFLICT (idempotency_key) DO NOTHING;
-- INSERT INTO public.outbox_events (aggregate_type, aggregate_id, event_type,
--   payload, idempotency_key)
-- VALUES ('orders', <order>::text, 'order.payment_confirmed',
--   jsonb_build_object('order_id', <order>, 'payment_id', <id>), $6)
-- ON CONFLICT (idempotency_key) DO NOTHING;

-- D. open the fulfillment leg (delivery row 1:1 with order) -------------------
-- INSERT INTO public.deliveries (delivery_number, order_id, idempotency_key,
--   status, pickup/dropoff_address_snapshot, …)
-- VALUES (…, <order>, 'delivery:' || <order>::text, 'AVAILABLE', …)
-- ON CONFLICT (order_id) DO NOTHING;

COMMIT;
-- expect: payment SUCCESS + order PLACED + history + outbox + AVAILABLE delivery.
-- Failure webhooks: same shape with status FAILED + failure_code/message and
--   order → PAYMENT_FAILED (no delivery row; retry allowed via new attempt).

-- ----------------------------------------------------------------------------
-- S3: functions/check_serviceability_transaction.sql -- Usage: pre-write serviceability reads: vendor live -> PIN in zones -> slot capacity -> per-line availability
-- (body below preserved VERBATIM from supabase/functions/check_serviceability_transaction.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- check_serviceability_transaction.sql — BACKEND FUNCTION PATTERN (not a migration)
-- Canonical single-statement sources (CALL these — do not duplicate SQL):
--   queries/marketplace/zones/01_check_zone_serviceability.sql (step 2) ·
--   queries/marketplace/slots/02_check_slot_capacity.sql (step 3) ·
--   queries/marketplace/menus/03_get_item_availability.sql (step 4).
-- TX wrapper: this file composes the 4 pre-write reads; it performs no writes.
-- ============================================================================
-- Flow    : can this (vendor, PIN, slot) serve this customer right now?
-- Truth   : 0003_marketplace (service_zones, vendor_service_zones,
--           vendor_slots, menu_item_availability)
-- Role    : service_role, Spring Boot (CartService / CheckoutService pre-check)
-- Clients : C (gating cart + place-order), V/A (coverage views)
-- Steps   : 1 vendor live? → 2 PIN in vendor zones? → 3 slot capacity? →
--           4 items available for (slot, service_date)?
-- Params  : $1 vendor_id · $2 customer PIN · $3 slot_id · $4 service_date ·
--           $5 menu_item_id (repeat step 4 per cart line)
-- ============================================================================

-- 1. Vendor is orderable ------------------------------------------------------
SELECT id FROM public.vendors
 WHERE id = $1 AND status = 'APPROVED' AND is_active AND deleted_at IS NULL;
-- expect 1 row, else 422 VENDOR_NOT_AVAILABLE.

-- 2. PIN serviceability (fee override wins) ------------------------------------
-- @see ../queries/marketplace/zones/01_check_zone_serviceability.sql
SELECT sz.id, sz.code,
       vsz.delivery_fee_override_paise
  FROM public.service_zones sz
  JOIN public.vendor_service_zones vsz ON vsz.service_zone_id = sz.id
 WHERE vsz.vendor_id = $1
   AND sz.is_active
   AND sz.postal_codes @> ARRAY[$2];
-- expect ≥1 row, else 422 NOT_SERVICEABLE (before any write).

-- 3. Slot capacity (DB authoritative; Redis lock:slot:* held by caller) -------
-- @see ../queries/marketplace/slots/02_check_slot_capacity.sql
SELECT (capacity_total - capacity_reserved) AS remaining
  FROM public.vendor_slots
 WHERE vendor_id = $1 AND slot_id = $3;
-- expect remaining >= cart quantity for the slot, else 409 SLOT_FULL.

-- 4. Per-line day availability (dated row wins, else recurring fallback) ------
-- @see ../queries/marketplace/menus/03_get_item_availability.sql
SELECT quantity_available, quantity_reserved,
       (quantity_available - quantity_reserved) AS remaining
  FROM public.menu_item_availability
 WHERE menu_item_id = $5
   AND vendor_slot_id IN (
         SELECT id FROM public.vendor_slots
          WHERE vendor_id = $1 AND slot_id = $3)
   AND (service_date = $4 OR service_date IS NULL)
 ORDER BY service_date NULLS LAST
 LIMIT 1;
-- expect remaining >= line quantity, else 409 ITEM_SOLD_OUT (per line).
