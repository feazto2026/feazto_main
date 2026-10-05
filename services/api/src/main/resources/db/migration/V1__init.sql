-- ============================================================================
-- V1__init.sql — Supabase parity baseline (mirrors supabase/migrations/0001-0007
-- tables + 0008 performance indexes; RLS/storage live in Supabase 0008 and are
-- intentionally NOT duplicated here — backend connects with service_role).
-- Any drift from supabase/migrations/* fails startup via V2 parity check
-- (supabase_migration_hashes + SupabaseParityVerifier).
-- UUID PKs everywhere. Money in paise (bigint). See docs/database/schema.md.
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "citext";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN NEW.updated_at = now(); RETURN NEW; END; $$;

-- ================= 0001 identity =================
CREATE TABLE IF NOT EXISTS public.roles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code text NOT NULL UNIQUE CHECK (code = upper(code)),
  name text NOT NULL, description text NOT NULL DEFAULT '',
  is_system boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.permissions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code text NOT NULL UNIQUE CHECK (code = upper(code)),
  module text NOT NULL DEFAULT 'PLATFORM',
  description text NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.role_permissions (
  role_id uuid NOT NULL REFERENCES public.roles (id) ON DELETE CASCADE,
  permission_id uuid NOT NULL REFERENCES public.permissions (id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (role_id, permission_id));
CREATE TABLE IF NOT EXISTS public.platform_users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  auth_user_id uuid UNIQUE, phone text UNIQUE, email citext UNIQUE,
  display_name text NOT NULL DEFAULT '',
  primary_role_id uuid REFERENCES public.roles (id) ON DELETE RESTRICT,
  account_status text NOT NULL DEFAULT 'ACTIVE'
    CHECK (account_status IN ('PENDING_VERIFICATION','ACTIVE','SUSPENDED','DEACTIVATED')),
  is_phone_verified boolean NOT NULL DEFAULT false,
  last_login_at timestamptz, metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz,
  CONSTRAINT platform_users_contact_check
    CHECK (phone IS NOT NULL OR email IS NOT NULL OR auth_user_id IS NOT NULL));
CREATE TABLE IF NOT EXISTS public.user_roles (
  user_id uuid NOT NULL REFERENCES public.platform_users (id) ON DELETE CASCADE,
  role_id uuid NOT NULL REFERENCES public.roles (id) ON DELETE RESTRICT,
  granted_by uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  granted_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, role_id));
DROP TRIGGER IF EXISTS trg_roles_updated_at ON public.roles;
CREATE TRIGGER trg_roles_updated_at BEFORE UPDATE ON public.roles FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
DROP TRIGGER IF EXISTS trg_permissions_updated_at ON public.permissions;
CREATE TRIGGER trg_permissions_updated_at BEFORE UPDATE ON public.permissions FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
DROP TRIGGER IF EXISTS trg_platform_users_updated_at ON public.platform_users;
CREATE TRIGGER trg_platform_users_updated_at BEFORE UPDATE ON public.platform_users FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
INSERT INTO public.roles (code,name,description,is_system) VALUES
  ('CUSTOMER','Customer','Purchases home-made food',true),
  ('VENDOR','Vendor/Home Cook','Prepares and sells food',true),
  ('RIDER','Rider','Delivery partner',true),
  ('ADMIN','Admin','Legacy generic admin (avoid for new grants)',true),
  ('SUPER_ADMIN','Super Admin','Full platform control',true),
  ('OPS_ADMIN','Operations Admin','Marketplace + fulfillment operations',true),
  ('SUPPORT_ADMIN','Support Admin','Customer/vendor/rider support',true),
  ('FINANCE_ADMIN','Finance Admin','Payments, refunds, payouts',true)
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,description=EXCLUDED.description,updated_at=now();
INSERT INTO public.permissions (code,module,description) VALUES
  ('VENDOR_VIEW','MARKETPLACE','View vendor profiles'),
  ('VENDOR_APPROVE','MARKETPLACE','Approve / reject / request changes on vendors'),
  ('VENDOR_SUSPEND','MARKETPLACE','Suspend / reactivate vendors'),
  ('MENU_VIEW','MARKETPLACE','Inspect menus, items, slots, capacity'),
  ('MENU_MANAGE','MARKETPLACE','Modify menu on behalf of vendor (audited)'),
  ('CUSTOMER_VIEW','CUSTOMER','View customer profiles and history'),
  ('CUSTOMER_SUSPEND','CUSTOMER','Suspend / reactivate customer accounts'),
  ('ORDER_VIEW','COMMERCE','View orders and timelines'),
  ('ORDER_CANCEL','COMMERCE','Cancel orders per policy (audited)'),
  ('ORDER_REFUND','COMMERCE','Issue / approve refunds (audited)'),
  ('SUBSCRIPTION_MANAGE','SUBSCRIPTION','Pause / cancel / fix subscriptions (audited)'),
  ('RIDER_VIEW','FULFILLMENT','View rider profiles and deliveries'),
  ('RIDER_APPROVE','FULFILLMENT','Approve rider onboarding'),
  ('RIDER_SUSPEND','FULFILLMENT','Suspend / reactivate riders'),
  ('DELIVERY_MANAGE','FULFILLMENT','Reassign / escalate deliveries'),
  ('FINANCE_VIEW','FINANCE','View payments, commissions, payouts'),
  ('PAYOUT_MANAGE','FINANCE','Schedule / approve payouts (audited)'),
  ('SUPPORT_VIEW','OPERATIONS','View support tickets'),
  ('SUPPORT_ASSIGN','OPERATIONS','Assign / resolve support tickets'),
  ('AUDIT_VIEW','OPERATIONS','Read audit logs'),
  ('SETTINGS_MANAGE','OPERATIONS','Change zones, slots, platform settings (audited)')
ON CONFLICT (code) DO UPDATE SET module=EXCLUDED.module,description=EXCLUDED.description,updated_at=now();
INSERT INTO public.role_permissions (role_id,permission_id)
SELECT r.id,p.id FROM public.roles r JOIN public.permissions p ON (
  (r.code='SUPER_ADMIN')
  OR (r.code='OPS_ADMIN' AND p.code IN ('VENDOR_VIEW','VENDOR_APPROVE','VENDOR_SUSPEND','MENU_VIEW','ORDER_VIEW','ORDER_CANCEL','SUBSCRIPTION_MANAGE','RIDER_VIEW','RIDER_APPROVE','RIDER_SUSPEND','DELIVERY_MANAGE','CUSTOMER_VIEW','SUPPORT_VIEW','SUPPORT_ASSIGN'))
  OR (r.code='FINANCE_ADMIN' AND p.code IN ('FINANCE_VIEW','PAYOUT_MANAGE','ORDER_VIEW','ORDER_REFUND','AUDIT_VIEW'))
  OR (r.code='SUPPORT_ADMIN' AND p.code IN ('SUPPORT_VIEW','SUPPORT_ASSIGN','ORDER_VIEW','CUSTOMER_VIEW','VENDOR_VIEW','RIDER_VIEW'))
  OR (r.code='ADMIN' AND p.code IN ('VENDOR_VIEW','ORDER_VIEW','RIDER_VIEW','CUSTOMER_VIEW','SUPPORT_VIEW')))
ON CONFLICT DO NOTHING;

-- ================= 0002 customer =================
CREATE TABLE IF NOT EXISTS public.customer_profiles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL UNIQUE REFERENCES public.platform_users (id) ON DELETE CASCADE,
  full_name text NOT NULL DEFAULT '', avatar_url text,
  hometown_region_id uuid,
  preferred_region_ids uuid[] NOT NULL DEFAULT '{}',
  preferred_cuisines text[] NOT NULL DEFAULT '{}',
  dietary_preferences text[] NOT NULL DEFAULT '{}',
  preferred_language text NOT NULL DEFAULT 'en',
  date_of_birth date, is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.addresses (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id uuid NOT NULL REFERENCES public.customer_profiles (id) ON DELETE CASCADE,
  label text NOT NULL DEFAULT 'Home',
  address_type text NOT NULL DEFAULT 'HOME' CHECK (address_type IN ('HOME','WORK','OTHER')),
  house_flat text NOT NULL DEFAULT '', street text NOT NULL DEFAULT '',
  landmark text, area text NOT NULL DEFAULT '',
  city text NOT NULL DEFAULT '', state text NOT NULL DEFAULT '',
  postal_code text NOT NULL DEFAULT '',
  latitude double precision, longitude double precision,
  delivery_instructions text,
  is_default boolean NOT NULL DEFAULT false,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT addresses_geo_pair_check CHECK ((latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL)),
  CONSTRAINT addresses_lat_range_check CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
  CONSTRAINT addresses_lng_range_check CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180)));
DROP TRIGGER IF EXISTS trg_customer_profiles_updated_at ON public.customer_profiles;
CREATE TRIGGER trg_customer_profiles_updated_at BEFORE UPDATE ON public.customer_profiles FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
DROP TRIGGER IF EXISTS trg_addresses_updated_at ON public.addresses;
CREATE TRIGGER trg_addresses_updated_at BEFORE UPDATE ON public.addresses FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- ================= 0003 marketplace =================
CREATE TABLE IF NOT EXISTS public.regions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code text NOT NULL UNIQUE CHECK (code = upper(code)),
  name text NOT NULL,
  kind text NOT NULL CHECK (kind IN ('STATE','DISTRICT','CITY','CULTURAL_REGION')),
  state text, district text, city text,
  parent_id uuid REFERENCES public.regions (id) ON DELETE SET NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.cuisines (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code text NOT NULL UNIQUE CHECK (code = upper(code)),
  name text NOT NULL, category text NOT NULL DEFAULT 'REGIONAL',
  description text NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.vendors (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL UNIQUE REFERENCES public.platform_users (id) ON DELETE RESTRICT,
  kitchen_name text NOT NULL, description text NOT NULL DEFAULT '',
  phone text, email citext, profile_image_url text, cover_image_url text,
  address_line text NOT NULL DEFAULT '', area text NOT NULL DEFAULT '',
  city text NOT NULL DEFAULT '', state text NOT NULL DEFAULT '',
  postal_code text NOT NULL DEFAULT '',
  latitude double precision, longitude double precision,
  native_region_id uuid REFERENCES public.regions (id) ON DELETE SET NULL,
  cuisine_tags text[] NOT NULL DEFAULT '{}',
  status text NOT NULL DEFAULT 'DRAFT'
    CHECK (status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','ACTION_REQUIRED','APPROVED','SUSPENDED','REJECTED','DEACTIVATED')),
  commission_bps integer NOT NULL DEFAULT 1500 CHECK (commission_bps >= 0 AND commission_bps <= 10000),
  rating_avg numeric(3,2) NOT NULL DEFAULT 0.00 CHECK (rating_avg >= 0 AND rating_avg <= 5),
  rating_count integer NOT NULL DEFAULT 0 CHECK (rating_count >= 0),
  is_active boolean NOT NULL DEFAULT false,
  service_radius_km numeric(5,2) NOT NULL DEFAULT 5.00 CHECK (service_radius_km > 0),
  preparation_capacity_per_slot integer NOT NULL DEFAULT 20 CHECK (preparation_capacity_per_slot > 0),
  slot_buffer_minutes integer NOT NULL DEFAULT 30 CHECK (slot_buffer_minutes >= 0),
  fssai_license_no text, payout_account_ref text,
  terms_accepted_at timestamptz, approved_at timestamptz,
  approved_by uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  suspension_reason text, deleted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT vendors_geo_pair_check CHECK ((latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL)));
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_customer_hometown_region') THEN
  ALTER TABLE public.customer_profiles ADD CONSTRAINT fk_customer_hometown_region FOREIGN KEY (hometown_region_id) REFERENCES public.regions (id) ON DELETE SET NULL;
END IF; END; $$;
CREATE TABLE IF NOT EXISTS public.vendor_regions (
  vendor_id uuid NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  region_id uuid NOT NULL REFERENCES public.regions (id) ON DELETE CASCADE,
  kind text NOT NULL DEFAULT 'SPECIALTY' CHECK (kind IN ('NATIVE','SPECIALTY','SERVED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (vendor_id, region_id, kind));
CREATE TABLE IF NOT EXISTS public.vendor_cuisines (
  vendor_id uuid NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  cuisine_id uuid NOT NULL REFERENCES public.cuisines (id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (vendor_id, cuisine_id));
CREATE TABLE IF NOT EXISTS public.vendor_verifications (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  vendor_id uuid NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  verification_type text NOT NULL CHECK (verification_type IN ('KYC','FSSAI','ADDRESS','BANK','DOCUMENT')),
  status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','CHANGES_REQUESTED','EXPIRED')),
  document_urls text[] NOT NULL DEFAULT '{}',
  submitted_by uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  reviewed_by uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  reviewed_at timestamptz, rejection_reason text, admin_notes text,
  idempotency_key text UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.vendor_documents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  vendor_id uuid NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  document_type text NOT NULL, file_url text NOT NULL,
  status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.menus (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  vendor_id uuid NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  name text NOT NULL, description text NOT NULL DEFAULT '',
  is_active boolean NOT NULL DEFAULT true, published_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.menu_categories (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  menu_id uuid NOT NULL REFERENCES public.menus (id) ON DELETE CASCADE,
  name text NOT NULL, sort_order integer NOT NULL DEFAULT 0,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (menu_id, name));
CREATE TABLE IF NOT EXISTS public.menu_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  menu_id uuid NOT NULL REFERENCES public.menus (id) ON DELETE CASCADE,
  category_id uuid REFERENCES public.menu_categories (id) ON DELETE SET NULL,
  vendor_id uuid NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  name text NOT NULL, description text NOT NULL DEFAULT '', image_url text,
  price_paise bigint NOT NULL CHECK (price_paise >= 0),
  mrp_paise bigint CHECK (mrp_paise IS NULL OR mrp_paise >= 0),
  currency text NOT NULL DEFAULT 'INR',
  cuisine_tags text[] NOT NULL DEFAULT '{}', region_tags text[] NOT NULL DEFAULT '{}',
  food_type text NOT NULL DEFAULT 'VEG' CHECK (food_type IN ('VEG','NON_VEG','EGG','VEGAN','JAIN')),
  meal_types text[] NOT NULL DEFAULT '{}',
  preparation_time_minutes integer CHECK (preparation_time_minutes IS NULL OR preparation_time_minutes >= 0),
  is_available boolean NOT NULL DEFAULT true, is_active boolean NOT NULL DEFAULT true,
  sort_order integer NOT NULL DEFAULT 0, deleted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.slots (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code text NOT NULL UNIQUE CHECK (code = upper(code)),
  name text NOT NULL, starts_at time NOT NULL, ends_at time NOT NULL,
  cutoff_minutes_before integer NOT NULL DEFAULT 60 CHECK (cutoff_minutes_before >= 0),
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT slots_time_order_check CHECK (starts_at < ends_at));
CREATE TABLE IF NOT EXISTS public.vendor_slots (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  vendor_id uuid NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  slot_id uuid NOT NULL REFERENCES public.slots (id) ON DELETE RESTRICT,
  service_days integer[] NOT NULL DEFAULT '{0,1,2,3,4,5,6}',
  capacity_total integer NOT NULL CHECK (capacity_total > 0),
  capacity_reserved integer NOT NULL DEFAULT 0 CHECK (capacity_reserved >= 0),
  is_available boolean NOT NULL DEFAULT true,
  effective_from date, effective_to date,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (vendor_id, slot_id),
  CONSTRAINT vendor_slots_capacity_check CHECK (capacity_reserved <= capacity_total),
  CONSTRAINT vendor_slots_days_check CHECK (service_days <@ '{0,1,2,3,4,5,6}'::integer[]));
CREATE TABLE IF NOT EXISTS public.service_zones (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code text NOT NULL UNIQUE CHECK (code = upper(code)),
  name text NOT NULL, city text NOT NULL DEFAULT '', state text NOT NULL DEFAULT '',
  kind text NOT NULL DEFAULT 'AREA' CHECK (kind IN ('CITY','AREA','PIN','RADIUS','POLYGON')),
  postal_codes text[] NOT NULL DEFAULT '{}',
  center_lat double precision, center_lng double precision,
  radius_km numeric(6,2) CHECK (radius_km IS NULL OR radius_km > 0),
  geojson jsonb NOT NULL DEFAULT '{}'::jsonb,
  delivery_fee_paise bigint NOT NULL DEFAULT 0 CHECK (delivery_fee_paise >= 0),
  min_order_paise bigint NOT NULL DEFAULT 0 CHECK (min_order_paise >= 0),
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.vendor_service_zones (
  vendor_id uuid NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  service_zone_id uuid NOT NULL REFERENCES public.service_zones (id) ON DELETE CASCADE,
  custom_delivery_fee_paise bigint CHECK (custom_delivery_fee_paise IS NULL OR custom_delivery_fee_paise >= 0),
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (vendor_id, service_zone_id));
CREATE TABLE IF NOT EXISTS public.menu_item_availability (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  menu_item_id uuid NOT NULL REFERENCES public.menu_items (id) ON DELETE CASCADE,
  vendor_slot_id uuid REFERENCES public.vendor_slots (id) ON DELETE CASCADE,
  service_date date,
  quantity_available integer NOT NULL CHECK (quantity_available >= 0),
  quantity_reserved integer NOT NULL DEFAULT 0 CHECK (quantity_reserved >= 0),
  is_available boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (menu_item_id, vendor_slot_id, service_date),
  CONSTRAINT menu_item_availability_reserved_check CHECK (quantity_reserved <= quantity_available));

-- ================= 0004 commerce =================
CREATE TABLE IF NOT EXISTS public.coupons (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code citext NOT NULL UNIQUE, title text NOT NULL,
  description text NOT NULL DEFAULT '',
  discount_type text NOT NULL CHECK (discount_type IN ('PERCENT','FLAT','FREE_DELIVERY')),
  discount_bps integer CHECK (discount_bps IS NULL OR (discount_bps > 0 AND discount_bps <= 10000)),
  discount_paise bigint CHECK (discount_paise IS NULL OR discount_paise >= 0),
  max_discount_paise bigint CHECK (max_discount_paise IS NULL OR max_discount_paise >= 0),
  min_order_paise bigint NOT NULL DEFAULT 0 CHECK (min_order_paise >= 0),
  usage_limit_total integer CHECK (usage_limit_total IS NULL OR usage_limit_total > 0),
  usage_limit_per_user integer NOT NULL DEFAULT 1 CHECK (usage_limit_per_user > 0),
  used_count integer NOT NULL DEFAULT 0 CHECK (used_count >= 0),
  applicable_vendor_ids uuid[] NOT NULL DEFAULT '{}',
  valid_from timestamptz, valid_to timestamptz,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT coupons_window_check CHECK (valid_from IS NULL OR valid_to IS NULL OR valid_from <= valid_to));
CREATE TABLE IF NOT EXISTS public.carts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id uuid NOT NULL REFERENCES public.customer_profiles (id) ON DELETE CASCADE,
  vendor_id uuid NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  slot_id uuid REFERENCES public.slots (id) ON DELETE SET NULL,
  address_id uuid REFERENCES public.addresses (id) ON DELETE SET NULL,
  coupon_id uuid REFERENCES public.coupons (id) ON DELETE SET NULL,
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CHECKED_OUT','ABANDONED','EXPIRED')),
  expires_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.cart_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  cart_id uuid NOT NULL REFERENCES public.carts (id) ON DELETE CASCADE,
  menu_item_id uuid NOT NULL REFERENCES public.menu_items (id) ON DELETE RESTRICT,
  quantity integer NOT NULL CHECK (quantity > 0 AND quantity <= 99),
  unit_price_paise_snapshot bigint NOT NULL CHECK (unit_price_paise_snapshot >= 0),
  customization_hash text NOT NULL DEFAULT '',
  customizations jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (cart_id, menu_item_id, customization_hash));
CREATE TABLE IF NOT EXISTS public.orders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_number text NOT NULL UNIQUE,
  idempotency_key text NOT NULL UNIQUE,
  customer_id uuid NOT NULL REFERENCES public.customer_profiles (id) ON DELETE RESTRICT,
  vendor_id uuid NOT NULL REFERENCES public.vendors (id) ON DELETE RESTRICT,
  address_id uuid REFERENCES public.addresses (id) ON DELETE SET NULL,
  slot_id uuid REFERENCES public.slots (id) ON DELETE SET NULL,
  service_date date, subscription_id uuid, subscription_schedule_id uuid,
  coupon_id uuid REFERENCES public.coupons (id) ON DELETE SET NULL,
  status text NOT NULL DEFAULT 'CREATED'
    CHECK (status IN ('CREATED','PAYMENT_PENDING','PAYMENT_CONFIRMED','PAYMENT_FAILED','PLACED','VENDOR_PENDING','VENDOR_ACCEPTED','PREPARING','READY_FOR_PICKUP','RIDER_ASSIGNED','RIDER_ACCEPTED','PICKED_UP','OUT_FOR_DELIVERY','ARRIVED','DELIVERED','CUSTOMER_CONFIRMED','COMPLETED','CANCEL_REQUESTED','CANCELLED','REFUND_PENDING','REFUNDED','FAILED')),
  payment_status text NOT NULL DEFAULT 'PENDING'
    CHECK (payment_status IN ('PENDING','INITIATED','SUCCESS','FAILED','CANCELLED','REFUND_PENDING','PARTIALLY_REFUNDED','REFUNDED')),
  subtotal_paise bigint NOT NULL CHECK (subtotal_paise >= 0),
  discount_paise bigint NOT NULL DEFAULT 0 CHECK (discount_paise >= 0),
  tax_paise bigint NOT NULL DEFAULT 0 CHECK (tax_paise >= 0),
  delivery_fee_paise bigint NOT NULL DEFAULT 0 CHECK (delivery_fee_paise >= 0),
  platform_fee_paise bigint NOT NULL DEFAULT 0 CHECK (platform_fee_paise >= 0),
  packaging_fee_paise bigint NOT NULL DEFAULT 0 CHECK (packaging_fee_paise >= 0),
  total_paise bigint NOT NULL CHECK (total_paise >= 0),
  currency text NOT NULL DEFAULT 'INR',
  commission_bps_snapshot integer NOT NULL CHECK (commission_bps_snapshot >= 0 AND commission_bps_snapshot <= 10000),
  platform_commission_paise_snapshot bigint NOT NULL DEFAULT 0 CHECK (platform_commission_paise_snapshot >= 0),
  vendor_payout_paise_snapshot bigint NOT NULL DEFAULT 0 CHECK (vendor_payout_paise_snapshot >= 0),
  rider_payout_paise_snapshot bigint CHECK (rider_payout_paise_snapshot IS NULL OR rider_payout_paise_snapshot >= 0),
  coupon_code_snapshot text,
  delivery_address_snapshot jsonb NOT NULL,
  item_count integer NOT NULL DEFAULT 0 CHECK (item_count >= 0),
  customer_note text, vendor_note text, cancellation_reason text,
  cancelled_by uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  cancelled_at timestamptz, estimated_ready_at timestamptz, estimated_delivery_at timestamptz,
  delivered_at timestamptz, completed_at timestamptz, payment_due_at timestamptz,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT orders_total_consistency_check
    CHECK (total_paise = subtotal_paise - discount_paise + tax_paise + delivery_fee_paise + platform_fee_paise + packaging_fee_paise
           AND subtotal_paise >= discount_paise));
CREATE TABLE IF NOT EXISTS public.order_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL REFERENCES public.orders (id) ON DELETE CASCADE,
  menu_item_id uuid REFERENCES public.menu_items (id) ON DELETE SET NULL,
  vendor_id uuid NOT NULL REFERENCES public.vendors (id) ON DELETE RESTRICT,
  item_name_snapshot text NOT NULL,
  item_description_snapshot text NOT NULL DEFAULT '',
  unit_price_paise_snapshot bigint NOT NULL CHECK (unit_price_paise_snapshot >= 0),
  mrp_paise_snapshot bigint CHECK (mrp_paise_snapshot IS NULL OR mrp_paise_snapshot >= 0),
  quantity integer NOT NULL CHECK (quantity > 0),
  discount_paise_snapshot bigint NOT NULL DEFAULT 0 CHECK (discount_paise_snapshot >= 0),
  tax_paise_snapshot bigint NOT NULL DEFAULT 0 CHECK (tax_paise_snapshot >= 0),
  line_total_paise bigint NOT NULL CHECK (line_total_paise >= 0),
  cuisine_tags_snapshot text[] NOT NULL DEFAULT '{}',
  customization_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.order_status_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL REFERENCES public.orders (id) ON DELETE CASCADE,
  from_status text, to_status text NOT NULL,
  changed_by uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  actor_role text, change_reason text,
  idempotency_key text UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.payments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid REFERENCES public.orders (id) ON DELETE RESTRICT,
  subscription_id uuid, customer_id uuid NOT NULL REFERENCES public.customer_profiles (id) ON DELETE RESTRICT,
  provider text NOT NULL CHECK (provider IN ('RAZORPAY','CASHFREE','STRIPE','UPI','COD','INTERNAL')),
  provider_payment_id text UNIQUE, provider_order_id text,
  idempotency_key text NOT NULL UNIQUE,
  amount_paise bigint NOT NULL CHECK (amount_paise >= 0),
  currency text NOT NULL DEFAULT 'INR',
  method text NOT NULL CHECK (method IN ('UPI','CARD','NETBANKING','WALLET','COD','BANK_TRANSFER')),
  status text NOT NULL DEFAULT 'CREATED'
    CHECK (status IN ('CREATED','INITIATED','PENDING','SUCCESS','FAILED','CANCELLED','REFUND_PENDING','PARTIALLY_REFUNDED','REFUNDED')),
  verified_at timestamptz, failure_code text, failure_message text,
  webhook_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT payments_subject_check CHECK (order_id IS NOT NULL OR subscription_id IS NOT NULL));
CREATE TABLE IF NOT EXISTS public.payment_attempts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  payment_id uuid NOT NULL REFERENCES public.payments (id) ON DELETE CASCADE,
  attempt_no integer NOT NULL CHECK (attempt_no > 0),
  provider_reference text,
  status text NOT NULL CHECK (status IN ('INITIATED','PENDING','SUCCESS','FAILED','CANCELLED')),
  request_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  response_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  idempotency_key text NOT NULL UNIQUE,
  error_code text, created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (payment_id, attempt_no));
CREATE TABLE IF NOT EXISTS public.refunds (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL REFERENCES public.orders (id) ON DELETE RESTRICT,
  payment_id uuid NOT NULL REFERENCES public.payments (id) ON DELETE RESTRICT,
  idempotency_key text NOT NULL UNIQUE,
  amount_paise bigint NOT NULL CHECK (amount_paise > 0),
  currency text NOT NULL DEFAULT 'INR', reason text NOT NULL DEFAULT '',
  status text NOT NULL DEFAULT 'REQUESTED'
    CHECK (status IN ('REQUESTED','APPROVED','PROCESSING','COMPLETED','FAILED','REJECTED','CANCELLED')),
  provider_refund_id text UNIQUE,
  requested_by uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  approved_by uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  processed_at timestamptz, failure_reason text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.coupon_usages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  coupon_id uuid NOT NULL REFERENCES public.coupons (id) ON DELETE RESTRICT,
  order_id uuid NOT NULL UNIQUE REFERENCES public.orders (id) ON DELETE CASCADE,
  customer_id uuid NOT NULL REFERENCES public.customer_profiles (id) ON DELETE CASCADE,
  discount_paise bigint NOT NULL CHECK (discount_paise >= 0),
  created_at timestamptz NOT NULL DEFAULT now());

-- ================= 0005 subscription =================
CREATE TABLE IF NOT EXISTS public.meal_plans (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  vendor_id uuid NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  name text NOT NULL, description text NOT NULL DEFAULT '', image_url text,
  price_per_meal_paise bigint NOT NULL CHECK (price_per_meal_paise >= 0),
  price_monthly_paise bigint CHECK (price_monthly_paise IS NULL OR price_monthly_paise >= 0),
  currency text NOT NULL DEFAULT 'INR',
  meals_per_day integer NOT NULL DEFAULT 1 CHECK (meals_per_day > 0),
  trial_available boolean NOT NULL DEFAULT false,
  applicable_days integer[] NOT NULL DEFAULT '{0,1,2,3,4,5,6}',
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT meal_plans_days_check CHECK (applicable_days <@ '{0,1,2,3,4,5,6}'::integer[]));
CREATE TABLE IF NOT EXISTS public.subscriptions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  subscription_number text NOT NULL UNIQUE,
  idempotency_key text NOT NULL UNIQUE,
  customer_id uuid NOT NULL REFERENCES public.customer_profiles (id) ON DELETE RESTRICT,
  vendor_id uuid NOT NULL REFERENCES public.vendors (id) ON DELETE RESTRICT,
  meal_plan_id uuid NOT NULL REFERENCES public.meal_plans (id) ON DELETE RESTRICT,
  address_id uuid REFERENCES public.addresses (id) ON DELETE SET NULL,
  status text NOT NULL DEFAULT 'DRAFT'
    CHECK (status IN ('DRAFT','PAYMENT_PENDING','ACTIVE','PAUSED','SKIPPED','EXPIRED','CANCELLED','PAYMENT_FAILED')),
  start_date date NOT NULL, end_date date NOT NULL,
  total_meals integer NOT NULL CHECK (total_meals > 0),
  meals_consumed integer NOT NULL DEFAULT 0 CHECK (meals_consumed >= 0),
  meals_skipped integer NOT NULL DEFAULT 0 CHECK (meals_skipped >= 0),
  price_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
  total_amount_paise bigint NOT NULL CHECK (total_amount_paise >= 0),
  amount_paid_paise bigint NOT NULL DEFAULT 0 CHECK (amount_paid_paise >= 0),
  currency text NOT NULL DEFAULT 'INR',
  delivery_preferences jsonb NOT NULL DEFAULT '{}'::jsonb,
  skip_dates date[] NOT NULL DEFAULT '{}',
  paused_from date, paused_to date,
  cancelled_at timestamptz, cancellation_reason text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT subscriptions_date_order_check CHECK (end_date >= start_date),
  CONSTRAINT subscriptions_pause_window_check CHECK ((paused_from IS NULL AND paused_to IS NULL) OR (paused_from IS NOT NULL AND paused_to IS NOT NULL AND paused_to >= paused_from)));
CREATE TABLE IF NOT EXISTS public.subscription_schedules (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  subscription_id uuid NOT NULL REFERENCES public.subscriptions (id) ON DELETE CASCADE,
  weekday integer NOT NULL CHECK (weekday >= 0 AND weekday <= 6),
  meal_slot_id uuid NOT NULL REFERENCES public.slots (id) ON DELETE RESTRICT,
  quantity integer NOT NULL DEFAULT 1 CHECK (quantity > 0),
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (subscription_id, weekday, meal_slot_id));
CREATE TABLE IF NOT EXISTS public.subscription_daily_orders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  subscription_id uuid NOT NULL REFERENCES public.subscriptions (id) ON DELETE CASCADE,
  order_id uuid UNIQUE REFERENCES public.orders (id) ON DELETE SET NULL,
  service_date date NOT NULL,
  meal_slot_id uuid NOT NULL REFERENCES public.slots (id) ON DELETE RESTRICT,
  status text NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED','GENERATED','SKIPPED','FAILED','CANCELLED')),
  idempotency_key text NOT NULL UNIQUE,
  failure_reason text, generated_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (subscription_id, service_date, meal_slot_id));
CREATE TABLE IF NOT EXISTS public.subscription_status_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  subscription_id uuid NOT NULL REFERENCES public.subscriptions (id) ON DELETE CASCADE,
  from_status text, to_status text NOT NULL,
  changed_by uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  actor_role text, change_reason text,
  idempotency_key text UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now());
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_orders_subscription') THEN
    ALTER TABLE public.orders ADD CONSTRAINT fk_orders_subscription FOREIGN KEY (subscription_id) REFERENCES public.subscriptions (id) ON DELETE SET NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_payments_subscription') THEN
    ALTER TABLE public.payments ADD CONSTRAINT fk_payments_subscription FOREIGN KEY (subscription_id) REFERENCES public.subscriptions (id) ON DELETE SET NULL;
  END IF;
END; $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriptions_single_live ON public.subscriptions (customer_id, vendor_id, meal_plan_id) WHERE status IN ('ACTIVE','PAUSED');

-- ================= 0006 fulfillment =================
CREATE TABLE IF NOT EXISTS public.riders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL UNIQUE REFERENCES public.platform_users (id) ON DELETE RESTRICT,
  display_name text NOT NULL DEFAULT '', phone text, photo_url text,
  vehicle_type text NOT NULL DEFAULT 'BIKE' CHECK (vehicle_type IN ('BICYCLE','BIKE','SCOOTER','CAR','FOOT')),
  vehicle_number text, city text NOT NULL DEFAULT '',
  status text NOT NULL DEFAULT 'DRAFT'
    CHECK (status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','APPROVED','ACTIVE','INACTIVE','SUSPENDED','DEACTIVATED')),
  kyc_status text NOT NULL DEFAULT 'PENDING' CHECK (kyc_status IN ('PENDING','APPROVED','REJECTED','CHANGES_REQUESTED','EXPIRED')),
  rating_avg numeric(3,2) NOT NULL DEFAULT 0.00 CHECK (rating_avg >= 0 AND rating_avg <= 5),
  rating_count integer NOT NULL DEFAULT 0 CHECK (rating_count >= 0),
  total_deliveries integer NOT NULL DEFAULT 0 CHECK (total_deliveries >= 0),
  current_lat double precision, current_lng double precision, last_location_at timestamptz,
  is_online boolean NOT NULL DEFAULT false,
  max_concurrent_deliveries integer NOT NULL DEFAULT 2 CHECK (max_concurrent_deliveries > 0 AND max_concurrent_deliveries <= 5),
  approved_at timestamptz, approved_by uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  suspension_reason text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.rider_documents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  rider_id uuid NOT NULL REFERENCES public.riders (id) ON DELETE CASCADE,
  document_type text NOT NULL, file_url text NOT NULL,
  status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.rider_availability (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  rider_id uuid NOT NULL REFERENCES public.riders (id) ON DELETE CASCADE,
  weekday integer NOT NULL CHECK (weekday >= 0 AND weekday <= 6),
  start_time time NOT NULL, end_time time NOT NULL,
  is_available boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (rider_id, weekday, start_time, end_time),
  CONSTRAINT rider_availability_time_order CHECK (start_time < end_time));
CREATE TABLE IF NOT EXISTS public.deliveries (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  delivery_number text NOT NULL UNIQUE,
  order_id uuid NOT NULL UNIQUE REFERENCES public.orders (id) ON DELETE RESTRICT,
  rider_id uuid REFERENCES public.riders (id) ON DELETE SET NULL,
  idempotency_key text NOT NULL UNIQUE,
  status text NOT NULL DEFAULT 'AVAILABLE'
    CHECK (status IN ('AVAILABLE','ASSIGNMENT_PENDING','ASSIGNED','ACCEPTED','ARRIVED_AT_VENDOR','PICKUP_VERIFICATION_PENDING','PICKED_UP','EN_ROUTE','ARRIVED_AT_CUSTOMER','DELIVERY_VERIFICATION_PENDING','DELIVERED','CANCELLED','FAILED')),
  pickup_code_hash text, pickup_code_expires_at timestamptz,
  delivery_code_hash text, delivery_code_expires_at timestamptz,
  pickup_qr_token_hash text, pickup_qr_expires_at timestamptz,
  pickup_address_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
  dropoff_address_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
  distance_km numeric(7,2) CHECK (distance_km IS NULL OR distance_km >= 0),
  delivery_fee_paise_snapshot bigint NOT NULL DEFAULT 0 CHECK (delivery_fee_paise_snapshot >= 0),
  rider_payout_paise_snapshot bigint CHECK (rider_payout_paise_snapshot IS NULL OR rider_payout_paise_snapshot >= 0),
  tip_paise bigint NOT NULL DEFAULT 0 CHECK (tip_paise >= 0),
  proof_of_delivery jsonb NOT NULL DEFAULT '{}'::jsonb,
  failure_reason text, estimated_pickup_at timestamptz, estimated_delivery_at timestamptz,
  picked_up_at timestamptz, delivered_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.delivery_assignments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  delivery_id uuid NOT NULL REFERENCES public.deliveries (id) ON DELETE CASCADE,
  rider_id uuid NOT NULL REFERENCES public.riders (id) ON DELETE RESTRICT,
  attempt_no integer NOT NULL CHECK (attempt_no > 0),
  status text NOT NULL DEFAULT 'OFFERED' CHECK (status IN ('OFFERED','ACCEPTED','REJECTED','EXPIRED','TIMEOUT','CANCELLED','REASSIGNED')),
  offered_at timestamptz NOT NULL DEFAULT now(),
  responded_at timestamptz, expires_at timestamptz,
  assignment_reason jsonb NOT NULL DEFAULT '{}'::jsonb,
  idempotency_key text NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (delivery_id, rider_id, attempt_no));
CREATE TABLE IF NOT EXISTS public.delivery_status_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  delivery_id uuid NOT NULL REFERENCES public.deliveries (id) ON DELETE CASCADE,
  from_status text, to_status text NOT NULL,
  changed_by uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  actor_role text, change_reason text,
  location_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
  idempotency_key text UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now());

-- ================= 0007 operations/finance/shared =================
CREATE TABLE IF NOT EXISTS public.support_tickets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_number text NOT NULL UNIQUE,
  requester_id uuid NOT NULL REFERENCES public.platform_users (id) ON DELETE RESTRICT,
  requester_role text NOT NULL DEFAULT 'CUSTOMER',
  order_id uuid REFERENCES public.orders (id) ON DELETE SET NULL,
  delivery_id uuid REFERENCES public.deliveries (id) ON DELETE SET NULL,
  subject text NOT NULL, description text NOT NULL DEFAULT '',
  category text NOT NULL DEFAULT 'OTHER' CHECK (category IN ('ORDER','PAYMENT','DELIVERY','SUBSCRIPTION','VENDOR','RIDER','ACCOUNT','OTHER')),
  priority text NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
  status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','ASSIGNED','IN_PROGRESS','WAITING_ON_CUSTOMER','RESOLVED','CLOSED','REOPENED')),
  assigned_to uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  resolution_note text, resolved_at timestamptz, sla_due_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.support_ticket_messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_id uuid NOT NULL REFERENCES public.support_tickets (id) ON DELETE CASCADE,
  sender_id uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  message text NOT NULL, attachments jsonb NOT NULL DEFAULT '[]'::jsonb,
  is_internal boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.audit_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_id uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  actor_role text, action text NOT NULL, entity_type text NOT NULL, entity_id uuid,
  old_value jsonb NOT NULL DEFAULT '{}'::jsonb, new_value jsonb NOT NULL DEFAULT '{}'::jsonb,
  reason text, ip_address inet, user_agent text, request_id text,
  created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.notifications (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  recipient_user_id uuid NOT NULL REFERENCES public.platform_users (id) ON DELETE CASCADE,
  channel text NOT NULL CHECK (channel IN ('PUSH','SMS','EMAIL','IN_APP')),
  template_code text NOT NULL DEFAULT 'GENERIC',
  title text NOT NULL DEFAULT '', body text NOT NULL DEFAULT '',
  data jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','DELIVERED','READ','FAILED','CANCELLED')),
  dedupe_key text UNIQUE, sent_at timestamptz, read_at timestamptz, failure_reason text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.notification_preferences (
  user_id uuid PRIMARY KEY REFERENCES public.platform_users (id) ON DELETE CASCADE,
  push_enabled boolean NOT NULL DEFAULT true, sms_enabled boolean NOT NULL DEFAULT true,
  email_enabled boolean NOT NULL DEFAULT true, locale text NOT NULL DEFAULT 'en',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.reviews (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL UNIQUE REFERENCES public.orders (id) ON DELETE CASCADE,
  customer_id uuid NOT NULL REFERENCES public.customer_profiles (id) ON DELETE CASCADE,
  vendor_id uuid NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  rider_id uuid REFERENCES public.riders (id) ON DELETE SET NULL,
  delivery_id uuid REFERENCES public.deliveries (id) ON DELETE SET NULL,
  vendor_rating smallint CHECK (vendor_rating IS NULL OR (vendor_rating >= 1 AND vendor_rating <= 5)),
  food_rating smallint CHECK (food_rating IS NULL OR (food_rating >= 1 AND food_rating <= 5)),
  delivery_rating smallint CHECK (delivery_rating IS NULL OR (delivery_rating >= 1 AND delivery_rating <= 5)),
  comment text, images jsonb NOT NULL DEFAULT '[]'::jsonb,
  status text NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('PUBLISHED','HIDDEN','FLAGGED','REMOVED')),
  moderated_by uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  moderated_at timestamptz, moderation_reason text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT reviews_at_least_one_rating CHECK (vendor_rating IS NOT NULL OR food_rating IS NOT NULL OR delivery_rating IS NOT NULL));
CREATE TABLE IF NOT EXISTS public.commissions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL UNIQUE REFERENCES public.orders (id) ON DELETE RESTRICT,
  vendor_id uuid NOT NULL REFERENCES public.vendors (id) ON DELETE RESTRICT,
  basis_amount_paise bigint NOT NULL CHECK (basis_amount_paise >= 0),
  commission_bps_snapshot integer NOT NULL CHECK (commission_bps_snapshot >= 0 AND commission_bps_snapshot <= 10000),
  commission_amount_paise bigint NOT NULL CHECK (commission_amount_paise >= 0),
  tax_on_commission_paise bigint NOT NULL DEFAULT 0 CHECK (tax_on_commission_paise >= 0),
  net_vendor_share_paise bigint NOT NULL CHECK (net_vendor_share_paise >= 0),
  currency text NOT NULL DEFAULT 'INR',
  status text NOT NULL DEFAULT 'ACCRUED' CHECK (status IN ('ACCRUED','SETTLED','REVERSED')),
  accrued_at timestamptz NOT NULL DEFAULT now(),
  reversed_at timestamptz, reversal_reason text,
  created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.vendor_payouts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  payout_number text NOT NULL UNIQUE,
  vendor_id uuid NOT NULL REFERENCES public.vendors (id) ON DELETE RESTRICT,
  period_start date NOT NULL, period_end date NOT NULL,
  gross_amount_paise bigint NOT NULL CHECK (gross_amount_paise >= 0),
  commission_deducted_paise bigint NOT NULL DEFAULT 0 CHECK (commission_deducted_paise >= 0),
  refunds_deducted_paise bigint NOT NULL DEFAULT 0 CHECK (refunds_deducted_paise >= 0),
  adjustments_paise bigint NOT NULL DEFAULT 0,
  net_amount_paise bigint NOT NULL, currency text NOT NULL DEFAULT 'INR',
  status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SCHEDULED','PROCESSING','COMPLETED','FAILED','CANCELLED')),
  provider_reference text UNIQUE, idempotency_key text NOT NULL UNIQUE,
  initiated_by uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  approved_by uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  failure_reason text, processed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT vendor_payouts_period_check CHECK (period_end >= period_start));
CREATE TABLE IF NOT EXISTS public.vendor_payout_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  payout_id uuid NOT NULL REFERENCES public.vendor_payouts (id) ON DELETE CASCADE,
  order_id uuid NOT NULL REFERENCES public.orders (id) ON DELETE RESTRICT,
  commission_id uuid REFERENCES public.commissions (id) ON DELETE SET NULL,
  amount_paise bigint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (payout_id, order_id));
CREATE TABLE IF NOT EXISTS public.rider_payouts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  payout_number text NOT NULL UNIQUE,
  rider_id uuid NOT NULL REFERENCES public.riders (id) ON DELETE RESTRICT,
  period_start date NOT NULL, period_end date NOT NULL,
  gross_amount_paise bigint NOT NULL CHECK (gross_amount_paise >= 0),
  adjustments_paise bigint NOT NULL DEFAULT 0,
  net_amount_paise bigint NOT NULL, currency text NOT NULL DEFAULT 'INR',
  status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SCHEDULED','PROCESSING','COMPLETED','FAILED','CANCELLED')),
  provider_reference text UNIQUE, idempotency_key text NOT NULL UNIQUE,
  initiated_by uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  approved_by uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  failure_reason text, processed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT rider_payouts_period_check CHECK (period_end >= period_start));
CREATE TABLE IF NOT EXISTS public.rider_payout_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  payout_id uuid NOT NULL REFERENCES public.rider_payouts (id) ON DELETE CASCADE,
  delivery_id uuid NOT NULL REFERENCES public.deliveries (id) ON DELETE RESTRICT,
  amount_paise bigint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (payout_id, delivery_id));
CREATE TABLE IF NOT EXISTS public.outbox_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  aggregate_type text NOT NULL, aggregate_id uuid NOT NULL,
  event_type text NOT NULL, payload jsonb NOT NULL, headers jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PUBLISHED','FAILED','DEAD_LETTER')),
  attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  next_attempt_at timestamptz NOT NULL DEFAULT now(),
  published_at timestamptz, error text,
  idempotency_key text NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS public.idempotency_keys (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  scope text NOT NULL, key text NOT NULL,
  user_id uuid REFERENCES public.platform_users (id) ON DELETE SET NULL,
  status text NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS','COMPLETED','FAILED')),
  response_code integer, response_body jsonb NOT NULL DEFAULT '{}'::jsonb,
  locked_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL DEFAULT (now() + interval '24 hours'),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (scope, key));

-- ================= 0008 indexes (no RLS/storage here) =================
CREATE INDEX IF NOT EXISTS ix_platform_users_status ON public.platform_users (account_status);
CREATE INDEX IF NOT EXISTS ix_user_roles_role ON public.user_roles (role_id);
CREATE INDEX IF NOT EXISTS ix_customer_profiles_user ON public.customer_profiles (user_id);
CREATE INDEX IF NOT EXISTS ix_addresses_customer ON public.addresses (customer_id) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS ix_addresses_city_pin ON public.addresses (city, postal_code) WHERE is_active = true;
CREATE UNIQUE INDEX IF NOT EXISTS uq_addresses_single_default ON public.addresses (customer_id) WHERE is_default = true AND is_active = true;
CREATE INDEX IF NOT EXISTS ix_vendors_status_active ON public.vendors (status, city) WHERE deleted_at IS NULL AND is_active = true;
CREATE INDEX IF NOT EXISTS ix_vendors_city_status ON public.vendors (city, status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_vendors_native_region ON public.vendors (native_region_id) WHERE deleted_at IS NULL AND status = 'APPROVED';
CREATE INDEX IF NOT EXISTS ix_vendors_cuisine_tags ON public.vendors USING gin (cuisine_tags);
CREATE INDEX IF NOT EXISTS ix_vendors_name_trgm ON public.vendors USING gin (kitchen_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS ix_vendor_regions_region ON public.vendor_regions (region_id);
CREATE INDEX IF NOT EXISTS ix_vendor_cuisines_cuisine ON public.vendor_cuisines (cuisine_id);
CREATE INDEX IF NOT EXISTS ix_vendor_verifications_vendor ON public.vendor_verifications (vendor_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_vendor_verifications_single_open ON public.vendor_verifications (vendor_id, verification_type) WHERE status IN ('PENDING','CHANGES_REQUESTED');
CREATE INDEX IF NOT EXISTS ix_menus_vendor ON public.menus (vendor_id) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS ix_menu_categories_menu ON public.menu_categories (menu_id);
CREATE INDEX IF NOT EXISTS ix_menu_items_vendor_avail ON public.menu_items (vendor_id, is_available) WHERE deleted_at IS NULL AND is_active = true;
CREATE INDEX IF NOT EXISTS ix_menu_items_category ON public.menu_items (category_id);
CREATE INDEX IF NOT EXISTS ix_menu_items_name_trgm ON public.menu_items USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS ix_menu_items_tags ON public.menu_items USING gin (cuisine_tags);
CREATE INDEX IF NOT EXISTS ix_menu_items_region_tags ON public.menu_items USING gin (region_tags);
CREATE INDEX IF NOT EXISTS ix_vendor_slots_vendor ON public.vendor_slots (vendor_id, is_available);
CREATE INDEX IF NOT EXISTS ix_service_zones_city ON public.service_zones (city) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS ix_service_zones_pins ON public.service_zones USING gin (postal_codes) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS ix_vendor_service_zones_zone ON public.vendor_service_zones (service_zone_id) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS ix_menu_item_avail_lookup ON public.menu_item_availability (menu_item_id, service_date) WHERE is_available = true;
CREATE INDEX IF NOT EXISTS ix_orders_customer_created ON public.orders (customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_orders_vendor_status ON public.orders (vendor_id, status);
CREATE INDEX IF NOT EXISTS ix_orders_vendor_created ON public.orders (vendor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_orders_status_service ON public.orders (status, service_date);
CREATE INDEX IF NOT EXISTS ix_orders_subscription ON public.orders (subscription_id) WHERE subscription_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_order_items_order ON public.order_items (order_id);
CREATE INDEX IF NOT EXISTS ix_order_history_order_created ON public.order_status_history (order_id, created_at);
CREATE INDEX IF NOT EXISTS ix_payments_order ON public.payments (order_id);
CREATE INDEX IF NOT EXISTS ix_payments_subscription ON public.payments (subscription_id) WHERE subscription_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_payments_provider_ref ON public.payments (provider, provider_payment_id);
CREATE INDEX IF NOT EXISTS ix_payments_status ON public.payments (status);
CREATE INDEX IF NOT EXISTS ix_payment_attempts_payment ON public.payment_attempts (payment_id);
CREATE INDEX IF NOT EXISTS ix_refunds_order ON public.refunds (order_id);
CREATE INDEX IF NOT EXISTS ix_refunds_status ON public.refunds (status);
CREATE INDEX IF NOT EXISTS ix_carts_customer ON public.carts (customer_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_carts_single_active ON public.carts (customer_id, vendor_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS ix_cart_items_cart ON public.cart_items (cart_id);
CREATE INDEX IF NOT EXISTS ix_coupon_usages_customer_coupon ON public.coupon_usages (customer_id, coupon_id);
CREATE INDEX IF NOT EXISTS ix_subscriptions_vendor_status ON public.subscriptions (vendor_id, status);
CREATE INDEX IF NOT EXISTS ix_subscriptions_customer_status ON public.subscriptions (customer_id, status);
CREATE INDEX IF NOT EXISTS ix_subscriptions_active_window ON public.subscriptions (start_date, end_date) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS ix_daily_orders_due ON public.subscription_daily_orders (service_date, status);
CREATE INDEX IF NOT EXISTS ix_daily_orders_subscription ON public.subscription_daily_orders (subscription_id, service_date);
CREATE INDEX IF NOT EXISTS ix_riders_status_online ON public.riders (status, is_online, city);
CREATE INDEX IF NOT EXISTS ix_rider_availability_rider ON public.rider_availability (rider_id, weekday) WHERE is_available = true;
CREATE INDEX IF NOT EXISTS ix_deliveries_rider_status ON public.deliveries (rider_id, status) WHERE rider_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_deliveries_status ON public.deliveries (status);
CREATE INDEX IF NOT EXISTS ix_assignments_delivery ON public.delivery_assignments (delivery_id, status);
CREATE INDEX IF NOT EXISTS ix_assignments_rider_status ON public.delivery_assignments (rider_id, status);
CREATE INDEX IF NOT EXISTS ix_delivery_history_delivery_created ON public.delivery_status_history (delivery_id, created_at);
CREATE INDEX IF NOT EXISTS ix_tickets_status_priority ON public.support_tickets (status, priority);
CREATE INDEX IF NOT EXISTS ix_tickets_assignee ON public.support_tickets (assigned_to, status) WHERE assigned_to IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_tickets_requester ON public.support_tickets (requester_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_ticket_messages_ticket ON public.support_ticket_messages (ticket_id, created_at);
CREATE INDEX IF NOT EXISTS ix_audit_entity ON public.audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_actor_created ON public.audit_logs (actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_notifications_recipient ON public.notifications (recipient_user_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_reviews_vendor ON public.reviews (vendor_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_reviews_rider ON public.reviews (rider_id) WHERE rider_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_commissions_vendor_status ON public.commissions (vendor_id, status);
CREATE INDEX IF NOT EXISTS ix_vendor_payouts_vendor_status ON public.vendor_payouts (vendor_id, status);
CREATE INDEX IF NOT EXISTS ix_rider_payouts_rider_status ON public.rider_payouts (rider_id, status);
CREATE INDEX IF NOT EXISTS ix_outbox_pending ON public.outbox_events (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS ix_outbox_aggregate ON public.outbox_events (aggregate_type, aggregate_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_idempotency_expiry ON public.idempotency_keys (expires_at);

-- updated_at triggers for mutable tables
DO $$ DECLARE t text; BEGIN FOREACH t IN ARRAY ARRAY[
  'roles','permissions','platform_users','customer_profiles','addresses',
  'regions','cuisines','vendors','vendor_verifications','vendor_documents',
  'menus','menu_categories','menu_items','slots','vendor_slots','service_zones','menu_item_availability',
  'coupons','carts','cart_items','orders','order_items','payments','refunds',
  'meal_plans','subscriptions','subscription_schedules','subscription_daily_orders',
  'riders','rider_documents','rider_availability','deliveries','delivery_assignments',
  'support_tickets','notifications','notification_preferences','reviews',
  'vendor_payouts','rider_payouts','outbox_events','idempotency_keys'
] LOOP
  EXECUTE format('DROP TRIGGER IF EXISTS trg_%s_updated_at ON public.%I', t, t);
  EXECUTE format('CREATE TRIGGER trg_%s_updated_at BEFORE UPDATE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.set_updated_at()', t, t);
END LOOP; END; $$;
