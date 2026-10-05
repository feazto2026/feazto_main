-- ============================================================================
-- Codewild Food Platform — Migration 0003: Marketplace domain
-- ============================================================================
-- Domain owner: MARKETPLACE (Vendor / Home Cook / Cuisine / Menu / Slots /
-- Availability) with Verification co-owned by OPERATIONS (approvals audited).
-- Tables: regions, cuisines, vendors, vendor_regions, vendor_cuisines,
--         vendor_verifications, vendor_documents, menus, menu_categories,
--         menu_items, slots, vendor_slots, service_zones,
--         vendor_service_zones, menu_item_availability
--
-- Also attaches customer_profiles.hometown_region_id -> regions FK (column was
-- created nullable in 0002 to preserve migration ordering).
-- Money is stored in paise (bigint) — never float.
-- ============================================================================

-- Region taxonomy (§19): State / District / City / Cultural-Cuisine region ----
CREATE TABLE IF NOT EXISTS public.regions (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  code        text        NOT NULL UNIQUE CHECK (code = upper(code)),
  name        text        NOT NULL,
  kind        text        NOT NULL
              CHECK (kind IN ('STATE', 'DISTRICT', 'CITY', 'CULTURAL_REGION')),
  state       text,
  district    text,
  city        text,
  parent_id   uuid        REFERENCES public.regions (id) ON DELETE SET NULL,
  metadata    jsonb       NOT NULL DEFAULT '{}'::jsonb,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);

-- Cuisine taxonomy: regional / community / meal-type / food-category ----------
CREATE TABLE IF NOT EXISTS public.cuisines (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  code        text        NOT NULL UNIQUE CHECK (code = upper(code)),
  name        text        NOT NULL,
  category    text        NOT NULL DEFAULT 'REGIONAL',
  description text        NOT NULL DEFAULT '',
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);

-- Vendors / home cooks ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.vendors (
  id                          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                     uuid        NOT NULL UNIQUE
                              REFERENCES public.platform_users (id) ON DELETE RESTRICT,
  kitchen_name                text        NOT NULL,
  description                 text        NOT NULL DEFAULT '',
  phone                       text,
  email                       citext,
  profile_image_url           text,
  cover_image_url             text,
  -- Kitchen location (operational address; payout address kept separately)
  address_line                text        NOT NULL DEFAULT '',
  area                        text        NOT NULL DEFAULT '',
  city                        text        NOT NULL DEFAULT '',
  state                       text        NOT NULL DEFAULT '',
  postal_code                 text        NOT NULL DEFAULT '',
  latitude                    double precision,
  longitude                   double precision,
  -- Regional identity (discovery metadata, §19)
  native_region_id            uuid        REFERENCES public.regions (id) ON DELETE SET NULL,
  cuisine_tags                text[]      NOT NULL DEFAULT '{}',
  -- Lifecycle (§11): DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED ...
  status                      text        NOT NULL DEFAULT 'DRAFT'
                              CHECK (status IN (
                                'DRAFT', 'SUBMITTED', 'UNDER_REVIEW',
                                'ACTION_REQUIRED', 'APPROVED', 'SUSPENDED',
                                'REJECTED', 'DEACTIVATED')),
  commission_bps              integer     NOT NULL DEFAULT 1500
                              CHECK (commission_bps >= 0 AND commission_bps <= 10000),
  rating_avg                  numeric(3,2) NOT NULL DEFAULT 0.00
                              CHECK (rating_avg >= 0 AND rating_avg <= 5),
  rating_count                integer     NOT NULL DEFAULT 0 CHECK (rating_count >= 0),
  is_active                   boolean     NOT NULL DEFAULT false,
  service_radius_km           numeric(5,2) NOT NULL DEFAULT 5.00
                              CHECK (service_radius_km > 0),
  preparation_capacity_per_slot integer   NOT NULL DEFAULT 20
                              CHECK (preparation_capacity_per_slot > 0),
  slot_buffer_minutes         integer     NOT NULL DEFAULT 30
                              CHECK (slot_buffer_minutes >= 0),
  -- Compliance / payout references (never store full bank secrets here)
  fssai_license_no            text,
  payout_account_ref          text,
  terms_accepted_at           timestamptz,
  approved_at                 timestamptz,
  approved_by                 uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  suspension_reason           text,
  deleted_at                  timestamptz,
  created_at                  timestamptz NOT NULL DEFAULT now(),
  updated_at                  timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT vendors_geo_pair_check
    CHECK ((latitude IS NULL AND longitude IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL))
);

