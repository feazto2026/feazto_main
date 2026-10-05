-- ============================================================================
-- 05_orders.sql -- create + detail + transition (USAGE-GROUPED library)
-- ============================================================================
-- Truth   : supabase/migrations/0004_commerce.sql
--           (orders, order_items, order_status_history, coupon_usages)
--           + 0007 (outbox_events, idempotency_keys)
-- Role    : service_role (Spring Boot only; copy into order repository).
-- Clients : C, V, A (subscription generator also inserts here)
-- Guards  : orders.order_number/idempotency_key UNIQUE;
--           orders_total_consistency_check (total = subtotal-discount+tax+
--           delivery+platform+packaging); coupon_usages.order_id UNIQUE.
--           Status rule: UPDATE status + history row in SAME TX; clients never
--           write status (allowed edges enforced in backend code).
-- Sections: Q1..Q3. Q1 canonical; TX wrapper: functions/order_lifecycle.sql S1.
--           Bodies VERBATIM.
-- History : consolidated 2026-10-03 from queries/commerce/orders/
--           01_create_order.sql + 02_get_order_detail.sql +
--           03_transition_order_status.sql (deleted).
-- See     : supabase/queries/README.md, docs/database/schema.md Query-paths.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Q1: queries/commerce/orders/01_create_order.sql -- Usage: order + frozen snapshots + CREATED history + outbox in ONE TX (canonical; wrapper: functions/order_lifecycle.sql S1)
-- (body below preserved VERBATIM from supabase/queries/commerce/orders/01_create_order.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_create_order.sql — order + frozen snapshots + history + outbox (ONE TX)
-- ============================================================================
-- Truth   : supabase/migrations/0004_commerce.sql
--           (orders, order_items, order_status_history, coupon_usages)
--           + 0007 (outbox_events, idempotency_keys)
--           Guards: orders.order_number/idempotency_key UNIQUE,
--             orders_total_consistency_check
--             (total = subtotal-discount+tax+delivery+platform+packaging),
--             coupon_usages.order_id UNIQUE
-- Role    : service_role (Spring Boot only — full pattern in
--           supabase/functions/create_order_transaction.sql)
-- Clients : C (subscription generator also inserts here; see
--           functions/generate_subscription_order_transaction.sql)
-- Params  : $1 order_number · $2 idempotency_key · $3 customer_id ·
--           $4 vendor_id · $5 address_id · $6 slot_id · $7 service_date ·
--           $8..$13 money snapshot (subtotal, discount, tax, delivery_fee,
--             platform_fee, packaging_fee — total derived, must satisfy CHECK)
--           $14 commission_bps · $15 platform_commission · $16 vendor_payout ·
--           $17 coupon_code · $18 delivery_address JSONB · $19 changed_by user
-- Effect  : order + CREATED history + outbox event atomically; retries reuse
--           $2 (ON CONFLICT DO NOTHING → return existing) — never double-charge
-- ============================================================================

BEGIN;

-- Idempotency ledger first: concurrent retries collapse here, not in orders.
INSERT INTO public.idempotency_keys (scope, key)
VALUES ('create_order', $2)
ON CONFLICT (scope, key) DO NOTHING;

INSERT INTO public.orders
  (order_number, idempotency_key, customer_id, vendor_id, address_id, slot_id,
   service_date, subtotal_paise, discount_paise, tax_paise,
   delivery_fee_paise, platform_fee_paise, packaging_fee_paise,
   total_paise,
   commission_bps_snapshot, platform_commission_paise_snapshot,
   vendor_payout_paise_snapshot, coupon_code_snapshot,
   delivery_address_snapshot, status, payment_status)
VALUES
  ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13,
   ($8 - $9 + $10 + $11 + $12 + $13),
   $14, $15, $16, $17, $18::jsonb, 'CREATED', 'PENDING')
ON CONFLICT (idempotency_key) DO NOTHING
RETURNING id;

-- Order lines freeze item_name/unit_price/mrp/discount/tax/tags/customization
-- (INSERT INTO order_items … one row per cart line; omitted here — see
-- functions/create_order_transaction.sql for the full multi-row form).

-- State-machine rule: every transition appends history in the SAME TX.
INSERT INTO public.order_status_history
  (order_id, from_status, to_status, changed_by, actor_role,
   change_reason, idempotency_key)
SELECT id, NULL, 'CREATED', $19, 'CUSTOMER', 'order placed', $2 || ':created'
  FROM public.orders WHERE idempotency_key = $2
