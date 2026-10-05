-- ============================================================================
-- Codewild Food Platform — Migration 0004: Commerce domain
-- ============================================================================
-- Domain owner: COMMERCE (Cart / Pricing / Order / Payment / Refund).
-- Tables: coupons, coupon_usages, carts, cart_items, orders, order_items,
--         order_status_history, payments, payment_attempts, refunds
--
-- Finance-snapshot rule (§11): orders freeze every money-affecting value at
-- creation (item names/prices, discounts, taxes, fees, commission). Historical
-- reports must NEVER re-price from today's menu/commission/tax tables.
--
-- Forward references: orders.subscription_id / payments.subscription_id point
-- at subscriptions (0005). Columns are created here as nullable UUID WITHOUT
-- FKs; 0005 attaches the constraints via ALTER TABLE.
-- Idempotency (§30): orders, payments, payment_attempts and refunds each carry
-- a UNIQUE idempotency_key supplied by the backend on first attempt.
-- ============================================================================

-- Coupons -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.coupons (
  id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  code                  citext      NOT NULL UNIQUE,
  title                 text        NOT NULL,
  description           text        NOT NULL DEFAULT '',
  discount_type         text        NOT NULL
                        CHECK (discount_type IN ('PERCENT', 'FLAT', 'FREE_DELIVERY')),
  discount_bps          integer     CHECK (discount_bps IS NULL OR (discount_bps > 0 AND discount_bps <= 10000)),
  discount_paise        bigint      CHECK (discount_paise IS NULL OR discount_paise >= 0),
  max_discount_paise    bigint      CHECK (max_discount_paise IS NULL OR max_discount_paise >= 0),
  min_order_paise       bigint      NOT NULL DEFAULT 0 CHECK (min_order_paise >= 0),
  usage_limit_total     integer     CHECK (usage_limit_total IS NULL OR usage_limit_total > 0),
  usage_limit_per_user  integer     NOT NULL DEFAULT 1 CHECK (usage_limit_per_user > 0),
  used_count            integer     NOT NULL DEFAULT 0 CHECK (used_count >= 0),
  applicable_vendor_ids uuid[]      NOT NULL DEFAULT '{}',
  valid_from            timestamptz,
  valid_to              timestamptz,
  is_active             boolean     NOT NULL DEFAULT true,
  created_at            timestamptz NOT NULL DEFAULT now(),
  updated_at            timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT coupons_window_check
    CHECK (valid_from IS NULL OR valid_to IS NULL OR valid_from <= valid_to)
);

-- Carts (single-vendor rule enforced server-side + by partial unique index) ------
CREATE TABLE IF NOT EXISTS public.carts (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id uuid        NOT NULL REFERENCES public.customer_profiles (id) ON DELETE CASCADE,
  vendor_id   uuid        NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  slot_id     uuid        REFERENCES public.slots (id) ON DELETE SET NULL,
  address_id  uuid        REFERENCES public.addresses (id) ON DELETE SET NULL,
  coupon_id   uuid        REFERENCES public.coupons (id) ON DELETE SET NULL,
  status      text        NOT NULL DEFAULT 'ACTIVE'
              CHECK (status IN ('ACTIVE', 'CHECKED_OUT', 'ABANDONED', 'EXPIRED')),
  expires_at  timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);
-- One ACTIVE cart per (customer, vendor): partial unique index lives in 0008.

CREATE TABLE IF NOT EXISTS public.cart_items (
  id                          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  cart_id                     uuid        NOT NULL REFERENCES public.carts (id) ON DELETE CASCADE,
  menu_item_id                uuid        NOT NULL REFERENCES public.menu_items (id) ON DELETE RESTRICT,
  quantity                    integer     NOT NULL CHECK (quantity > 0 AND quantity <= 99),
  unit_price_paise_snapshot   bigint      NOT NULL CHECK (unit_price_paise_snapshot >= 0),
  customization_hash          text        NOT NULL DEFAULT '',
  customizations              jsonb       NOT NULL DEFAULT '{}'::jsonb,
  created_at                  timestamptz NOT NULL DEFAULT now(),
  updated_at                  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (cart_id, menu_item_id, customization_hash)
);