-- Back-fill FK promised in 0002 --------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_customer_hometown_region'
  ) THEN
    ALTER TABLE public.customer_profiles
      ADD CONSTRAINT fk_customer_hometown_region
      FOREIGN KEY (hometown_region_id)
      REFERENCES public.regions (id) ON DELETE SET NULL;
  END IF;
END;
$$;

-- Vendor <-> region links (native / specialty / served) --------------------------
CREATE TABLE IF NOT EXISTS public.vendor_regions (
  vendor_id   uuid        NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  region_id   uuid        NOT NULL REFERENCES public.regions (id) ON DELETE CASCADE,
  kind        text        NOT NULL DEFAULT 'SPECIALTY'
              CHECK (kind IN ('NATIVE', 'SPECIALTY', 'SERVED')),
  created_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (vendor_id, region_id, kind)
);

CREATE TABLE IF NOT EXISTS public.vendor_cuisines (
  vendor_id   uuid        NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  cuisine_id  uuid        NOT NULL REFERENCES public.cuisines (id) ON DELETE CASCADE,
  created_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (vendor_id, cuisine_id)
);

-- Verification / KYC (Marketplace data, Operations decision) -----------------------
CREATE TABLE IF NOT EXISTS public.vendor_verifications (
  id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  vendor_id         uuid        NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  verification_type text        NOT NULL
                    CHECK (verification_type IN (
                      'KYC', 'FSSAI', 'ADDRESS', 'BANK', 'DOCUMENT')),
  status            text        NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN (
                      'PENDING', 'APPROVED', 'REJECTED',
                      'CHANGES_REQUESTED', 'EXPIRED')),
  document_urls     text[]      NOT NULL DEFAULT '{}',
  submitted_by      uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  reviewed_by       uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  reviewed_at       timestamptz,
  rejection_reason  text,
  admin_notes       text,
  idempotency_key   text        UNIQUE,
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.vendor_documents (
  id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  vendor_id     uuid        NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  document_type text        NOT NULL,
  file_url      text        NOT NULL,
  status        text        NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now()
);

