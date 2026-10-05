-- ============================================================================
-- Codewild Food Platform — Migration 0005: Subscription domain
-- ============================================================================
-- Domain owner: SUBSCRIPTION (Meal Plans / Subscription / Daily generation).
-- Tables: meal_plans, subscriptions, subscription_schedules,
--         subscription_daily_orders, subscription_status_history
--
-- Model (§15): a subscription is a recurring CONTRACT, not a delivery. A
-- scheduled generator creates normal operational orders (daily orders link to
-- orders.order_id and flow through standard fulfillment). Pausing a
-- subscription never mutates already-generated orders.
--
-- Idempotency (§10/§25): daily generation is unique on
-- (subscription_id, service_date, meal_slot_id) AND carries its own
-- idempotency_key, so a double-run generator creates exactly ONE order.
--
-- Also attaches the forward FKs promised in 0004:
--   orders.subscription_id, payments.subscription_id -> subscriptions.id
-- ============================================================================

-- Meal plans offered by a vendor ---------------------------------------------------
CREATE TABLE IF NOT EXISTS public.meal_plans (
  id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  vendor_id             uuid        NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  name                  text        NOT NULL,
  description           text        NOT NULL DEFAULT '',
  image_url             text,
  price_per_meal_paise  bigint      NOT NULL CHECK (price_per_meal_paise >= 0),
  price_monthly_paise   bigint      CHECK (price_monthly_paise IS NULL OR price_monthly_paise >= 0),
  currency              text        NOT NULL DEFAULT 'INR',
  meals_per_day         integer     NOT NULL DEFAULT 1 CHECK (meals_per_day > 0),
  trial_available       boolean     NOT NULL DEFAULT false,
  applicable_days       integer[]   NOT NULL DEFAULT '{0,1,2,3,4,5,6}',
  is_active             boolean     NOT NULL DEFAULT true,
  created_at            timestamptz NOT NULL DEFAULT now(),
  updated_at            timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT meal_plans_days_check
    CHECK (applicable_days <@ '{0,1,2,3,4,5,6}'::integer[])
);

-- Subscriptions (the contract) --------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.subscriptions (
  id                      uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  subscription_number     text        NOT NULL UNIQUE,
  idempotency_key         text        NOT NULL UNIQUE,
  customer_id             uuid        NOT NULL REFERENCES public.customer_profiles (id) ON DELETE RESTRICT,
  vendor_id               uuid        NOT NULL REFERENCES public.vendors (id) ON DELETE RESTRICT,
  meal_plan_id            uuid        NOT NULL REFERENCES public.meal_plans (id) ON DELETE RESTRICT,
  address_id              uuid        REFERENCES public.addresses (id) ON DELETE SET NULL,
  status                  text        NOT NULL DEFAULT 'DRAFT'
                          CHECK (status IN (
                            'DRAFT', 'PAYMENT_PENDING', 'ACTIVE', 'PAUSED',
                            'SKIPPED', 'EXPIRED', 'CANCELLED', 'PAYMENT_FAILED')),
  start_date              date        NOT NULL,
  end_date                date        NOT NULL,
  total_meals             integer     NOT NULL CHECK (total_meals > 0),
  meals_consumed          integer     NOT NULL DEFAULT 0 CHECK (meals_consumed >= 0),
  meals_skipped           integer     NOT NULL DEFAULT 0 CHECK (meals_skipped >= 0),
  -- Frozen pricing at activation (§11)
  price_snapshot          jsonb       NOT NULL DEFAULT '{}'::jsonb,
  total_amount_paise      bigint      NOT NULL CHECK (total_amount_paise >= 0),
  amount_paid_paise       bigint      NOT NULL DEFAULT 0 CHECK (amount_paid_paise >= 0),
  currency                text        NOT NULL DEFAULT 'INR',
  delivery_preferences    jsonb       NOT NULL DEFAULT '{}'::jsonb,
  skip_dates              date[]      NOT NULL DEFAULT '{}',
  paused_from             date,
  paused_to               date,
  cancelled_at            timestamptz,
  cancellation_reason     text,
  created_at              timestamptz NOT NULL DEFAULT now(),
  updated_at              timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT subscriptions_date_order_check CHECK (end_date >= start_date),
  CONSTRAINT subscriptions_pause_window_check
    CHECK ((paused_from IS NULL AND paused_to IS NULL)
        OR (paused_from IS NOT NULL AND paused_to IS NOT NULL
            AND paused_to >= paused_from))
);