-- Orders --------------------------------------------------------------------------
-- Status machine (§9/§14/§22): server transitions only; every change appends to
-- order_status_history. Clients may never write status directly.
CREATE TABLE IF NOT EXISTS public.orders (
  id                                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  order_number                      text        NOT NULL UNIQUE,
  idempotency_key                   text        NOT NULL UNIQUE,
  customer_id                       uuid        NOT NULL REFERENCES public.customer_profiles (id) ON DELETE RESTRICT,
  vendor_id                         uuid        NOT NULL REFERENCES public.vendors (id) ON DELETE RESTRICT,
  address_id                        uuid        REFERENCES public.addresses (id) ON DELETE SET NULL,
  slot_id                           uuid        REFERENCES public.slots (id) ON DELETE SET NULL,
  service_date                      date,
  subscription_id                   uuid,
  subscription_schedule_id          uuid,
  coupon_id                         uuid        REFERENCES public.coupons (id) ON DELETE SET NULL,
  status                            text        NOT NULL DEFAULT 'CREATED'
                                    CHECK (status IN (
                                      'CREATED', 'PAYMENT_PENDING', 'PAYMENT_CONFIRMED',
                                      'PAYMENT_FAILED', 'PLACED', 'VENDOR_PENDING',
                                      'VENDOR_ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP',
                                      'RIDER_ASSIGNED', 'RIDER_ACCEPTED', 'PICKED_UP',
                                      'OUT_FOR_DELIVERY', 'ARRIVED', 'DELIVERED',
                                      'CUSTOMER_CONFIRMED', 'COMPLETED',
                                      'CANCEL_REQUESTED', 'CANCELLED',
                                      'REFUND_PENDING', 'REFUNDED', 'FAILED')),
  payment_status                    text        NOT NULL DEFAULT 'PENDING'
                                    CHECK (payment_status IN (
                                      'PENDING', 'INITIATED', 'SUCCESS', 'FAILED',
                                      'CANCELLED', 'REFUND_PENDING',
                                      'PARTIALLY_REFUNDED', 'REFUNDED')),
  -- ---- Financial snapshot (frozen at creation, §11) ----
  subtotal_paise                    bigint      NOT NULL CHECK (subtotal_paise >= 0),
  discount_paise                    bigint      NOT NULL DEFAULT 0 CHECK (discount_paise >= 0),
  tax_paise                         bigint      NOT NULL DEFAULT 0 CHECK (tax_paise >= 0),
  delivery_fee_paise                bigint      NOT NULL DEFAULT 0 CHECK (delivery_fee_paise >= 0),
  platform_fee_paise                bigint      NOT NULL DEFAULT 0 CHECK (platform_fee_paise >= 0),
  packaging_fee_paise               bigint      NOT NULL DEFAULT 0 CHECK (packaging_fee_paise >= 0),
  total_paise                       bigint      NOT NULL CHECK (total_paise >= 0),
  currency                          text        NOT NULL DEFAULT 'INR',
  commission_bps_snapshot           integer     NOT NULL CHECK (commission_bps_snapshot >= 0 AND commission_bps_snapshot <= 10000),
  platform_commission_paise_snapshot bigint     NOT NULL DEFAULT 0 CHECK (platform_commission_paise_snapshot >= 0),
  vendor_payout_paise_snapshot      bigint      NOT NULL DEFAULT 0 CHECK (vendor_payout_paise_snapshot >= 0),
  rider_payout_paise_snapshot       bigint      CHECK (rider_payout_paise_snapshot IS NULL OR rider_payout_paise_snapshot >= 0),
  coupon_code_snapshot              text,
  -- ---- Frozen operational context ----
  delivery_address_snapshot         jsonb       NOT NULL,
  item_count                        integer     NOT NULL DEFAULT 0 CHECK (item_count >= 0),
  customer_note                     text,
  vendor_note                       text,
  cancellation_reason               text,
  cancelled_by                      uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  cancelled_at                      timestamptz,
  estimated_ready_at                timestamptz,
  estimated_delivery_at             timestamptz,
  delivered_at                      timestamptz,
  completed_at                      timestamptz,
  payment_due_at                    timestamptz,
  metadata                          jsonb       NOT NULL DEFAULT '{}'::jsonb,
  created_at                        timestamptz NOT NULL DEFAULT now(),
  updated_at                        timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT orders_total_consistency_check
    CHECK (total_paise = subtotal_paise - discount_paise + tax_paise
           + delivery_fee_paise + platform_fee_paise + packaging_fee_paise
           AND subtotal_paise >= discount_paise)
);

