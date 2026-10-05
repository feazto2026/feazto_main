-- ============================================================================
-- 09_finance.sql -- commission + vendor/rider payouts (USAGE-GROUPED library)
-- ============================================================================
-- Truth   : supabase/migrations/0007_operations.sql (commissions,
--           vendor_payouts(+items), rider_payouts(+items))
--           + 0009 net CHECKs (vendor/rider net + item amount checks)
-- Role    : service_role (FINANCE_ADMIN batch jobs only).
-- Clients : V, R, A (statements read frozen snapshots, never live rates)
-- Guards  : commissions.order_id UNIQUE (correct via REVERSED + re-accrue,
--           never UPDATE); payout payout_number/provider_reference/
--           idempotency_key UNIQUE + UNIQUE(payout,order/delivery); net CHECKs
--           roll back the whole batch on mismatch.
-- Sections: Q1..Q3 in execution order. Each body preserved VERBATIM.
-- History : consolidated 2026-10-03 from queries/finance/
--           01_accrue_order_commission.sql +
--           02_create_vendor_payout_batch.sql +
--           03_create_rider_payout_batch.sql (deleted).
-- See     : supabase/queries/README.md, docs/database/schema.md Query-paths.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Q1: queries/finance/01_accrue_order_commission.sql -- Usage: commission row copied from the frozen order snapshot (post-payment-SUCCESS)
-- (body below preserved VERBATIM from supabase/queries/finance/01_accrue_order_commission.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_accrue_order_commission.sql — commission row from the order snapshot (no recompute)
-- ============================================================================
-- Truth   : supabase/migrations/0007_operations.sql (commissions)
--           Guard: order_id UNIQUE (one commission per order)
--           Corrections via REVERSED + re-accrue, never UPDATE (0008 note)
-- Role    : service_role (Spring Boot finance job, post-payment-SUCCESS)
-- Clients : V, R, A (statements read snapshots, never live rates)
-- Params  : $1 :: uuid — orders.id
-- Effect  : copies the FROZEN order snapshot (commission_bps, totals) into
--           commissions.*_snapshot — menu/commission edits never rewrite history
-- ============================================================================

INSERT INTO public.commissions
  (order_id, vendor_id,
   commission_bps_snapshot, order_total_paise_snapshot,
   commission_paise_snapshot, status, idempotency_key)
SELECT o.id,
       o.vendor_id,
       o.commission_bps_snapshot,
       o.total_paise,
       o.platform_commission_paise_snapshot,
       'ACCRUED',
       'commission:' || o.id::text
  FROM public.orders o
 WHERE o.id = $1
   AND o.payment_status = 'SUCCESS'
ON CONFLICT (order_id) DO NOTHING;
-- expect: 1 row on first accrue; 0 when payment not SUCCESS or already accrued.

-- ----------------------------------------------------------------------------
-- Q2: queries/finance/02_create_vendor_payout_batch.sql -- Usage: vendor payout batch + items in ONE TX (net-guarded)
-- (body below preserved VERBATIM from supabase/queries/finance/02_create_vendor_payout_batch.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 02_create_vendor_payout_batch.sql — payout batch + items (ONE TX, net-guarded)
-- ============================================================================
-- Truth   : supabase/migrations/0007_operations.sql
--           (vendor_payouts, vendor_payout_items) + 0009 net CHECKs
--           (vendor_payouts_net_check: net = gross-commission-refunds+adj,
--            net >= 0; items amount_check >= 0)
--           Guards: payout_number/provider_reference/idempotency_key UNIQUE,
--             UNIQUE(payout_id, order_id)
-- Role    : service_role (FINANCE_ADMIN batch job only)
-- Clients : V, A
-- Params  : $1 payout_number · $2 idempotency_key · $3 vendor_id ·
--           $4 gross · $5 commission · $6 refunds · $7 adjustments
--             (net derived: must satisfy net CHECK) · $8 period_start ·
--           $9 period_end
-- Items   : INSERT INTO vendor_payout_items (payout_id, order_id, amount_paise)
--           one row per settled order (UNIQUE blocks double-pay of an order in
--           the same batch; cross-batch double-pay is a backend scope check).
-- ============================================================================

BEGIN;

INSERT INTO public.vendor_payouts
  (payout_number, idempotency_key, vendor_id,
   gross_paise, commission_paise, refunds_paise, adjustments_paise, net_paise,
   period_start, period_end, status)
VALUES
  ($1, $2, $3, $4, $5, $6, $7, ($4 - $5 - $6 + $7), $8, $9, 'INITIATED')
ON CONFLICT (idempotency_key) DO NOTHING
RETURNING id;

-- INSERT INTO public.vendor_payout_items (payout_id, order_id, amount_paise)
-- SELECT <payout_id>, o.id, o.vendor_payout_paise_snapshot
--   FROM public.orders o WHERE o.id = ANY($10) AND o.vendor_id = $3
-- ON CONFLICT (payout_id, order_id) DO NOTHING;

COMMIT;
-- expect: 1 payout + n item rows; CHECK violation (net mismatch/negative) →
--   whole batch rolls back — fix the inputs, never the CHECK.

-- ----------------------------------------------------------------------------
-- Q3: queries/finance/03_create_rider_payout_batch.sql -- Usage: rider payout batch + items in ONE TX, DELIVERED only (net-guarded)
-- (body below preserved VERBATIM from supabase/queries/finance/03_create_rider_payout_batch.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 03_create_rider_payout_batch.sql — rider payout batch + items (ONE TX, net-guarded)
-- ============================================================================
-- Truth   : supabase/migrations/0007_operations.sql
--           (rider_payouts, rider_payout_items) + 0009 net CHECKs
--           (rider_payouts_net_check: net = gross + adjustments; items >= 0)
--           Guards: payout_number/provider_reference/idempotency_key UNIQUE,
--             UNIQUE(payout_id, delivery_id)
-- Role    : service_role (FINANCE_ADMIN batch job only)
-- Clients : R, A
-- Params  : $1 payout_number · $2 idempotency_key · $3 rider_id ·
--           $4 gross · $5 adjustments (net derived) · $6 period_start ·
--           $7 period_end
-- Items   : INSERT INTO rider_payout_items (payout_id, delivery_id, amount_paise)
--           one row per completed delivery (UNIQUE blocks double-pay per batch).
-- ============================================================================

BEGIN;

INSERT INTO public.rider_payouts
  (payout_number, idempotency_key, rider_id,
   gross_paise, adjustments_paise, net_paise,
   period_start, period_end, status)
VALUES
  ($1, $2, $3, $4, $5, ($4 + $5), $6, $7, 'INITIATED')
ON CONFLICT (idempotency_key) DO NOTHING
RETURNING id;

-- INSERT INTO public.rider_payout_items (payout_id, delivery_id, amount_paise)
-- SELECT <payout_id>, d.id, d.rider_payout_paise_snapshot
--   FROM public.deliveries d
--  WHERE d.id = ANY($8) AND d.rider_id = $3 AND d.status = 'DELIVERED'
-- ON CONFLICT (payout_id, delivery_id) DO NOTHING;

COMMIT;
-- expect: 1 payout + n item rows; only DELIVERED deliveries are payable.
