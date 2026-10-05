-- ============================================================================
-- 12_admin.sql -- health snapshot (USAGE-GROUPED library)
-- ============================================================================
-- Truth   : all migrations 0001..0009 (56-table list) + seed/demo.sql
--           (expected demo counts); mirrors config/rls.md A0 +
--           seeds/verify_seed_counts.sql (focused seed-twice proof).
-- Role    : service_role (admin health endpoint / runbook step 4).
-- Clients : A
-- Guards  : read-only (census + counts, no writes).
-- Sections: Q1 (single section). Body preserved VERBATIM.
-- History : consolidated 2026-10-03 from queries/admin/
--           01_get_platform_health_snapshot.sql (deleted).
-- See     : supabase/queries/README.md, docs/database/schema.md Query-paths.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Q1: queries/admin/01_get_platform_health_snapshot.sql -- Usage: 56-table census + seed footprint + 24h flow pressure (runbook verify step)
-- (body below preserved VERBATIM from supabase/queries/admin/01_get_platform_health_snapshot.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_get_platform_health_snapshot.sql — 56-table / seed health snapshot (read-only, admin)
-- ============================================================================
-- Truth   : all migrations 0001..0009 (table list) + seed/demo.sql (expected
--           demo counts) — mirrors supabase/config/rls.md §0 and
--           supabase/seeds/verify_seed_counts.sql (focused seed-twice proof)
-- Role    : service_role (admin health endpoint / runbook step 4)
-- Clients : A
-- ============================================================================

-- 0. Business-table census ----------------------------------------------------
SELECT count(*) AS business_tables
  FROM pg_tables WHERE schemaname = 'public'
   AND tablename IN (
     'roles','permissions','role_permissions','platform_users','user_roles',
     'customer_profiles','addresses','regions','cuisines','vendors',
     'vendor_regions','vendor_cuisines','vendor_verifications','vendor_documents',
     'menus','menu_categories','menu_items','slots','vendor_slots',
     'service_zones','vendor_service_zones','menu_item_availability',
     'coupons','coupon_usages','carts','cart_items','orders','order_items',
     'order_status_history','payments','payment_attempts','refunds',
     'meal_plans','subscriptions','subscription_schedules',
     'subscription_daily_orders','subscription_status_history',
     'riders','rider_documents','rider_availability','deliveries',
     'delivery_assignments','delivery_status_history',
     'support_tickets','support_ticket_messages','audit_logs','notifications',
     'notification_preferences','reviews','commissions','vendor_payouts',
     'vendor_payout_items','rider_payouts','rider_payout_items',
     'outbox_events','idempotency_keys');
-- expect: 56

-- 1. Demo-seed footprint (post-seed values; run twice → identical) -------------
SELECT (SELECT count(*) FROM public.regions) AS regions,                       -- 5
       (SELECT count(*) FROM public.orders) AS orders,                         -- 1
       (SELECT count(*) FROM public.payments) AS payments,                     -- 1
       (SELECT count(*) FROM public.deliveries) AS deliveries,                 -- 1
       (SELECT count(*) FROM public.subscription_daily_orders) AS daily,       -- 1
       (SELECT count(*) FROM public.outbox_events) AS outbox;                 -- 1

-- 2. Flow pressure (last 24h — operational pulse) ------------------------------
SELECT (SELECT count(*) FROM public.orders
         WHERE created_at > now() - interval '24 hours') AS orders_24h,
       (SELECT count(*) FROM public.payments
         WHERE created_at > now() - interval '24 hours'
           AND status = 'SUCCESS') AS payments_success_24h,
       (SELECT count(*) FROM public.deliveries
         WHERE status NOT IN ('DELIVERED', 'CANCELLED', 'FAILED')) AS open_deliveries,
       (SELECT count(*) FROM public.outbox_events
         WHERE status = 'PENDING') AS outbox_pending,
       (SELECT count(*) FROM public.support_tickets
         WHERE status = 'OPEN') AS open_tickets;