-- Order line items: immutable snapshots (§11) ---------------------------------------
CREATE TABLE IF NOT EXISTS public.order_items (
  id                          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id                    uuid        NOT NULL REFERENCES public.orders (id) ON DELETE CASCADE,
  menu_item_id                uuid        REFERENCES public.menu_items (id) ON DELETE SET NULL,
  vendor_id                   uuid        NOT NULL REFERENCES public.vendors (id) ON DELETE RESTRICT,
  item_name_snapshot          text        NOT NULL,
  item_description_snapshot   text        NOT NULL DEFAULT '',
  unit_price_paise_snapshot   bigint      NOT NULL CHECK (unit_price_paise_snapshot >= 0),
  mrp_paise_snapshot          bigint      CHECK (mrp_paise_snapshot IS NULL OR mrp_paise_snapshot >= 0),
  quantity                    integer     NOT NULL CHECK (quantity > 0),
  discount_paise_snapshot     bigint      NOT NULL DEFAULT 0 CHECK (discount_paise_snapshot >= 0),
  tax_paise_snapshot          bigint      NOT NULL DEFAULT 0 CHECK (tax_paise_snapshot >= 0),
  line_total_paise            bigint      NOT NULL CHECK (line_total_paise >= 0),
  cuisine_tags_snapshot       text[]      NOT NULL DEFAULT '{}',
  customization_snapshot      jsonb       NOT NULL DEFAULT '{}'::jsonb,
  created_at                  timestamptz NOT NULL DEFAULT now(),
  updated_at                  timestamptz NOT NULL DEFAULT now()
);

-- Status history: append-only ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.order_status_history (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id        uuid        NOT NULL REFERENCES public.orders (id) ON DELETE CASCADE,
  from_status     text,
  to_status       text        NOT NULL,
  changed_by      uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  actor_role      text,
  change_reason   text,
  idempotency_key text        UNIQUE,
  created_at      timestamptz NOT NULL DEFAULT now()
);

