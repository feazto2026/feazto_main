-- ============================================================================
-- V4__supabase_fixes.sql — Backend mirror of supabase/migrations/0009_fixes.sql
-- (backend-relevant subset; storage buckets + RLS deny-all live in Supabase only
-- and are intentionally NOT duplicated here — backend connects with service_role).
-- Includes: fk_orders_schedule + ix_orders_schedule, payout net-consistency
-- CHECKs + payout-item guards, and the 0009 hot-path indexes on backend tables.
-- Every statement is re-runnable (IF NOT EXISTS / guarded DO blocks).
-- ddl-auto stays `validate`: entities already match these constraints.
-- ============================================================================

-- ---- 1. Missing forward FK: orders.subscription_schedule_id -> subscription_schedules ----
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_orders_schedule') THEN
    ALTER TABLE public.orders
      ADD CONSTRAINT fk_orders_schedule
      FOREIGN KEY (subscription_schedule_id)
      REFERENCES public.subscription_schedules (id) ON DELETE SET NULL;
  END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS ix_orders_schedule
  ON public.orders (subscription_schedule_id)
  WHERE subscription_schedule_id IS NOT NULL;

-- ---- 2. Payout consistency CHECKs (finance integrity, mirrors orders_total_consistency_check) ----
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vendor_payouts_net_check') THEN
    ALTER TABLE public.vendor_payouts
      ADD CONSTRAINT vendor_payouts_net_check
      CHECK (net_amount_paise = gross_amount_paise
             - commission_deducted_paise
             - refunds_deducted_paise
             + adjustments_paise
             AND net_amount_paise >= 0
             AND gross_amount_paise >= commission_deducted_paise + refunds_deducted_paise);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'rider_payouts_net_check') THEN
    ALTER TABLE public.rider_payouts
      ADD CONSTRAINT rider_payouts_net_check
      CHECK (net_amount_paise = gross_amount_paise + adjustments_paise
             AND net_amount_paise >= 0);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vendor_payout_items_amount_check') THEN
    ALTER TABLE public.vendor_payout_items
      ADD CONSTRAINT vendor_payout_items_amount_check
      CHECK (amount_paise >= 0);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'rider_payout_items_amount_check') THEN
    ALTER TABLE public.rider_payout_items
      ADD CONSTRAINT rider_payout_items_amount_check
      CHECK (amount_paise >= 0);
  END IF;
END;
$$;

-- ---- 3. Missing hot-path indexes (0009 §3, backend tables only) ----
CREATE INDEX IF NOT EXISTS ix_vendor_documents_vendor
  ON public.vendor_documents (vendor_id, status);
CREATE INDEX IF NOT EXISTS ix_rider_documents_rider
  ON public.rider_documents (rider_id, status);
CREATE INDEX IF NOT EXISTS ix_subscription_status_history_sub
  ON public.subscription_status_history (subscription_id, created_at);
CREATE INDEX IF NOT EXISTS ix_vendor_payout_items_payout
  ON public.vendor_payout_items (payout_id);
CREATE INDEX IF NOT EXISTS ix_vendor_payout_items_order
  ON public.vendor_payout_items (order_id);
CREATE INDEX IF NOT EXISTS ix_rider_payout_items_payout
  ON public.rider_payout_items (payout_id);
CREATE INDEX IF NOT EXISTS ix_rider_payout_items_delivery
  ON public.rider_payout_items (delivery_id);
CREATE INDEX IF NOT EXISTS ix_coupons_validity
  ON public.coupons (valid_from, valid_to) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS ix_menu_item_avail_slot_date
  ON public.menu_item_availability (vendor_slot_id, service_date);
CREATE INDEX IF NOT EXISTS ix_support_tickets_order
  ON public.support_tickets (order_id) WHERE order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_support_tickets_delivery
  ON public.support_tickets (delivery_id) WHERE delivery_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_notifications_dedupe
  ON public.notifications (dedupe_key) WHERE dedupe_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_commissions_status
  ON public.commissions (status);

-- ---- 4. Parity bookkeeping: record 0009 so V2 parity gate covers it ----
INSERT INTO public.supabase_migration_hashes (migration, sha256) VALUES
  ('0009_fixes','parity:see-supabase-migrations-0009_fixes.sql')
ON CONFLICT (migration) DO NOTHING;