-- Menus ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.menus (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  vendor_id   uuid        NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  name        text        NOT NULL,
  description text        NOT NULL DEFAULT '',
  is_active   boolean     NOT NULL DEFAULT true,
  published_at timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.menu_categories (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  menu_id     uuid        NOT NULL REFERENCES public.menus (id) ON DELETE CASCADE,
  name        text        NOT NULL,
  sort_order  integer     NOT NULL DEFAULT 0,
  is_active   boolean     NOT NULL DEFAULT true,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (menu_id, name)
);

CREATE TABLE IF NOT EXISTS public.menu_items (
  id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  menu_id               uuid        NOT NULL REFERENCES public.menus (id) ON DELETE CASCADE,
  category_id           uuid        REFERENCES public.menu_categories (id) ON DELETE SET NULL,
  vendor_id             uuid        NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  name                  text        NOT NULL,
  description           text        NOT NULL DEFAULT '',
  image_url             text,
  price_paise           bigint      NOT NULL CHECK (price_paise >= 0),
  mrp_paise             bigint      CHECK (mrp_paise IS NULL OR mrp_paise >= 0),
  currency              text        NOT NULL DEFAULT 'INR',
  cuisine_tags          text[]      NOT NULL DEFAULT '{}',
  region_tags           text[]      NOT NULL DEFAULT '{}',
  food_type             text        NOT NULL DEFAULT 'VEG'
                        CHECK (food_type IN ('VEG', 'NON_VEG', 'EGG', 'VEGAN', 'JAIN')),
  meal_types            text[]      NOT NULL DEFAULT '{}',
  preparation_time_minutes integer  CHECK (preparation_time_minutes IS NULL OR preparation_time_minutes >= 0),
  is_available          boolean     NOT NULL DEFAULT true,
  is_active             boolean     NOT NULL DEFAULT true,
  sort_order            integer     NOT NULL DEFAULT 0,
  deleted_at            timestamptz,
  created_at            timestamptz NOT NULL DEFAULT now(),
  updated_at            timestamptz NOT NULL DEFAULT now()
);

-- Meal slots -------------------------------------------------------------------------
-- slots: platform-level templates (Breakfast/Lunch/...). vendor_slots: per-vendor
-- capacity + weekly schedule. Capacity is authoritative in Postgres; Redis locks
-- (§24) are reservations only.
CREATE TABLE IF NOT EXISTS public.slots (
  id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  code                  text        NOT NULL UNIQUE CHECK (code = upper(code)),
  name                  text        NOT NULL,
  starts_at             time        NOT NULL,
  ends_at               time        NOT NULL,
  cutoff_minutes_before integer     NOT NULL DEFAULT 60 CHECK (cutoff_minutes_before >= 0),
  is_active             boolean     NOT NULL DEFAULT true,
  created_at            timestamptz NOT NULL DEFAULT now(),
  updated_at            timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT slots_time_order_check CHECK (starts_at < ends_at)
);

CREATE TABLE IF NOT EXISTS public.vendor_slots (
  id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  vendor_id         uuid        NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  slot_id           uuid        NOT NULL REFERENCES public.slots (id) ON DELETE RESTRICT,
  service_days      integer[]   NOT NULL DEFAULT '{0,1,2,3,4,5,6}',
  capacity_total    integer     NOT NULL CHECK (capacity_total > 0),
  capacity_reserved integer     NOT NULL DEFAULT 0 CHECK (capacity_reserved >= 0),
  is_available      boolean     NOT NULL DEFAULT true,
  effective_from    date,
  effective_to      date,
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now(),
  UNIQUE (vendor_id, slot_id),
  CONSTRAINT vendor_slots_capacity_check CHECK (capacity_reserved <= capacity_total),
  CONSTRAINT vendor_slots_days_check
    CHECK (service_days <@ '{0,1,2,3,4,5,6}'::integer[])
);

-- Service zones (§13 serviceability: city / area / PIN / radius / boundary) ------------
CREATE TABLE IF NOT EXISTS public.service_zones (
  id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  code                text        NOT NULL UNIQUE CHECK (code = upper(code)),
  name                text        NOT NULL,
  city                text        NOT NULL DEFAULT '',
  state               text        NOT NULL DEFAULT '',
  kind                text        NOT NULL DEFAULT 'AREA'
                      CHECK (kind IN ('CITY', 'AREA', 'PIN', 'RADIUS', 'POLYGON')),
  postal_codes        text[]      NOT NULL DEFAULT '{}',
  center_lat          double precision,
  center_lng          double precision,
  radius_km           numeric(6,2) CHECK (radius_km IS NULL OR radius_km > 0),
  geojson             jsonb       NOT NULL DEFAULT '{}'::jsonb,
  delivery_fee_paise  bigint      NOT NULL DEFAULT 0 CHECK (delivery_fee_paise >= 0),
  min_order_paise     bigint      NOT NULL DEFAULT 0 CHECK (min_order_paise >= 0),
  is_active           boolean     NOT NULL DEFAULT true,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.vendor_service_zones (
  vendor_id                  uuid        NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  service_zone_id            uuid        NOT NULL REFERENCES public.service_zones (id) ON DELETE CASCADE,
  custom_delivery_fee_paise  bigint      CHECK (custom_delivery_fee_paise IS NULL OR custom_delivery_fee_paise >= 0),
  is_active                  boolean     NOT NULL DEFAULT true,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (vendor_id, service_zone_id)
);

-- Per-day item availability (recurring when service_date IS NULL) -----------------------
CREATE TABLE IF NOT EXISTS public.menu_item_availability (
  id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  menu_item_id       uuid        NOT NULL REFERENCES public.menu_items (id) ON DELETE CASCADE,
  vendor_slot_id     uuid        REFERENCES public.vendor_slots (id) ON DELETE CASCADE,
  service_date       date,
  quantity_available integer     NOT NULL CHECK (quantity_available >= 0),
  quantity_reserved  integer     NOT NULL DEFAULT 0 CHECK (quantity_reserved >= 0),
  is_available       boolean     NOT NULL DEFAULT true,
  created_at         timestamptz NOT NULL DEFAULT now(),
  updated_at         timestamptz NOT NULL DEFAULT now(),
  UNIQUE (menu_item_id, vendor_slot_id, service_date),
  CONSTRAINT menu_item_availability_reserved_check
    CHECK (quantity_reserved <= quantity_available)
);

-- updated_at triggers ---------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'regions','cuisines','vendors','vendor_verifications','vendor_documents',
    'menus','menu_categories','menu_items','slots','vendor_slots',
    'service_zones','menu_item_availability'
  ] LOOP
    EXECUTE format(
      'DROP TRIGGER IF EXISTS trg_%s_updated_at ON public.%I', t, t);
    EXECUTE format(
      'CREATE TRIGGER trg_%s_updated_at BEFORE UPDATE ON public.%I '
      'FOR EACH ROW EXECUTE FUNCTION public.set_updated_at()', t, t);
  END LOOP;
END;
$$;

COMMENT ON TABLE public.vendor_slots IS
  'Per-vendor slot capacity. DB is authoritative; Redis holds reservation locks only (§24).';
COMMENT ON TABLE public.service_zones IS
  'Serviceability domain data. No city hard-coded in app logic (§18); zones are data.';
