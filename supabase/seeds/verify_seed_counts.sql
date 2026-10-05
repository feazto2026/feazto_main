-- ============================================================================
-- verify_seed_counts.sql — seed-twice idempotency proof (read-only)
-- ============================================================================
-- Usage : psql "$DATABASE_URL" -f supabase/seed/demo.sql   (run 1, note counts)
--         psql "$DATABASE_URL" -f supabase/seed/demo.sql   (run 2)
--         psql "$DATABASE_URL" -f supabase/seeds/verify_seed_counts.sql
-- Truth : supabase/seed/demo.sql (fixed UUIDs + ON CONFLICT DO NOTHING) ·
--         mirrors supabase/config/rls.md §10
-- Role  : service_role / superuser (reads). Runbook: supabase/00_RUNBOOK.md §3
-- ============================================================================

-- A. Core chain footprint ------------------------------------------------------
-- expect: regions=5, orders=1, payments=1, deliveries=1, daily=1, outbox=1
-- (identical after run 1 AND run 2 — any growth = seed regression)
SELECT (SELECT count(*) FROM public.regions) AS regions,
       (SELECT count(*) FROM public.orders) AS orders,
       (SELECT count(*) FROM public.payments) AS payments,
       (SELECT count(*) FROM public.deliveries) AS deliveries,
       (SELECT count(*) FROM public.subscription_daily_orders) AS daily,
       (SELECT count(*) FROM public.outbox_events) AS outbox;

-- B. Full demo footprint (matches the demo-seed NOTICE line) ------------------
SELECT (SELECT count(*) FROM public.regions) AS regions,                       -- 5
       (SELECT count(*) FROM public.cuisines) AS cuisines,                     -- 4
       (SELECT count(*) FROM public.service_zones) AS zones,                  -- 4
       (SELECT count(*) FROM public.slots) AS slots,                           -- 4
       (SELECT count(*) FROM public.menu_items) AS items,                     -- 4
       (SELECT count(*) FROM public.orders) AS orders,                         -- 1
       (SELECT count(*) FROM public.payments) AS payments,                     -- 1
       (SELECT count(*) FROM public.deliveries) AS deliveries,                 -- 1
       (SELECT count(*) FROM public.subscriptions) AS subs,                    -- 1
       (SELECT count(*) FROM public.subscription_daily_orders) AS daily,       -- 1
       (SELECT count(*) FROM public.vendor_payouts) AS vendor_payouts,         -- 1
       (SELECT count(*) FROM public.rider_payouts) AS rider_payouts,           -- 1
       (SELECT count(*) FROM public.reviews) AS reviews,                       -- 1
       (SELECT count(*) FROM public.support_tickets) AS tickets,               -- 1
       (SELECT count(*) FROM public.notifications) AS notifs,                 -- 1
       (SELECT count(*) FROM public.outbox_events) AS outbox,                 -- 1
       (SELECT count(*) FROM public.idempotency_keys) AS idem;                -- 1
