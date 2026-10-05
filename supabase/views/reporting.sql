-- ============================================================================
-- reporting.sql -- opt-in reporting VIEWs over frozen snapshots (NOT migrations)
-- ============================================================================
-- Role    : service_role (admin/analytics sessions). Re-running the CREATEs is
--           always safe (CREATE OR REPLACE VIEW).
-- Rule    : statements derive from SNAPSHOTS + settled payout items; live
--           menu/commission edits never appear here. Only DELIVERED legs payable.
-- Sections: V1..V4 below (vendor earnings, rider earnings, order timeline,
--           dashboard summary). Bodies preserved VERBATIM.
-- History : consolidated 2026-10-03 from views/get_vendor_earnings.sql +
--           get_rider_earnings.sql + get_order_timeline.sql +
--           get_dashboard_summary.sql (deleted).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- V1: views/get_vendor_earnings.sql -- Usage: vendor statement view v_vendor_earnings (snapshot commission + settled payout)
-- (body below preserved VERBATIM from supabase/views/get_vendor_earnings.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- get_vendor_earnings.sql — VIEW (opt-in reporting): vendor statement from snapshots
-- ============================================================================
-- Truth   : 0004 orders (vendor_payout_paise_snapshot — frozen at creation) ·
--           0007 commissions/vendor_payouts(+items)
-- Rule    : statements derive from SNAPSHOTS + settled payout items; live
--           menu/commission edits never appear here. NOT a migration.
-- Clients : V (statements), A/FINANCE (reconciliation)
-- Params  : $1 vendor_id · $2 period_start · $3 period_end
-- ============================================================================

CREATE OR REPLACE VIEW public.v_vendor_earnings AS
SELECT o.vendor_id,
       o.id                 AS order_id,
       o.order_number,
       o.status             AS order_status,
       o.payment_status,
       o.total_paise        AS order_total_paise,
       o.platform_commission_paise_snapshot AS commission_paise,
       o.vendor_payout_paise_snapshot        AS vendor_payout_paise,
       c.status             AS commission_status,
       (SELECT vpi.vendor_payout_id
          FROM public.vendor_payout_items vpi
         WHERE vpi.order_id = o.id
         LIMIT 1)           AS settled_in_payout,
       o.created_at         AS ordered_at
  FROM public.orders o
  LEFT JOIN public.commissions c ON c.order_id = o.id;

-- Usage:
-- SELECT *, sum(vendor_payout_paise) OVER () AS period_total
--   FROM public.v_vendor_earnings
--  WHERE vendor_id = $1 AND ordered_at >= $2 AND ordered_at < $3
--  ORDER BY ordered_at;

-- ----------------------------------------------------------------------------
-- V2: views/get_rider_earnings.sql -- Usage: rider statement view v_rider_earnings (DELIVERED only)
-- (body below preserved VERBATIM from supabase/views/get_rider_earnings.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- get_rider_earnings.sql — VIEW (opt-in reporting): rider statement (DELIVERED only)
-- ============================================================================
-- Truth   : 0006 deliveries (rider_payout_paise_snapshot + tip) ·
--           0007 rider_payouts(+items)
-- Rule    : only DELIVERED legs are payable; snapshots frozen per delivery.
--           NOT a migration.
-- Clients : R (earnings), A/FINANCE (reconciliation)
-- Params  : $1 rider_id · $2 period_start · $3 period_end
-- ============================================================================

CREATE OR REPLACE VIEW public.v_rider_earnings AS
SELECT d.rider_id,
       d.id                 AS delivery_id,
       d.delivery_number,
       d.order_id,
       d.status             AS delivery_status,
       d.rider_payout_paise_snapshot AS base_payout_paise,
       d.tip_paise,
       COALESCE(d.rider_payout_paise_snapshot, 0) + d.tip_paise AS total_paise,
       (SELECT rpi.rider_payout_id
          FROM public.rider_payout_items rpi
         WHERE rpi.delivery_id = d.id
         LIMIT 1)           AS settled_in_payout,
       d.delivered_at
  FROM public.deliveries d
 WHERE d.rider_id IS NOT NULL;

-- Usage:
-- SELECT *, sum(total_paise) OVER () AS period_total
--   FROM public.v_rider_earnings
--  WHERE rider_id = $1 AND delivered_at >= $2 AND delivered_at < $3
--    AND delivery_status = 'DELIVERED'
--  ORDER BY delivered_at;

-- ----------------------------------------------------------------------------
-- V3: views/get_order_timeline.sql -- Usage: full order story view v_order_timeline (items + status trail + payments + delivery)
-- (body below preserved VERBATIM from supabase/views/get_order_timeline.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- get_order_timeline.sql — VIEW (opt-in reporting): full order story in one scan
-- ============================================================================
-- Truth   : 0004 orders/order_items/history/payments/refunds · 0006 deliveries
--           Reads SNAPSHOTS only (never live menus). Apply: run once in the
--           analytics session (CREATE OR REPLACE VIEW …), read via service_role.
--           NOT a migration — re-running the CREATE is always safe.
-- Clients : C (tracking), V/A (ops views)
-- ============================================================================

CREATE OR REPLACE VIEW public.v_order_timeline AS
SELECT o.id                 AS order_id,
       o.order_number,
       o.status             AS order_status,
       o.payment_status,
       o.total_paise,
       o.created_at         AS ordered_at,
       (SELECT jsonb_agg(
                 jsonb_build_object('item', oi.item_name_snapshot,
                                    'qty', oi.quantity,
                                    'line_total', oi.line_total_paise)
                 ORDER BY oi.created_at)
          FROM public.order_items oi
         WHERE oi.order_id = o.id) AS items,
       (SELECT jsonb_agg(
                 jsonb_build_object('to', h.to_status, 'at', h.created_at,
                                    'by', h.actor_role)
                 ORDER BY h.created_at)
          FROM public.order_status_history h
         WHERE h.order_id = o.id) AS status_trail,
       (SELECT jsonb_agg(
                 jsonb_build_object('status', p.status,
                                    'provider', p.provider,
                                    'amount', p.amount_paise)
                 ORDER BY p.created_at)
          FROM public.payments p
         WHERE p.order_id = o.id) AS payments,
       d.status             AS delivery_status,
       d.rider_id
  FROM public.orders o
  LEFT JOIN public.deliveries d ON d.order_id = o.id;

-- Usage: SELECT * FROM public.v_order_timeline WHERE order_id = $1;

-- ----------------------------------------------------------------------------
-- V4: views/get_dashboard_summary.sql -- Usage: ops pulse view v_dashboard_summary (24h activity + open backlogs, 1 row)
-- (body below preserved VERBATIM from supabase/views/get_dashboard_summary.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- get_dashboard_summary.sql — VIEW (opt-in reporting): ops pulse in one row
-- ============================================================================
-- Truth   : 0001 platform_users · 0003 vendors · 0004 orders/payments ·
--           0005 subscriptions · 0006 deliveries · 0007 tickets/outbox
-- Role    : service_role (admin dashboard API). NOT a migration.
-- Clients : A
-- Windows : trailing 24h activity + current open backlogs.
-- ============================================================================

CREATE OR REPLACE VIEW public.v_dashboard_summary AS
SELECT
  (SELECT count(*) FROM public.orders
    WHERE created_at > now() - interval '24 hours')            AS orders_24h,
  (SELECT count(*) FROM public.payments
    WHERE status = 'SUCCESS'
      AND created_at > now() - interval '24 hours')            AS payments_success_24h,
  (SELECT count(*) FROM public.deliveries
    WHERE status NOT IN ('DELIVERED', 'CANCELLED', 'FAILED'))  AS open_deliveries,
  (SELECT count(*) FROM public.delivery_assignments
    WHERE status = 'OFFERED' AND expires_at > now())           AS live_offers,
  (SELECT count(*) FROM public.subscription_daily_orders
    WHERE service_date = CURRENT_DATE AND status = 'PENDING') AS daily_pending_today,
  (SELECT count(*) FROM public.outbox_events
    WHERE status = 'PENDING')                                 AS outbox_pending,
  (SELECT count(*) FROM public.support_tickets
    WHERE status = 'OPEN')                                    AS open_tickets,
  (SELECT count(*) FROM public.vendor_verifications
    WHERE status = 'PENDING')                                 AS pending_verifications,
  (SELECT count(*) FROM public.vendors
    WHERE status = 'APPROVED' AND is_active)                  AS live_vendors,
  (SELECT count(*) FROM public.riders
    WHERE status = 'ACTIVE' AND is_online)                    AS riders_online;

-- Usage: SELECT * FROM public.v_dashboard_summary;
-- expect: exactly 1 row. Combine with queries/admin/01_get_platform_health_snapshot.sql for
--   the 56-table census + seed footprint on the same admin screen.