-- Payments ------------------------------------------------------------------------------
-- Frontend callbacks are hints only; SUCCESS requires server verification +
-- webhook (§16/§17). Webhook processing is idempotent on provider_payment_id
-- and on idempotency_key.
CREATE TABLE IF NOT EXISTS public.payments (
  id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id            uuid        REFERENCES public.orders (id) ON DELETE RESTRICT,
  subscription_id     uuid,
  customer_id         uuid        NOT NULL REFERENCES public.customer_profiles (id) ON DELETE RESTRICT,
  provider            text        NOT NULL
                      CHECK (provider IN ('RAZORPAY', 'CASHFREE', 'STRIPE', 'UPI', 'COD', 'INTERNAL')),
  provider_payment_id text        UNIQUE,
  provider_order_id   text,
  idempotency_key     text        NOT NULL UNIQUE,
  amount_paise        bigint      NOT NULL CHECK (amount_paise >= 0),
  currency            text        NOT NULL DEFAULT 'INR',
  method              text        NOT NULL
                      CHECK (method IN ('UPI', 'CARD', 'NETBANKING', 'WALLET', 'COD', 'BANK_TRANSFER')),
  status              text        NOT NULL DEFAULT 'CREATED'
                      CHECK (status IN (
                        'CREATED', 'INITIATED', 'PENDING', 'SUCCESS', 'FAILED',
                        'CANCELLED', 'REFUND_PENDING', 'PARTIALLY_REFUNDED', 'REFUNDED')),
  verified_at         timestamptz,
  failure_code        text,
  failure_message     text,
  webhook_payload     jsonb       NOT NULL DEFAULT '{}'::jsonb,
  metadata            jsonb       NOT NULL DEFAULT '{}'::jsonb,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT payments_subject_check
    CHECK (order_id IS NOT NULL OR subscription_id IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS public.payment_attempts (
  id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  payment_id        uuid        NOT NULL REFERENCES public.payments (id) ON DELETE CASCADE,
  attempt_no        integer     NOT NULL CHECK (attempt_no > 0),
  provider_reference text,
  status            text        NOT NULL
                    CHECK (status IN ('INITIATED', 'PENDING', 'SUCCESS', 'FAILED', 'CANCELLED')),
  request_payload   jsonb       NOT NULL DEFAULT '{}'::jsonb,
  response_payload  jsonb       NOT NULL DEFAULT '{}'::jsonb,
  idempotency_key   text        NOT NULL UNIQUE,
  error_code        text,
  created_at        timestamptz NOT NULL DEFAULT now(),
  UNIQUE (payment_id, attempt_no)
);

-- Refunds (§46): eligibility + amount computed server-side; auditable ---------------
CREATE TABLE IF NOT EXISTS public.refunds (
  id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id            uuid        NOT NULL REFERENCES public.orders (id) ON DELETE RESTRICT,
  payment_id          uuid        NOT NULL REFERENCES public.payments (id) ON DELETE RESTRICT,
  idempotency_key     text        NOT NULL UNIQUE,
  amount_paise        bigint      NOT NULL CHECK (amount_paise > 0),
  currency            text        NOT NULL DEFAULT 'INR',
  reason              text        NOT NULL DEFAULT '',
  status              text        NOT NULL DEFAULT 'REQUESTED'
                      CHECK (status IN (
                        'REQUESTED', 'APPROVED', 'PROCESSING', 'COMPLETED',
                        'FAILED', 'REJECTED', 'CANCELLED')),
  provider_refund_id  text        UNIQUE,
  requested_by        uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  approved_by         uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  processed_at        timestamptz,
  failure_reason      text,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.coupon_usages (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  coupon_id       uuid        NOT NULL REFERENCES public.coupons (id) ON DELETE RESTRICT,
  order_id        uuid        NOT NULL UNIQUE REFERENCES public.orders (id) ON DELETE CASCADE,
  customer_id     uuid        NOT NULL REFERENCES public.customer_profiles (id) ON DELETE CASCADE,
  discount_paise  bigint      NOT NULL CHECK (discount_paise >= 0),
  created_at      timestamptz NOT NULL DEFAULT now()
);

-- updated_at triggers (mutable tables only; history tables are append-only) -----------
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'coupons','carts','cart_items','orders','order_items','payments','refunds'
  ] LOOP
    EXECUTE format(
      'DROP TRIGGER IF EXISTS trg_%s_updated_at ON public.%I', t, t);
    EXECUTE format(
      'CREATE TRIGGER trg_%s_updated_at BEFORE UPDATE ON public.%I '
      'FOR EACH ROW EXECUTE FUNCTION public.set_updated_at()', t, t);
  END LOOP;
END;
$$;

COMMENT ON TABLE public.orders IS
  'COMMERCE domain. All money columns are frozen snapshots (§11); state changes via server-side machine + order_status_history.';
COMMENT ON TABLE public.payments IS
  'COMMERCE/FINANCE. SUCCESS only after server-side verification/webhook; webhook handling idempotent.';
