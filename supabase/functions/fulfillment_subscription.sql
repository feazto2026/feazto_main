-- ============================================================================
-- fulfillment_subscription.sql -- BACKEND TX WRAPPERS: dispatch offer +
--                                subscription generation (NOT a migration)
-- ============================================================================
-- Role    : service_role, Spring Boot dispatch worker + scheduler.
-- Canonical sources:
--           S1 (dispatch offer) <- queries/08_fulfillment.sql Q1, Q2
--           S2 (subscription generation) <- queries/07_subscriptions.sql Q3
-- Sections: S1..S2 below. Bodies preserved VERBATIM (no SQL change).
-- History : consolidated 2026-10-03 from functions/
--           offer_delivery_dispatch_transaction.sql +
--           generate_subscription_order_transaction.sql (deleted). Old in-body
--           cross-references to fragmented paths kept verbatim.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- S1: functions/offer_delivery_dispatch_transaction.sql -- Usage: dispatch TX wrapper: lock + pick best candidate -> offer -> accept/timeout/escalate loop
-- (body below preserved VERBATIM from supabase/functions/offer_delivery_dispatch_transaction.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- offer_delivery_dispatch_transaction.sql — BACKEND FUNCTION PATTERN (not a migration)
-- ============================================================================
-- Flow    : AVAILABLE delivery → best-rider offer → accept/timeout/escalate
-- Truth   : 0006 deliveries/delivery_assignments/history · riders,
--           rider_availability
-- Role    : service_role, Spring Boot dispatch worker
-- Clients : R (offer in rider_app), A (tracking); V (read-only status)
-- Params  : $1 :: uuid — deliveries.id · $2 :: int — attempt_no (escalates) ·
--           $3 :: interval — offer TTL (e.g. '90 seconds')
-- Canonical single-statement sources:
--   queries/fulfillment/deliveries/01_list_online_riders.sql (candidate scan) ·
--   queries/fulfillment/deliveries/02_offer_delivery_assignment.sql (core OFFERED
--     + ASSIGNMENT_PENDING TX form). This file is the multi-statement TX wrapper
--     (score → offer → accept/timeout/escalate loop).
-- ============================================================================

BEGIN;

-- A. lock + pick the best candidate (skip riders with a live OPEN offer) ------
-- SELECT r.id, <score> FROM public.riders r …(01_list_online_riders.sql + zone/join)…
--  WHERE r.id NOT IN (SELECT rider_id FROM public.delivery_assignments
--                      WHERE delivery_id=$1 AND status='OFFERED'
--                        AND expires_at > now())
--  ORDER BY <score> LIMIT 1 FOR UPDATE SKIP LOCKED;
-- no candidate → COMMIT no-op; scheduler retries with wider zone/longer TTL.

-- B. offer (ONE TX with delivery state — core form in 02_offer_delivery_assignment.sql) -------
-- INSERT INTO public.delivery_assignments (delivery_id, rider_id, attempt_no,
--   status, expires_at, assignment_reason, idempotency_key)
-- VALUES ($1, <rider>, $2, 'OFFERED', now()+$3, <reason jsonb>, <key>)
-- ON CONFLICT (idempotency_key) DO NOTHING;
-- UPDATE public.deliveries SET status='ASSIGNMENT_PENDING', rider_id=<rider>
--  WHERE id=$1 AND status IN ('AVAILABLE','ASSIGNMENT_PENDING');
-- INSERT INTO public.delivery_status_history (…) — same TX.

COMMIT;

-- C. outcomes (later transitions, same guarded pattern as 03_transition_order_status.sql) ---
-- ACCEPT: assignment OFFERED→ACCEPTED + delivery →ASSIGNED/RIDER_ACCEPTED.
-- REJECT/TIMEOUT: assignment →REJECTED/TIMEOUT; loop back to A with
--   attempt_no+1 (row history preserved — never UPDATE over a timed-out offer).
--   After N attempts: delivery →CANCELLED/FAILED + outbox event + ops alert.

-- ----------------------------------------------------------------------------
-- S2: functions/generate_subscription_order_transaction.sql -- Usage: subscription daily row (PENDING) -> real order TX wrapper (lock row -> create order -> mark GENERATED)
-- (body below preserved VERBATIM from supabase/functions/generate_subscription_order_transaction.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- generate_subscription_order_transaction.sql — BACKEND FUNCTION PATTERN (not a migration)
-- ============================================================================
-- Flow    : subscription daily row (PENDING) → real order (prepaid / COD terms)
-- Truth   : 0005 subscription_daily_orders/subscriptions · 0004 orders/items ·
--           0007 outbox · 0003 availability (re-checked — vendor may have
--           edited quantities since the contract)
-- Role    : service_role, Spring Boot scheduler (after
--           queries/subscriptions/03_generate_subscription_daily_rows.sql
--           inserted the daily rows for the date)
-- Canonical single-statement source:
--   queries/subscriptions/03_generate_subscription_daily_rows.sql (daily-row tick);
--   this file is the multi-statement TX wrapper (lock daily row → create order → mark GENERATED).
-- Clients : C (orders appear), V (prep list), A (generator monitor)
-- Params  : $1 :: uuid — subscription_daily_orders.id (the PENDING row)
-- Rules   : pause/cancel NEVER touches generated orders (only stops FUTURE
--           ticks); each daily row yields ≤1 order (UNIQUE + idempotency_key);
--           link via orders.subscription_id (+ fk_orders_schedule, 0009).
-- ============================================================================

BEGIN;

-- A. lock the daily row (skip when already consumed by a racing worker) -------
SELECT id, subscription_id, service_date, meal_slot_id, status
  FROM public.subscription_daily_orders WHERE id = $1 FOR UPDATE;
-- status <> 'PENDING' → COMMIT no-op (already GENERATED/SKIPPED).

-- B. create the order linked to the contract (frozen price from subscription) -
-- INSERT INTO public.orders (order_number, idempotency_key, customer_id,
--   vendor_id, slot_id, service_date, subscription_id, subscription_schedule_id,
--   <money snapshot from subscriptions.*_paise / meal_plans>, status, …)
-- SELECT …, 'suborder:' || $1::text, … FROM public.subscriptions s
--  WHERE s.id = (SELECT subscription_id FROM … WHERE id=$1)
-- ON CONFLICT (idempotency_key) DO NOTHING;
-- INSERT INTO public.order_items (…) — lines from the plan snapshot.
-- INSERT INTO public.order_status_history (…) — CREATED row, same TX.
-- INSERT INTO public.outbox_events (…) — 'order.created' {subscription:true}.

-- C. mark the daily row consumed ----------------------------------------------
-- UPDATE public.subscription_daily_orders
--    SET status='GENERATED', order_id=<new order id>
--  WHERE id=$1 AND status='PENDING';

COMMIT;
-- expect: 1 order + daily row GENERATED; re-run on same $1 = no-op.
-- Holiday/pause skip: UPDATE … SET status='SKIPPED', skip_reason=… (no order).
