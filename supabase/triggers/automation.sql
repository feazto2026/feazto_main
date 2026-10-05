-- ============================================================================
-- automation.sql -- TRIGGER-PATTERN REFERENCE (truth stays in migrations)
-- ============================================================================
-- Truth   : public.set_updated_at() defined in 0001_identity.sql; per-table
--           BEFORE UPDATE triggers attached in 0001..0007 (re-asserted in 0009).
-- Role    : owner / superuser (DDL). Application code never touches this.
-- Sections: T1 canonical set_updated_at body + attach/audit pattern; T2
--           OPTIONAL outbox pg_notify fast-path (NOT installed by 0001..0009).
--           Bodies preserved VERBATIM.
-- History : consolidated 2026-10-03 from triggers/set_updated_at_trigger.sql +
--           notify_outbox_event_trigger.sql (deleted).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- T1: triggers/set_updated_at_trigger.sql -- Usage: canonical set_updated_at() body + new-table attach pattern + trigger audit
-- (body below preserved VERBATIM from supabase/triggers/set_updated_at_trigger.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- set_updated_at_trigger.sql — TRIGGER REFERENCE (installed by migrations; not re-applied)
-- ============================================================================
-- Truth   : public.set_updated_at() defined in 0001_identity.sql; per-table
--           BEFORE UPDATE triggers attached in 0001..0007 (mutable tables) and
--           re-asserted where needed in 0009. History/audit tables are
--           append-only and carry NO updated_at trigger by design.
-- Purpose : single canonical copy of the function body + the re-attach pattern
--           for when a NEW 0010 migration adds a mutable table.
-- Role    : owner / superuser (DDL). Application code never touches this.
-- ============================================================================

-- Canonical body (must stay identical to 0001_identity.sql) --------------------
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

-- Attach pattern for a new mutable table <t> (in the 0010 migration itself): --
-- DROP TRIGGER IF EXISTS trg_<t>_updated_at ON public.<t>;
-- CREATE TRIGGER trg_<t>_updated_at BEFORE UPDATE ON public.<t>
--   FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- AUDIT: every mutable table has its trigger (expect: zero rows) --------------
-- SELECT t AS missing_trigger FROM (VALUES
--   ('platform_users'),('roles'),('permissions'),
--   ('customer_profiles'),('addresses'),
--   ('vendors'),('vendor_documents'),('menus'),('menu_categories'),('menu_items'),
--   ('slots'),('vendor_slots'),('service_zones'),('menu_item_availability'),
--   ('coupons'),('carts'),('cart_items'),('orders'),('order_items'),
--   ('payments'),('refunds'),
--   ('meal_plans'),('subscriptions'),('subscription_schedules'),
--   ('riders'),('rider_documents'),('rider_availability'),
--   ('deliveries'),('delivery_assignments'),
--   ('support_tickets'),('notifications'),('notification_preferences'),
--   ('vendor_payouts'),('rider_payouts')) AS v(t)
-- EXCEPT
-- SELECT c.relname FROM pg_trigger tg
-- JOIN pg_class c ON c.oid = tg.tgrelid
-- WHERE tg.tgname LIKE 'trg_%_updated_at' AND NOT tg.tgisinternal;

-- ----------------------------------------------------------------------------
-- T2: triggers/notify_outbox_event_trigger.sql -- Usage: optional pg_notify fast-path for the outbox relay (poller remains source of truth)
-- (body below preserved VERBATIM from supabase/triggers/notify_outbox_event_trigger.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- notify_outbox_event_trigger.sql — OPTIONAL pg_notify fast-path for the outbox relay
-- ============================================================================
-- Truth   : outbox_events table + ix_outbox_pending in 0007/0008. The PRIMARY
--           relay is backend POLLING of ix_outbox_pending (status='PENDING') —
--           this trigger is an OPTIONAL wake-up hint so the poller reacts fast.
-- Warning : NOTIFY is best-effort (payloads must stay < 8kB; notifications can
--           be lost on failover — the poller remains the source of truth).
--           Do NOT put business logic here; Spring Boot owns state machines.
-- Role    : owner / superuser (DDL). Consumers dedupe on idempotency_key.
-- Status  : NOT installed by 0001..0009 — apply deliberately (or keep polling
--           only) and record the decision in an ADR.
-- ============================================================================

CREATE OR REPLACE FUNCTION public.notify_outbox_event()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  PERFORM pg_notify('outbox_events',
    jsonb_build_object('id', NEW.id,
                       'aggregate_type', NEW.aggregate_type,
                       'aggregate_id', NEW.aggregate_id,
                       'event_type', NEW.event_type,
                       'idempotency_key', NEW.idempotency_key)::text);
  RETURN NEW;
END;
$$;

-- To enable (deliberate step, not a migration):
-- DROP TRIGGER IF EXISTS trg_outbox_notify ON public.outbox_events;
-- CREATE TRIGGER trg_outbox_notify AFTER INSERT ON public.outbox_events
--   FOR EACH ROW EXECUTE FUNCTION public.notify_outbox_event();
-- Backend: LISTEN outbox_events → on wake, SELECT … WHERE status='PENDING'
--   ORDER BY created_at LIMIT <batch> FOR UPDATE SKIP LOCKED (ix_outbox_pending).

-- To disable (back to pure polling):
-- DROP TRIGGER IF EXISTS trg_outbox_notify ON public.outbox_events;
-- DROP FUNCTION IF EXISTS public.notify_outbox_event();