ON CONFLICT (idempotency_key) DO NOTHING;

-- Domain event for async consumers (relay scans ix_outbox_pending).
INSERT INTO public.outbox_events
  (aggregate_type, aggregate_id, event_type, payload, idempotency_key)
SELECT 'orders', id::text, 'order.created',
       jsonb_build_object('order_id', id, 'order_number', order_number), $2
  FROM public.orders WHERE idempotency_key = $2
ON CONFLICT (idempotency_key) DO NOTHING;

COMMIT;
-- expect: 1 order + 1 history + 1 outbox row on first run; re-runs with the
--   same $2 insert 0 rows (ON CONFLICT) — return the existing order instead.

-- ----------------------------------------------------------------------------
-- Q2: queries/commerce/orders/02_get_order_detail.sql -- Usage: order header + lines + history in one fetch (detail view)
-- (body below preserved VERBATIM from supabase/queries/commerce/orders/02_get_order_detail.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 02_get_order_detail.sql — order + lines + history (single fetch for detail view)
-- ============================================================================
-- Truth   : supabase/migrations/0004_commerce.sql
--           (orders, order_items, order_status_history)
-- Role    : service_role (Spring Boot only; ownership checked in API layer)
-- Clients : C, V, A — reports read the *_snapshot columns, never live menus
-- Params  : $1 :: uuid — orders.id
-- ============================================================================

-- 1. header (financial snapshot + frozen address + commission) ----------------
SELECT * FROM public.orders WHERE id = $1;

-- 2. immutable lines ----------------------------------------------------------
SELECT * FROM public.order_items WHERE order_id = $1 ORDER BY created_at;

-- 3. append-only transition trail ---------------------------------------------
SELECT * FROM public.order_status_history
 WHERE order_id = $1 ORDER BY created_at;
-- expect: 1 header + n lines + ≥1 history row (CREATED first).

-- ----------------------------------------------------------------------------
-- Q3: queries/commerce/orders/03_transition_order_status.sql -- Usage: server-side guarded status transition + history in ONE TX (optimistic from_status)
-- (body below preserved VERBATIM from supabase/queries/commerce/orders/03_transition_order_status.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 03_transition_order_status.sql — server-side status transition + history (ONE TX)
-- ============================================================================
-- Truth   : supabase/migrations/0004_commerce.sql
--           (orders.status TEXT+CHECK, order_status_history append-only)
-- Role    : service_role (Spring Boot state machine only — clients NEVER write
--           status; allowed edges are enforced in backend code, not in SQL)
-- Clients : C, V, R, A (actor_role records who drove the transition)
-- Params  : $1 :: uuid — orders.id · $2 :: text — to_status (CHECK-listed) ·
--           $3 :: uuid — changed_by (platform_users.id) · $4 :: text — actor_role
--           $5 :: text — change_reason · $6 :: text — history idempotency_key
--           $7 :: text — expected from_status (optimistic guard; caller binds
--           the status it read — transition aborts when stale)
-- Effect  : guarded UPDATE + history row in ONE TX; replay with same $6 = no-op
-- ============================================================================

BEGIN;

-- Lock the row and enforce the expected edge (stale caller → 0 rows → abort).
SELECT id FROM public.orders WHERE id = $1 AND status = $7 FOR UPDATE;

UPDATE public.orders
   SET status = $2,
       updated_at = now(),
       delivered_at = CASE WHEN $2 = 'DELIVERED' THEN now() ELSE delivered_at END,
       completed_at = CASE WHEN $2 = 'COMPLETED' THEN now() ELSE completed_at END,
       cancelled_at = CASE WHEN $2 = 'CANCELLED' THEN now() ELSE cancelled_at END
 WHERE id = $1
   AND status = $7;

INSERT INTO public.order_status_history
  (order_id, from_status, to_status, changed_by, actor_role,
   change_reason, idempotency_key)
VALUES ($1, $7, $2, $3, $4, $5, $6)
ON CONFLICT (idempotency_key) DO NOTHING;

COMMIT;
-- expect: 1 order row updated + 1 history row (from=$7 → to=$2).
-- 0 rows updated → stale from_status (concurrent transition won) → caller
--   re-reads via 02_get_order_detail.sql and retries on the fresh edge.
-- Replay with same $6 inserts 0 history rows (ON CONFLICT) — safe retry.
