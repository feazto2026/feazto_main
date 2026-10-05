-- ============================================================================
-- Codewild Food Platform — Migration 0007: Operations (+ Finance ledger,
-- Reviews, Outbox, Idempotency)
-- ============================================================================
-- Domain owners:
--   OPERATIONS : support_tickets(+messages), audit_logs, notifications,
--                notification_preferences, reviews
--   FINANCE    : commissions, vendor_payouts(+items), rider_payouts(+items)
--                (colocated here because every FK — orders, vendors, riders,
--                deliveries — already exists at this migration step)
--   SHARED     : outbox_events (transactional event relay, §13),
--                idempotency_keys (durable idempotency record, §30)
--
-- Immutability: audit_logs, outbox_events and commission rows are append-only
-- (no updated_at, no UPDATE policy — enforced in 0008 via RLS + docs).
-- ============================================================================

-- ============================ SUPPORT ========================================

CREATE TABLE IF NOT EXISTS public.support_tickets (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_number   text        NOT NULL UNIQUE,
  requester_id    uuid        NOT NULL REFERENCES public.platform_users (id) ON DELETE RESTRICT,
  requester_role  text        NOT NULL DEFAULT 'CUSTOMER',
  order_id        uuid        REFERENCES public.orders (id) ON DELETE SET NULL,
  delivery_id     uuid        REFERENCES public.deliveries (id) ON DELETE SET NULL,
  subject         text        NOT NULL,
  description     text        NOT NULL DEFAULT '',
  category        text        NOT NULL DEFAULT 'OTHER'
                  CHECK (category IN (
                    'ORDER', 'PAYMENT', 'DELIVERY', 'SUBSCRIPTION',
                    'VENDOR', 'RIDER', 'ACCOUNT', 'OTHER')),
  priority        text        NOT NULL DEFAULT 'MEDIUM'
                  CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
  status          text        NOT NULL DEFAULT 'OPEN'
                  CHECK (status IN (
                    'OPEN', 'ASSIGNED', 'IN_PROGRESS', 'WAITING_ON_CUSTOMER',
                    'RESOLVED', 'CLOSED', 'REOPENED')),
  assigned_to     uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  resolution_note text,
  resolved_at     timestamptz,
  sla_due_at      timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.support_ticket_messages (
  id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_id     uuid        NOT NULL REFERENCES public.support_tickets (id) ON DELETE CASCADE,
  sender_id     uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  message       text        NOT NULL,
  attachments   jsonb       NOT NULL DEFAULT '[]'::jsonb,
  is_internal   boolean     NOT NULL DEFAULT false,
  created_at    timestamptz NOT NULL DEFAULT now()
);

-- ============================ AUDIT ===========================================
-- Append-only. Sensitive admin actions (§28): approve/reject/suspend vendor,
-- refund/cancel order, commission or zone changes, rider deactivation, etc.
CREATE TABLE IF NOT EXISTS public.audit_logs (
  id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_id      uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  actor_role    text,
  action        text        NOT NULL,
  entity_type   text        NOT NULL,
  entity_id     uuid,
  old_value     jsonb       NOT NULL DEFAULT '{}'::jsonb,
  new_value     jsonb       NOT NULL DEFAULT '{}'::jsonb,
  reason        text,
  ip_address    inet,
  user_agent    text,
  request_id    text,
  created_at    timestamptz NOT NULL DEFAULT now()
);

-- ========================= NOTIFICATIONS =======================================
CREATE TABLE IF NOT EXISTS public.notifications (
  id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  recipient_user_id uuid        NOT NULL REFERENCES public.platform_users (id) ON DELETE CASCADE,
  channel           text        NOT NULL
                    CHECK (channel IN ('PUSH', 'SMS', 'EMAIL', 'IN_APP')),
  template_code     text        NOT NULL DEFAULT 'GENERIC',
  title             text        NOT NULL DEFAULT '',
  body              text        NOT NULL DEFAULT '',
  data              jsonb       NOT NULL DEFAULT '{}'::jsonb,
  status            text        NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN (
                      'PENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED', 'CANCELLED')),
  dedupe_key        text        UNIQUE,
  sent_at           timestamptz,
  read_at           timestamptz,
  failure_reason    text,
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.notification_preferences (
  user_id       uuid        PRIMARY KEY REFERENCES public.platform_users (id) ON DELETE CASCADE,
  push_enabled  boolean     NOT NULL DEFAULT true,
  sms_enabled   boolean     NOT NULL DEFAULT true,
  email_enabled boolean     NOT NULL DEFAULT true,
  locale        text        NOT NULL DEFAULT 'en',
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now()
);

-- ============================ REVIEWS ==========================================
-- One review per order by default (§47: prevent duplicates unless supported).
CREATE TABLE IF NOT EXISTS public.reviews (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id        uuid        NOT NULL UNIQUE REFERENCES public.orders (id) ON DELETE CASCADE,
  customer_id     uuid        NOT NULL REFERENCES public.customer_profiles (id) ON DELETE CASCADE,
  vendor_id       uuid        NOT NULL REFERENCES public.vendors (id) ON DELETE CASCADE,
  rider_id        uuid        REFERENCES public.riders (id) ON DELETE SET NULL,
  delivery_id     uuid        REFERENCES public.deliveries (id) ON DELETE SET NULL,
  vendor_rating   smallint    CHECK (vendor_rating IS NULL OR (vendor_rating >= 1 AND vendor_rating <= 5)),
  food_rating     smallint    CHECK (food_rating IS NULL OR (food_rating >= 1 AND food_rating <= 5)),
  delivery_rating smallint    CHECK (delivery_rating IS NULL OR (delivery_rating >= 1 AND delivery_rating <= 5)),
  comment         text,
  images          jsonb       NOT NULL DEFAULT '[]'::jsonb,
  status          text        NOT NULL DEFAULT 'PUBLISHED'
                  CHECK (status IN ('PUBLISHED', 'HIDDEN', 'FLAGGED', 'REMOVED')),
  moderated_by    uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  moderated_at    timestamptz,
  moderation_reason text,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT reviews_at_least_one_rating
    CHECK (vendor_rating IS NOT NULL OR food_rating IS NOT NULL OR delivery_rating IS NOT NULL)
);

-- ============================ FINANCE LEDGER ====================================
-- Finance domain. Commission accrues per order from the order's frozen snapshot;
-- payouts settle aggregates. Rows are financial records — never UPDATE in place
-- for corrections; reverse + re-accrue (REVERSED status + audit log).

CREATE TABLE IF NOT EXISTS public.commissions (
  id                          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id                    uuid        NOT NULL UNIQUE REFERENCES public.orders (id) ON DELETE RESTRICT,
  vendor_id                   uuid        NOT NULL REFERENCES public.vendors (id) ON DELETE RESTRICT,
  basis_amount_paise          bigint      NOT NULL CHECK (basis_amount_paise >= 0),
  commission_bps_snapshot     integer     NOT NULL CHECK (commission_bps_snapshot >= 0 AND commission_bps_snapshot <= 10000),
  commission_amount_paise      bigint      NOT NULL CHECK (commission_amount_paise >= 0),
  tax_on_commission_paise     bigint      NOT NULL DEFAULT 0 CHECK (tax_on_commission_paise >= 0),
  net_vendor_share_paise      bigint      NOT NULL CHECK (net_vendor_share_paise >= 0),
  currency                    text        NOT NULL DEFAULT 'INR',
  status                      text        NOT NULL DEFAULT 'ACCRUED'
                              CHECK (status IN ('ACCRUED', 'SETTLED', 'REVERSED')),
  accrued_at                  timestamptz NOT NULL DEFAULT now(),
  reversed_at                 timestamptz,
  reversal_reason             text,
  created_at                  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.vendor_payouts (
  id                        uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  payout_number             text        NOT NULL UNIQUE,
  vendor_id                 uuid        NOT NULL REFERENCES public.vendors (id) ON DELETE RESTRICT,
  period_start              date        NOT NULL,
  period_end                date        NOT NULL,
  gross_amount_paise        bigint      NOT NULL CHECK (gross_amount_paise >= 0),
  commission_deducted_paise  bigint      NOT NULL DEFAULT 0 CHECK (commission_deducted_paise >= 0),
  refunds_deducted_paise    bigint      NOT NULL DEFAULT 0 CHECK (refunds_deducted_paise >= 0),
  adjustments_paise         bigint      NOT NULL DEFAULT 0,
  net_amount_paise          bigint      NOT NULL,
  currency                  text        NOT NULL DEFAULT 'INR',
  status                    text        NOT NULL DEFAULT 'DRAFT'
                            CHECK (status IN (
                              'DRAFT', 'SCHEDULED', 'PROCESSING',
                              'COMPLETED', 'FAILED', 'CANCELLED')),
  provider_reference        text        UNIQUE,
  idempotency_key           text        NOT NULL UNIQUE,
  initiated_by              uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  approved_by               uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  failure_reason            text,
  processed_at              timestamptz,
  created_at                timestamptz NOT NULL DEFAULT now(),
  updated_at                timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT vendor_payouts_period_check CHECK (period_end >= period_start)
);

CREATE TABLE IF NOT EXISTS public.vendor_payout_items (
  id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  payout_id     uuid        NOT NULL REFERENCES public.vendor_payouts (id) ON DELETE CASCADE,
  order_id      uuid        NOT NULL REFERENCES public.orders (id) ON DELETE RESTRICT,
  commission_id uuid        REFERENCES public.commissions (id) ON DELETE SET NULL,
  amount_paise  bigint      NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (payout_id, order_id)
);

CREATE TABLE IF NOT EXISTS public.rider_payouts (
  id                     uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  payout_number          text        NOT NULL UNIQUE,
  rider_id               uuid        NOT NULL REFERENCES public.riders (id) ON DELETE RESTRICT,
  period_start           date        NOT NULL,
  period_end             date        NOT NULL,
  gross_amount_paise     bigint      NOT NULL CHECK (gross_amount_paise >= 0),
  adjustments_paise      bigint      NOT NULL DEFAULT 0,
  net_amount_paise       bigint      NOT NULL,
  currency               text        NOT NULL DEFAULT 'INR',
  status                 text        NOT NULL DEFAULT 'DRAFT'
                         CHECK (status IN (
                           'DRAFT', 'SCHEDULED', 'PROCESSING',
                           'COMPLETED', 'FAILED', 'CANCELLED')),
  provider_reference     text        UNIQUE,
  idempotency_key        text        NOT NULL UNIQUE,
  initiated_by           uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  approved_by            uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  failure_reason         text,
  processed_at           timestamptz,
  created_at             timestamptz NOT NULL DEFAULT now(),
  updated_at             timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT rider_payouts_period_check CHECK (period_end >= period_start)
);

CREATE TABLE IF NOT EXISTS public.rider_payout_items (
  id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  payout_id     uuid        NOT NULL REFERENCES public.rider_payouts (id) ON DELETE CASCADE,
  delivery_id   uuid        NOT NULL REFERENCES public.deliveries (id) ON DELETE RESTRICT,
  amount_paise  bigint      NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (payout_id, delivery_id)
);

-- ============================ OUTBOX =============================================
-- Transactional relay (§13): business row + outbox row commit atomically; a
-- background publisher relays PENDING events to Redis/notifications/realtime.
CREATE TABLE IF NOT EXISTS public.outbox_events (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  aggregate_type  text        NOT NULL,
  aggregate_id    uuid        NOT NULL,
  event_type      text        NOT NULL,
  payload         jsonb       NOT NULL,
  headers         jsonb       NOT NULL DEFAULT '{}'::jsonb,
  status          text        NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER')),
  attempts        integer     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  next_attempt_at timestamptz NOT NULL DEFAULT now(),
  published_at    timestamptz,
  error           text,
  idempotency_key text        NOT NULL UNIQUE,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);

-- ========================= IDEMPOTENCY ==============================================
-- Durable idempotency record (§30) backing Redis short-term keys. Backend flow:
-- insert IN_PROGRESS on first attempt (UNIQUE(scope,key) collapses retries),
-- store the response, replay it for duplicates.
CREATE TABLE IF NOT EXISTS public.idempotency_keys (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  scope           text        NOT NULL,
  key             text        NOT NULL,
  user_id         uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  status          text        NOT NULL DEFAULT 'IN_PROGRESS'
                  CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
  response_code   integer,
  response_body   jsonb       NOT NULL DEFAULT '{}'::jsonb,
  locked_at       timestamptz NOT NULL DEFAULT now(),
  expires_at      timestamptz NOT NULL DEFAULT (now() + interval '24 hours'),
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (scope, key)
);

-- updated_at triggers (mutable tables only) --------------------------------------------------
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'support_tickets','notifications','notification_preferences','reviews',
    'vendor_payouts','rider_payouts','outbox_events','idempotency_keys'
  ] LOOP
    EXECUTE format(
      'DROP TRIGGER IF EXISTS trg_%s_updated_at ON public.%I', t, t);
    EXECUTE format(
      'CREATE TRIGGER trg_%s_updated_at BEFORE UPDATE ON public.%I '
      'FOR EACH ROW EXECUTE FUNCTION public.set_updated_at()', t, t);
  END LOOP;
END;
$$;

COMMENT ON TABLE public.audit_logs IS
  'OPERATIONS. Append-only trail of privileged actions with reason + actor + request metadata.';
COMMENT ON TABLE public.commissions IS
  'FINANCE. Per-order commission accrued from frozen order snapshot; correct via REVERSED + re-accrue, never UPDATE.';
COMMENT ON TABLE public.outbox_events IS
  'SHARED. Transactional outbox: written in the same DB transaction as the business change; relayed async.';
COMMENT ON TABLE public.idempotency_keys IS
  'SHARED. Durable idempotency ledger; UNIQUE(scope, key) makes retried money/fulfillment mutations safe.';
