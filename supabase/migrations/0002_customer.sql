-- ============================================================================
-- Codewild Food Platform — Migration 0002: Customer domain
-- ============================================================================
-- Domain owner: CUSTOMER (Profile / Address / Preferences / Discovery)
-- Tables: customer_profiles, addresses
--
-- NOTE on hometown_region_id: the Region taxonomy is created in 0003
-- (marketplace). The column is added here as nullable UUID WITHOUT a foreign
-- key; 0003_marketplace.sql attaches the FK via ALTER TABLE once regions
-- exists. Preferences are discovery metadata, never assumptions (§19).
-- ============================================================================

-- Customer profile -----------------------------------------------------------
-- One profile per platform user acting as a customer.
CREATE TABLE IF NOT EXISTS public.customer_profiles (
  id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id               uuid        NOT NULL UNIQUE
                        REFERENCES public.platform_users (id) ON DELETE CASCADE,
  full_name             text        NOT NULL DEFAULT '',
  avatar_url            text,
  hometown_region_id    uuid,
  preferred_region_ids  uuid[]      NOT NULL DEFAULT '{}',
  preferred_cuisines    text[]      NOT NULL DEFAULT '{}',
  dietary_preferences   text[]      NOT NULL DEFAULT '{}',
  preferred_language    text        NOT NULL DEFAULT 'en',
  date_of_birth         date,
  is_active             boolean     NOT NULL DEFAULT true,
  created_at            timestamptz NOT NULL DEFAULT now(),
  updated_at            timestamptz NOT NULL DEFAULT now()
);

-- Delivery addresses ----------------------------------------------------------
-- A customer may hold many addresses; exactly one may be default (enforced by
-- partial unique index in 0008). Address is snapshotted into orders at purchase
-- time (see orders.delivery_address_snapshot) so history survives edits here.
CREATE TABLE IF NOT EXISTS public.addresses (
  id                     uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id            uuid        NOT NULL
                         REFERENCES public.customer_profiles (id) ON DELETE CASCADE,
  label                  text        NOT NULL DEFAULT 'Home',
  address_type           text        NOT NULL DEFAULT 'HOME'
                         CHECK (address_type IN ('HOME', 'WORK', 'OTHER')),
  house_flat             text        NOT NULL DEFAULT '',
  street                 text        NOT NULL DEFAULT '',
  landmark               text,
  area                   text        NOT NULL DEFAULT '',
  city                   text        NOT NULL DEFAULT '',
  state                  text        NOT NULL DEFAULT '',
  postal_code            text        NOT NULL DEFAULT '',
  latitude               double precision,
  longitude              double precision,
  delivery_instructions  text,
  is_default             boolean     NOT NULL DEFAULT false,
  is_active              boolean     NOT NULL DEFAULT true,
  created_at             timestamptz NOT NULL DEFAULT now(),
  updated_at             timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT addresses_geo_pair_check
    CHECK ((latitude IS NULL AND longitude IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL)),
  CONSTRAINT addresses_lat_range_check
    CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
  CONSTRAINT addresses_lng_range_check
    CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180))
);

DROP TRIGGER IF EXISTS trg_customer_profiles_updated_at ON public.customer_profiles;
CREATE TRIGGER trg_customer_profiles_updated_at
  BEFORE UPDATE ON public.customer_profiles
  FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

DROP TRIGGER IF EXISTS trg_addresses_updated_at ON public.addresses;
CREATE TRIGGER trg_addresses_updated_at
  BEFORE UPDATE ON public.addresses
  FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

COMMENT ON TABLE public.customer_profiles IS
  'CUSTOMER domain. hometown/preferences are discovery filters, not assumptions.';
COMMENT ON TABLE public.addresses IS
  'CUSTOMER domain. Live address book; orders freeze a snapshot at purchase time.';
