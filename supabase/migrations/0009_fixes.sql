-- ============================================================================
-- Codewild Food Platform — Migration 0009: Consistency fixes + missing guards
-- ============================================================================
-- Purpose: close audit gaps found against 0001..0008 without rebuilding.
--   * Attach missing FK: orders.subscription_schedule_id -> subscription_schedules
--   * Add payout net-consistency CHECKs (mirrors orders_total_consistency_check)
--     + payout-item amount guards
--   * Add missing hot-path indexes (documents, payout items, history, zones)
--   * Harden storage bucket flags (idempotent correction, not just DO NOTHING)
--   * Re-assert RLS deny-all on all 56 business tables (additive, safe)
--
-- Safety: every statement is re-runnable (IF NOT EXISTS / guarded DO blocks /
-- DROP IF EXISTS). No table dropped/renamed. service_role bypasses RLS so
-- backend behaviour is unaffected. Apply AFTER 0001..0008, BEFORE seed.
-- Compatibility: ensures anon/authenticated roles exist (no-op on Supabase;
-- required for vanilla PG / WASM verification) and skips storage setup when
-- the storage schema is absent.
-- ============================================================================

-- Compatibility preamble (same as 0008): no-op on Supabase.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
    CREATE ROLE anon WITH NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
    CREATE ROLE authenticated WITH NOLOGIN;
  END IF;
END;
$$;

-- ---- 1. Missing forward FK (0004 column, 0005 missed this one) ------------------
-- orders.subscription_schedule_id is nullable UUID without constraint.
-- Guarded so re-run is a no-op.
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

-- Helpful lookup index for the new FK (orders filtered by schedule).
CREATE INDEX IF NOT EXISTS ix_orders_schedule
  ON public.orders (subscription_schedule_id)
  WHERE subscription_schedule_id IS NOT NULL;

-- ---- 2. Payout consistency CHECKs (finance integrity, §11) -----------------------
-- Vendor payout: net = gross - commission - refunds + adjustments, net >= 0.
-- Rider payout:  net = gross + adjustments, net >= 0.
-- Guarded ADD CONSTRAINT so re-run is safe; existing rows are empty/valid.
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

-- ---- 3. Missing hot-path indexes (0008 covered ~45; these close gaps) ------------
-- Document lookups (KYC screens, admin verification queues).
CREATE INDEX IF NOT EXISTS ix_vendor_documents_vendor
  ON public.vendor_documents (vendor_id, status);
CREATE INDEX IF NOT EXISTS ix_rider_documents_rider
  ON public.rider_documents (rider_id, status);

-- History scans (timeline APIs paginate by created_at).
CREATE INDEX IF NOT EXISTS ix_subscription_status_history_sub
  ON public.subscription_status_history (subscription_id, created_at);

-- Payout drill-downs (payout detail pages join items -> orders/deliveries).
CREATE INDEX IF NOT EXISTS ix_vendor_payout_items_payout
  ON public.vendor_payout_items (payout_id);
CREATE INDEX IF NOT EXISTS ix_vendor_payout_items_order
  ON public.vendor_payout_items (order_id);
CREATE INDEX IF NOT EXISTS ix_rider_payout_items_payout
  ON public.rider_payout_items (payout_id);
CREATE INDEX IF NOT EXISTS ix_rider_payout_items_delivery
  ON public.rider_payout_items (delivery_id);

-- Coupon validity scan (checkout validates active window).
CREATE INDEX IF NOT EXISTS ix_coupons_validity
  ON public.coupons (valid_from, valid_to) WHERE is_active = true;

-- Availability by slot+date (vendor calendar, capacity checks).
CREATE INDEX IF NOT EXISTS ix_menu_item_avail_slot_date
  ON public.menu_item_availability (vendor_slot_id, service_date);

-- Ticket linkage (support detail shows related order/delivery).
CREATE INDEX IF NOT EXISTS ix_support_tickets_order
  ON public.support_tickets (order_id) WHERE order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_support_tickets_delivery
  ON public.support_tickets (delivery_id) WHERE delivery_id IS NOT NULL;

-- Notification dedupe + preference fast path.
CREATE INDEX IF NOT EXISTS ix_notifications_dedupe
  ON public.notifications (dedupe_key) WHERE dedupe_key IS NOT NULL;

-- Commission settlement scan (finance settles ACCRUED -> SETTLED batches).
CREATE INDEX IF NOT EXISTS ix_commissions_status
  ON public.commissions (status);

-- ---- 4. Storage bucket flag hardening -------------------------------------------
-- 0008 used ON CONFLICT DO NOTHING (safe but never repairs drift).
-- This upsert corrects the public flag if an operator flipped it manually.
-- Guard: skip when storage schema is absent (vanilla PG verification).
DO $$
BEGIN
  IF to_regclass('storage.buckets') IS NULL
     OR to_regclass('storage.objects') IS NULL THEN
    RAISE NOTICE 'storage schema absent (vanilla PG verification): skipping bucket hardening';
    RETURN;
  END IF;

  INSERT INTO storage.buckets (id, name, public) VALUES
    ('vendor-profile-images', 'vendor-profile-images', true),
    ('menu-images',           'menu-images',           true),
    ('customer-avatars',      'customer-avatars',      true),
    ('vendor-documents',      'vendor-documents',      false),
    ('rider-documents',       'rider-documents',       false),
    ('delivery-proofs',       'delivery-proofs',       false),
    ('support-attachments',   'support-attachments',   false)
  ON CONFLICT (id) DO UPDATE SET public = EXCLUDED.public;

  DROP POLICY IF EXISTS "public_bucket_read" ON storage.objects;
  CREATE POLICY "public_bucket_read"
    ON storage.objects FOR SELECT TO anon, authenticated
    USING (bucket_id IN ('vendor-profile-images', 'menu-images', 'customer-avatars'));
END;
$$;

-- ---- 5. Re-assert RLS deny-all (additive; repairs any manually dropped policy) --
DO $$
DECLARE
  t text;
  business_tables text[] := ARRAY[
    'roles','permissions','role_permissions','platform_users','user_roles',
    'customer_profiles','addresses',
    'regions','cuisines','vendors','vendor_regions','vendor_cuisines',
    'vendor_verifications','vendor_documents','menus','menu_categories',
    'menu_items','slots','vendor_slots','service_zones','vendor_service_zones',
    'menu_item_availability',
    'coupons','coupon_usages','carts','cart_items','orders','order_items',
    'order_status_history','payments','payment_attempts','refunds',
    'meal_plans','subscriptions','subscription_schedules',
    'subscription_daily_orders','subscription_status_history',
    'riders','rider_documents','rider_availability','deliveries',
    'delivery_assignments','delivery_status_history',
    'support_tickets','support_ticket_messages','audit_logs','notifications',
    'notification_preferences','reviews',
    'commissions','vendor_payouts','vendor_payout_items',
    'rider_payouts','rider_payout_items',
    'outbox_events','idempotency_keys'
  ];
BEGIN
  FOREACH t IN ARRAY business_tables LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('DROP POLICY IF EXISTS deny_all_client_access ON public.%I', t);
    EXECUTE format(
      'CREATE POLICY deny_all_client_access ON public.%I '
      'FOR ALL TO anon, authenticated USING (false) WITH CHECK (false)', t);
  END LOOP;
END;
$$;

COMMENT ON CONSTRAINT fk_orders_schedule ON public.orders IS
  'Links subscription-generated orders to the firing schedule row; SET NULL preserves order history if schedule edited.';