-- Weekly schedule: which slots fire on which weekdays -------------------------------------
CREATE TABLE IF NOT EXISTS public.subscription_schedules (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  subscription_id uuid        NOT NULL REFERENCES public.subscriptions (id) ON DELETE CASCADE,
  weekday         integer     NOT NULL CHECK (weekday >= 0 AND weekday <= 6),
  meal_slot_id    uuid        NOT NULL REFERENCES public.slots (id) ON DELETE RESTRICT,
  quantity        integer     NOT NULL DEFAULT 1 CHECK (quantity > 0),
  is_active       boolean     NOT NULL DEFAULT true,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (subscription_id, weekday, meal_slot_id)
);

-- Generated daily orders (generation log + link to operational order) -----------------------
CREATE TABLE IF NOT EXISTS public.subscription_daily_orders (
  id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  subscription_id       uuid        NOT NULL REFERENCES public.subscriptions (id) ON DELETE CASCADE,
  order_id              uuid        UNIQUE REFERENCES public.orders (id) ON DELETE SET NULL,
  service_date          date        NOT NULL,
  meal_slot_id          uuid        NOT NULL REFERENCES public.slots (id) ON DELETE RESTRICT,
  status                text        NOT NULL DEFAULT 'SCHEDULED'
                        CHECK (status IN (
                          'SCHEDULED', 'GENERATED', 'SKIPPED', 'FAILED', 'CANCELLED')),
  idempotency_key       text        NOT NULL UNIQUE,
  failure_reason        text,
  generated_at          timestamptz,
  created_at            timestamptz NOT NULL DEFAULT now(),
  updated_at            timestamptz NOT NULL DEFAULT now(),
  -- THE anti-duplicate guarantee (§10): one row per subscription/day/slot.
  UNIQUE (subscription_id, service_date, meal_slot_id)
);

-- Subscription status history (append-only) ---------------------------------------------------
CREATE TABLE IF NOT EXISTS public.subscription_status_history (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  subscription_id uuid        NOT NULL REFERENCES public.subscriptions (id) ON DELETE CASCADE,
  from_status     text,
  to_status       text        NOT NULL,
  changed_by      uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  actor_role      text,
  change_reason   text,
  idempotency_key text        UNIQUE,
  created_at      timestamptz NOT NULL DEFAULT now()
);

-- Attach forward FKs promised in 0004 ------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_orders_subscription') THEN
    ALTER TABLE public.orders
      ADD CONSTRAINT fk_orders_subscription
      FOREIGN KEY (subscription_id)
      REFERENCES public.subscriptions (id) ON DELETE SET NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_payments_subscription') THEN
    ALTER TABLE public.payments
      ADD CONSTRAINT fk_payments_subscription
      FOREIGN KEY (subscription_id)
      REFERENCES public.subscriptions (id) ON DELETE SET NULL;
  END IF;
END;
$$;

-- Prevent two concurrently billable subscriptions for the same customer/vendor/plan ---------------
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriptions_single_live
  ON public.subscriptions (customer_id, vendor_id, meal_plan_id)
  WHERE status IN ('ACTIVE', 'PAUSED');

-- updated_at triggers ------------------------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'meal_plans','subscriptions','subscription_schedules','subscription_daily_orders'
  ] LOOP
    EXECUTE format(
      'DROP TRIGGER IF EXISTS trg_%s_updated_at ON public.%I', t, t);
    EXECUTE format(
      'CREATE TRIGGER trg_%s_updated_at BEFORE UPDATE ON public.%I '
      'FOR EACH ROW EXECUTE FUNCTION public.set_updated_at()', t, t);
  END LOOP;
END;
$$;

COMMENT ON TABLE public.subscription_daily_orders IS
  'Idempotent generation log. UNIQUE(subscription_id, service_date, meal_slot_id) guarantees one daily order even on double-run.';
