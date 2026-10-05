-- ============================================================================
-- 07_subscriptions.sql -- plans + active + daily tick (USAGE-GROUPED library)
-- ============================================================================
-- Truth   : supabase/migrations/0005_subscription.sql (meal_plans,
--           subscriptions, subscription_schedules, subscription_daily_orders)
-- Role    : service_role (Spring Boot app + scheduler only).
-- Clients : C (orders appear automatically), V, A (pause/cancel never touches
--           already-generated orders -- only stops FUTURE ticks)
-- Guards  : uq_subscriptions_single_live (one ACTIVE/PAUSED per triple);
--           UNIQUE(subscription_id, service_date, meal_slot_id) anti-double-run
--           + idempotency_key UNIQUE. Q3 canonical; TX wrapper:
--           functions/fulfillment_subscription.sql S2.
-- Sections: Q1..Q3 in execution order. Each body preserved VERBATIM.
-- History : consolidated 2026-10-03 from queries/subscriptions/
--           01_list_subscription_plans.sql + 02_get_active_subscription.sql +
--           03_generate_subscription_daily_rows.sql (deleted).
-- See     : supabase/queries/README.md, docs/database/schema.md Query-paths.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Q1: queries/subscriptions/01_list_subscription_plans.sql -- Usage: vendor subscription plan catalogue (prices frozen at contract time)
-- (body below preserved VERBATIM from supabase/queries/subscriptions/01_list_subscription_plans.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_list_subscription_plans.sql — vendor subscription plan catalogue
-- ============================================================================
-- Truth   : supabase/migrations/0005_subscription.sql (meal_plans)
--           Guard: service weekdays ⊆ 0..6
-- Role    : service_role (Spring Boot only)
-- Clients : C, V, A
-- Params  : $1 :: uuid — vendors.id
-- ============================================================================

SELECT *
  FROM public.meal_plans
 WHERE vendor_id = $1
   AND is_active
 ORDER BY price_paise;
-- expect: n active plans; prices frozen into subscriptions at contract time.

-- ----------------------------------------------------------------------------
-- Q2: queries/subscriptions/02_get_active_subscription.sql -- Usage: the one live contract per (customer,vendor,plan) + firing rules
-- (body below preserved VERBATIM from supabase/queries/subscriptions/02_get_active_subscription.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 02_get_active_subscription.sql — the one live contract per (customer,vendor,plan)
-- ============================================================================
-- Truth   : supabase/migrations/0005_subscription.sql
--           (subscriptions, subscription_schedules)
--           Guard: uq_subscriptions_single_live (partial unique on
--             ACTIVE/PAUSED per customer+vendor+plan)
-- Role    : service_role (Spring Boot only)
-- Clients : C, V, A — pause/cancel never touches already-generated orders
-- Params  : $1 :: uuid — customer_profiles.id · $2 :: uuid — vendors.id ·
--           $3 :: uuid — meal_plans.id
-- ============================================================================

SELECT s.*,
       (SELECT coalesce(jsonb_agg(
                 jsonb_build_object('weekday', weekday, 'meal_slot_id', meal_slot_id)
                 ORDER BY weekday), '[]'::jsonb)
          FROM public.subscription_schedules sch
         WHERE sch.subscription_id = s.id) AS firing_rules
  FROM public.subscriptions s
 WHERE s.customer_id = $1
   AND s.vendor_id = $2
   AND s.meal_plan_id = $3
   AND s.status IN ('ACTIVE', 'PAUSED');
-- expect: 0..1 rows (partial unique); 1 row → new purchase blocked until
--   cancel/expire (backend returns 409 + existing contract).

-- ----------------------------------------------------------------------------
-- Q3: queries/subscriptions/03_generate_subscription_daily_rows.sql -- Usage: idempotent daily-order generation cron tick for ONE date (canonical; wrapper: functions/fulfillment_subscription.sql S2)
-- (body below preserved VERBATIM from supabase/queries/subscriptions/03_generate_subscription_daily_rows.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 03_generate_subscription_daily_rows.sql — idempotent daily-order generation (cron tick, ONE date)
-- ============================================================================
-- Truth   : supabase/migrations/0005_subscription.sql
--           (subscription_daily_orders, subscription_schedules)
--           Guard: UNIQUE(subscription_id, service_date, meal_slot_id) —
--             the anti-double-run key; idempotency_key UNIQUE
--           Hot index: ACTIVE-window generator scan (0008)
-- Role    : service_role (Spring Boot scheduler only)
-- Clients : C (orders appear automatically); V, A (visibility)
-- Params  : $1 :: date — service_date to generate for (usually tomorrow)
-- Effect  : one daily row per due (subscription, slot); concurrent/overlapping
--           ticks collapse on the UNIQUE — second writer inserts 0 rows.
--           Order creation from each daily row: see
--           supabase/functions/generate_subscription_order_transaction.sql
-- ============================================================================

INSERT INTO public.subscription_daily_orders
  (subscription_id, service_date, meal_slot_id, idempotency_key, status)
SELECT s.id,
       $1,
       sch.meal_slot_id,
       'sub:' || s.id::text || ':' || $1::text || ':' || sch.meal_slot_id::text,
       'PENDING'
  FROM public.subscriptions s
  JOIN public.subscription_schedules sch
    ON sch.subscription_id = s.id
   AND sch.weekday = EXTRACT(DOW FROM $1)::int
 WHERE s.status = 'ACTIVE'
   AND s.start_date <= $1
   AND (s.end_date IS NULL OR s.end_date >= $1)
ON CONFLICT (subscription_id, service_date, meal_slot_id) DO NOTHING;
-- expect: n rows inserted on first tick; 0 on re-tick (idempotent).
-- Skipped weekdays (no firing rule) correctly yield 0 rows.
