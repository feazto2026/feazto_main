-- ============================================================================
-- Codewild Food Platform — Migration 0008: Indexes, RLS, Storage
-- ============================================================================
-- 1. Performance indexes for high-volume queries (lookup + list + dispatch).
-- 2. Row Level Security: business tables deny ALL direct client access.
--    RLS is ENABLED on every business table (56) and the only policies created
--    explicit DENY-ALL policies for anon/authenticated. The Spring Boot backend
--    connects with the service_role key, which bypasses RLS. Any future
--    client-direct access MUST add an explicit allow-policy + ADR-010 review.
-- 3. Storage buckets (public assets vs private documents).
-- ============================================================================

-- Extensions for search ---------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Compatibility preamble ------------------------------------------------------
-- Supabase projects always provide anon/authenticated roles + storage schema.
-- Vanilla Postgres (local psql / WASM verification) may lack them; create
-- minimally so this migration stays re-runnable everywhere. No-op on Supabase.
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

-- ============================ INDEXES ============================================

-- Identity
CREATE INDEX IF NOT EXISTS ix_platform_users_role
  ON public.platform_users (primary_role_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_platform_users_status
  ON public.platform_users (account_status);
CREATE INDEX IF NOT EXISTS ix_user_roles_role ON public.user_roles (role_id);

-- Customer
CREATE INDEX IF NOT EXISTS ix_customer_profiles_user
  ON public.customer_profiles (user_id);
CREATE INDEX IF NOT EXISTS ix_addresses_customer
  ON public.addresses (customer_id) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS ix_addresses_city_pin
  ON public.addresses (city, postal_code) WHERE is_active = true;
-- Exactly one default address per customer.
CREATE UNIQUE INDEX IF NOT EXISTS uq_addresses_single_default
  ON public.addresses (customer_id) WHERE is_default = true AND is_active = true;

-- Marketplace: discovery + serviceability (highest read volume)
CREATE INDEX IF NOT EXISTS ix_vendors_status_active
  ON public.vendors (status, city) WHERE deleted_at IS NULL AND is_active = true;
CREATE INDEX IF NOT EXISTS ix_vendors_city_status
  ON public.vendors (city, status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_vendors_native_region
  ON public.vendors (native_region_id) WHERE deleted_at IS NULL AND status = 'APPROVED';
CREATE INDEX IF NOT EXISTS ix_vendors_cuisine_tags
  ON public.vendors USING gin (cuisine_tags);
CREATE INDEX IF NOT EXISTS ix_vendors_name_trgm
  ON public.vendors USING gin (kitchen_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS ix_vendor_regions_region ON public.vendor_regions (region_id);
CREATE INDEX IF NOT EXISTS ix_vendor_cuisines_cuisine ON public.vendor_cuisines (cuisine_id);
CREATE INDEX IF NOT EXISTS ix_vendor_verifications_vendor
  ON public.vendor_verifications (vendor_id, status);
-- One open verification request per vendor+type.
CREATE UNIQUE INDEX IF NOT EXISTS uq_vendor_verifications_single_open
  ON public.vendor_verifications (vendor_id, verification_type)
  WHERE status IN ('PENDING', 'CHANGES_REQUESTED');
CREATE INDEX IF NOT EXISTS ix_menus_vendor ON public.menus (vendor_id) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS ix_menu_categories_menu ON public.menu_categories (menu_id);
CREATE INDEX IF NOT EXISTS ix_menu_items_vendor_avail
  ON public.menu_items (vendor_id, is_available) WHERE deleted_at IS NULL AND is_active = true;
CREATE INDEX IF NOT EXISTS ix_menu_items_category ON public.menu_items (category_id);
CREATE INDEX IF NOT EXISTS ix_menu_items_name_trgm
  ON public.menu_items USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS ix_menu_items_tags
  ON public.menu_items USING gin (cuisine_tags);
CREATE INDEX IF NOT EXISTS ix_menu_items_region_tags
  ON public.menu_items USING gin (region_tags);
CREATE INDEX IF NOT EXISTS ix_vendor_slots_vendor ON public.vendor_slots (vendor_id, is_available);
CREATE INDEX IF NOT EXISTS ix_service_zones_city
  ON public.service_zones (city) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS ix_service_zones_pins
  ON public.service_zones USING gin (postal_codes) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS ix_vendor_service_zones_zone
  ON public.vendor_service_zones (service_zone_id) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS ix_menu_item_avail_lookup
  ON public.menu_item_availability (menu_item_id, service_date) WHERE is_available = true;

-- Commerce: order/payment hot paths
CREATE INDEX IF NOT EXISTS ix_orders_customer_created
  ON public.orders (customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_orders_vendor_status
  ON public.orders (vendor_id, status);
CREATE INDEX IF NOT EXISTS ix_orders_vendor_created
  ON public.orders (vendor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_orders_status_service
  ON public.orders (status, service_date);
CREATE INDEX IF NOT EXISTS ix_orders_subscription ON public.orders (subscription_id)
  WHERE subscription_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_order_items_order ON public.order_items (order_id);
CREATE INDEX IF NOT EXISTS ix_order_history_order_created
  ON public.order_status_history (order_id, created_at);
CREATE INDEX IF NOT EXISTS ix_payments_order ON public.payments (order_id);
CREATE INDEX IF NOT EXISTS ix_payments_subscription ON public.payments (subscription_id)
  WHERE subscription_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_payments_provider_ref ON public.payments (provider, provider_payment_id);
CREATE INDEX IF NOT EXISTS ix_payments_status ON public.payments (status);
CREATE INDEX IF NOT EXISTS ix_payment_attempts_payment ON public.payment_attempts (payment_id);
CREATE INDEX IF NOT EXISTS ix_refunds_order ON public.refunds (order_id);
CREATE INDEX IF NOT EXISTS ix_refunds_status ON public.refunds (status);
CREATE INDEX IF NOT EXISTS ix_carts_customer ON public.carts (customer_id, status);
-- One ACTIVE cart per (customer, vendor): enforces single-vendor cart server rule.
CREATE UNIQUE INDEX IF NOT EXISTS uq_carts_single_active
  ON public.carts (customer_id, vendor_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS ix_cart_items_cart ON public.cart_items (cart_id);
CREATE INDEX IF NOT EXISTS ix_coupon_usages_customer_coupon
  ON public.coupon_usages (customer_id, coupon_id);

-- Subscription: daily generator scan
CREATE INDEX IF NOT EXISTS ix_subscriptions_vendor_status
  ON public.subscriptions (vendor_id, status);
CREATE INDEX IF NOT EXISTS ix_subscriptions_customer_status
  ON public.subscriptions (customer_id, status);
CREATE INDEX IF NOT EXISTS ix_subscriptions_active_window
  ON public.subscriptions (start_date, end_date) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS ix_daily_orders_due
  ON public.subscription_daily_orders (service_date, status);
CREATE INDEX IF NOT EXISTS ix_daily_orders_subscription
  ON public.subscription_daily_orders (subscription_id, service_date);

-- Fulfillment: dispatch + tracking
CREATE INDEX IF NOT EXISTS ix_riders_status_online
  ON public.riders (status, is_online, city);
CREATE INDEX IF NOT EXISTS ix_rider_availability_rider
  ON public.rider_availability (rider_id, weekday) WHERE is_available = true;
CREATE INDEX IF NOT EXISTS ix_deliveries_rider_status
  ON public.deliveries (rider_id, status) WHERE rider_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_deliveries_status ON public.deliveries (status);
CREATE INDEX IF NOT EXISTS ix_assignments_delivery
  ON public.delivery_assignments (delivery_id, status);
CREATE INDEX IF NOT EXISTS ix_assignments_rider_status
  ON public.delivery_assignments (rider_id, status);
CREATE INDEX IF NOT EXISTS ix_delivery_history_delivery_created
  ON public.delivery_status_history (delivery_id, created_at);

-- Operations / finance
CREATE INDEX IF NOT EXISTS ix_tickets_status_priority
  ON public.support_tickets (status, priority);
CREATE INDEX IF NOT EXISTS ix_tickets_assignee
  ON public.support_tickets (assigned_to, status) WHERE assigned_to IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_tickets_requester
  ON public.support_tickets (requester_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_ticket_messages_ticket
  ON public.support_ticket_messages (ticket_id, created_at);
CREATE INDEX IF NOT EXISTS ix_audit_entity
  ON public.audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_actor_created
  ON public.audit_logs (actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_notifications_recipient
  ON public.notifications (recipient_user_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_reviews_vendor
  ON public.reviews (vendor_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_reviews_rider
  ON public.reviews (rider_id) WHERE rider_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_commissions_vendor_status
  ON public.commissions (vendor_id, status);
CREATE INDEX IF NOT EXISTS ix_vendor_payouts_vendor_status
  ON public.vendor_payouts (vendor_id, status);
CREATE INDEX IF NOT EXISTS ix_rider_payouts_rider_status
  ON public.rider_payouts (rider_id, status);
-- Outbox publisher scan: pending events due for relay, oldest first.
CREATE INDEX IF NOT EXISTS ix_outbox_pending
  ON public.outbox_events (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS ix_outbox_aggregate
  ON public.outbox_events (aggregate_type, aggregate_id, created_at DESC);
-- Idempotency expiry sweep.
CREATE INDEX IF NOT EXISTS ix_idempotency_expiry ON public.idempotency_keys (expires_at);

-- ============================ RLS ================================================
-- "Database is not the API" (§3.2, ADR-010): no client receives direct write
-- privileges on business tables. Enable RLS everywhere and install explicit
-- DENY-ALL policies for anon/authenticated. service_role bypasses RLS, so the
-- Spring Boot backend is unaffected.

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
    -- FOR ALL + USING(false) + WITH CHECK(false): denies SELECT/INSERT/
    -- UPDATE/DELETE for anon + authenticated. service_role bypasses RLS.
    EXECUTE format(
      'CREATE POLICY deny_all_client_access ON public.%I '
      'FOR ALL TO anon, authenticated USING (false) WITH CHECK (false)', t);
  END LOOP;
END;
$$;

-- ============================ STORAGE =============================================
-- Buckets: public (asset delivery) vs private (KYC / proofs / attachments).
-- Access model: backend (service_role) mints signed URLs for private objects;
-- only public-bucket READS are allowed for anon/authenticated. No client
-- insert/update/delete policies exist anywhere -> client writes denied.
-- (Supabase serves storage.objects with RLS already enabled.)
-- Guard: storage schema exists on Supabase; on vanilla Postgres verification
-- without storage, this block raises NOTICE and skips (business tables above
-- still verified). Never create/overwrite the real storage schema here.

DO $$
BEGIN
  IF to_regclass('storage.buckets') IS NULL
     OR to_regclass('storage.objects') IS NULL THEN
    RAISE NOTICE 'storage schema absent (vanilla PG verification): skipping bucket/policy setup';
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
  ON CONFLICT (id) DO NOTHING;

  DROP POLICY IF EXISTS "public_bucket_read" ON storage.objects;
  CREATE POLICY "public_bucket_read"
    ON storage.objects FOR SELECT TO anon, authenticated
    USING (bucket_id IN ('vendor-profile-images', 'menu-images', 'customer-avatars'));
END;
$$;

-- NOTE: intentionally NO INSERT / UPDATE / DELETE policies on storage.objects
-- for anon/authenticated. Uploads go through the backend, which validates file
-- type/size/ownership and writes with service_role (or mints a short-lived
-- signed upload URL server-side).

DO $$
BEGIN
  IF to_regclass('storage.objects') IS NOT NULL
     AND EXISTS (SELECT 1 FROM pg_policy p
                 JOIN pg_class c ON c.oid = p.polrelid
                 WHERE c.relname = 'objects' AND p.polname = 'public_bucket_read') THEN
    EXECUTE $c$COMMENT ON POLICY "public_bucket_read" ON storage.objects IS
      'Public asset reads only. All client writes denied; uploads via backend (service_role).'$c$;
  END IF;
END;
$$;
