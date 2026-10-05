-- ============================================================================
-- Codewild Food Platform — Migration 0006: Fulfillment domain
-- ============================================================================
-- Domain owner: FULFILLMENT (Delivery / Rider / Dispatch / Pickup / Proof).
-- Tables: riders, rider_documents, rider_availability, deliveries,
--         delivery_assignments, delivery_status_history
--
-- Model (§16): Delivery is a fulfillment object attached to an Order (1:1 for
-- v1 — one delivery per order; re-attempts are modelled as assignments).
-- Dispatch strategy stays behind DeliveryAssignmentService; the schema only
-- stores zone/availability/workload inputs so the strategy can evolve without
-- rewriting Order.
--
-- Verification rule (§12): pickup/delivery evidence is verified SERVER-side
-- (assignment + order state + code validity/expiry). OTPs are stored as
-- HASHES here, never plaintext.
-- ============================================================================

-- Riders / delivery partners -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.riders (
  id                          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                     uuid        NOT NULL UNIQUE
                              REFERENCES public.platform_users (id) ON DELETE RESTRICT,
  display_name                text        NOT NULL DEFAULT '',
  phone                       text,
  photo_url                   text,
  vehicle_type                text        NOT NULL DEFAULT 'BIKE'
                              CHECK (vehicle_type IN (
                                'BICYCLE', 'BIKE', 'SCOOTER', 'CAR', 'FOOT')),
  vehicle_number              text,
  city                        text        NOT NULL DEFAULT '',
  status                      text        NOT NULL DEFAULT 'DRAFT'
                              CHECK (status IN (
                                'DRAFT', 'SUBMITTED', 'UNDER_REVIEW',
                                'APPROVED', 'ACTIVE', 'INACTIVE',
                                'SUSPENDED', 'DEACTIVATED')),
  kyc_status                  text        NOT NULL DEFAULT 'PENDING'
                              CHECK (kyc_status IN (
                                'PENDING', 'APPROVED', 'REJECTED',
                                'CHANGES_REQUESTED', 'EXPIRED')),
  rating_avg                  numeric(3,2) NOT NULL DEFAULT 0.00
                              CHECK (rating_avg >= 0 AND rating_avg <= 5),
  rating_count                integer     NOT NULL DEFAULT 0 CHECK (rating_count >= 0),
  total_deliveries            integer     NOT NULL DEFAULT 0 CHECK (total_deliveries >= 0),
  current_lat                 double precision,
  current_lng                 double precision,
  last_location_at            timestamptz,
  is_online                   boolean     NOT NULL DEFAULT false,
  max_concurrent_deliveries   integer     NOT NULL DEFAULT 2
                              CHECK (max_concurrent_deliveries > 0 AND max_concurrent_deliveries <= 5),
  approved_at                 timestamptz,
  approved_by                 uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  suspension_reason           text,
  created_at                  timestamptz NOT NULL DEFAULT now(),
  updated_at                  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.rider_documents (
  id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  rider_id      uuid        NOT NULL REFERENCES public.riders (id) ON DELETE CASCADE,
  document_type text        NOT NULL,
  file_url      text        NOT NULL,
  status        text        NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now()
);

-- Weekly availability windows ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.rider_availability (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  rider_id    uuid        NOT NULL REFERENCES public.riders (id) ON DELETE CASCADE,
  weekday     integer     NOT NULL CHECK (weekday >= 0 AND weekday <= 6),
  start_time  time        NOT NULL,
  end_time    time        NOT NULL,
  is_available boolean    NOT NULL DEFAULT true,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (rider_id, weekday, start_time, end_time),
  CONSTRAINT rider_availability_time_order CHECK (start_time < end_time)
);

-- Deliveries (one per order in v1) --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.deliveries (
  id                                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  delivery_number                   text        NOT NULL UNIQUE,
  order_id                          uuid        NOT NULL UNIQUE
                                    REFERENCES public.orders (id) ON DELETE RESTRICT,
  rider_id                          uuid        REFERENCES public.riders (id) ON DELETE SET NULL,
  idempotency_key                   text        NOT NULL UNIQUE,
  status                            text        NOT NULL DEFAULT 'AVAILABLE'
                                    CHECK (status IN (
                                      'AVAILABLE', 'ASSIGNMENT_PENDING', 'ASSIGNED',
                                      'ACCEPTED', 'ARRIVED_AT_VENDOR',
                                      'PICKUP_VERIFICATION_PENDING', 'PICKED_UP',
                                      'EN_ROUTE', 'ARRIVED_AT_CUSTOMER',
                                      'DELIVERY_VERIFICATION_PENDING', 'DELIVERED',
                                      'CANCELLED', 'FAILED')),
  -- Verification artefacts: HASHES + expiry, verified server-side (§12)
  pickup_code_hash                  text,
  pickup_code_expires_at            timestamptz,
  delivery_code_hash                text,
  delivery_code_expires_at          timestamptz,
  pickup_qr_token_hash              text,
  pickup_qr_expires_at              timestamptz,
  -- Frozen route/money context (§11)
  pickup_address_snapshot           jsonb       NOT NULL DEFAULT '{}'::jsonb,
  dropoff_address_snapshot          jsonb       NOT NULL DEFAULT '{}'::jsonb,
  distance_km                       numeric(7,2) CHECK (distance_km IS NULL OR distance_km >= 0),
  delivery_fee_paise_snapshot       bigint      NOT NULL DEFAULT 0 CHECK (delivery_fee_paise_snapshot >= 0),
  rider_payout_paise_snapshot       bigint      CHECK (rider_payout_paise_snapshot IS NULL OR rider_payout_paise_snapshot >= 0),
  tip_paise                         bigint      NOT NULL DEFAULT 0 CHECK (tip_paise >= 0),
  proof_of_delivery                 jsonb       NOT NULL DEFAULT '{}'::jsonb,
  failure_reason                    text,
  estimated_pickup_at               timestamptz,
  estimated_delivery_at             timestamptz,
  picked_up_at                      timestamptz,
  delivered_at                      timestamptz,
  created_at                        timestamptz NOT NULL DEFAULT now(),
  updated_at                        timestamptz NOT NULL DEFAULT now()
);

-- Dispatch assignments: offer -> accept/reject/timeout -> retry/escalate (§16/§23) ------------------
CREATE TABLE IF NOT EXISTS public.delivery_assignments (
  id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  delivery_id       uuid        NOT NULL REFERENCES public.deliveries (id) ON DELETE CASCADE,
  rider_id          uuid        NOT NULL REFERENCES public.riders (id) ON DELETE RESTRICT,
  attempt_no        integer     NOT NULL CHECK (attempt_no > 0),
  status            text        NOT NULL DEFAULT 'OFFERED'
                    CHECK (status IN (
                      'OFFERED', 'ACCEPTED', 'REJECTED', 'EXPIRED',
                      'TIMEOUT', 'CANCELLED', 'REASSIGNED')),
  offered_at        timestamptz NOT NULL DEFAULT now(),
  responded_at      timestamptz,
  expires_at        timestamptz,
  assignment_reason jsonb       NOT NULL DEFAULT '{}'::jsonb,
  idempotency_key   text        NOT NULL UNIQUE,
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now(),
  UNIQUE (delivery_id, rider_id, attempt_no)
);

-- Delivery status history (append-only) ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.delivery_status_history (
  id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  delivery_id       uuid        NOT NULL REFERENCES public.deliveries (id) ON DELETE CASCADE,
  from_status       text,
  to_status         text        NOT NULL,
  changed_by        uuid        REFERENCES public.platform_users (id) ON DELETE SET NULL,
  actor_role        text,
  change_reason     text,
  location_snapshot jsonb       NOT NULL DEFAULT '{}'::jsonb,
  idempotency_key   text        UNIQUE,
  created_at        timestamptz NOT NULL DEFAULT now()
);

-- updated_at triggers --------------------------------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'riders','rider_documents','rider_availability','deliveries','delivery_assignments'
  ] LOOP
    EXECUTE format(
      'DROP TRIGGER IF EXISTS trg_%s_updated_at ON public.%I', t, t);
    EXECUTE format(
      'CREATE TRIGGER trg_%s_updated_at BEFORE UPDATE ON public.%I '
      'FOR EACH ROW EXECUTE FUNCTION public.set_updated_at()', t, t);
  END LOOP;
END;
$$;

COMMENT ON TABLE public.deliveries IS
  'FULFILLMENT domain. Verification codes stored as hashes; pickup/delivery validated server-side against assignment + order state.';
COMMENT ON TABLE public.delivery_assignments IS
  'Dispatch offers. Strategy inputs (zone, availability, distance, workload) stored in assignment_reason; strategy itself lives in backend service.';
