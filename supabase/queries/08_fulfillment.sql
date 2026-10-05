-- ============================================================================
-- 08_fulfillment.sql -- online riders + offer + handover verify (USAGE-GROUPED)
-- ============================================================================
-- Truth   : supabase/migrations/0006_fulfillment.sql (riders, deliveries,
--           delivery_assignments, delivery_status_history)
-- Role    : service_role (Spring Boot dispatch only; assignment decision is
--           server-side; rider_app shows offers).
-- Clients : R (offer + code entry), C (shares code), A (tracking/override);
--           V read-only status
-- Guards  : UNIQUE(delivery_id, rider_id, attempt_no) + assignment
--           idempotency UNIQUE; code HASHES only (never plaintext) + expiry
--           checked server-side. Q2 canonical; TX wrapper:
--           functions/fulfillment_subscription.sql S1.
-- Sections: Q1..Q3 in execution order. Each body preserved VERBATIM.
-- History : consolidated 2026-10-03 from queries/fulfillment/deliveries/
--           01_list_online_riders.sql + 02_offer_delivery_assignment.sql +
--           03_verify_handover_code.sql (deleted).
-- See     : supabase/queries/README.md, docs/database/schema.md Query-paths.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Q1: queries/fulfillment/deliveries/01_list_online_riders.sql -- Usage: dispatchable riders (ACTIVE + online + city, freshest first)
-- (body below preserved VERBATIM from supabase/queries/fulfillment/deliveries/01_list_online_riders.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_list_online_riders.sql — dispatchable riders (ACTIVE + online + city)
-- ============================================================================
-- Truth   : supabase/migrations/0006_fulfillment.sql (riders)
--           Hot index: ix_riders_status_online
-- Role    : service_role (Spring Boot dispatch only)
-- Clients : R, A — rider_app shows offers; assignment decision is server-side
-- Params  : $1 :: text — city · $2 :: int — LIMIT · $3 :: int — OFFSET
-- Note    : final pick also weighs zone/availability/distance/workload and
--           records the breakdown in delivery_assignments.assignment_reason
--           (see 02_offer_delivery_assignment.sql + functions/offer_delivery_dispatch_transaction.sql)
-- ============================================================================

SELECT *
  FROM public.riders
 WHERE status = 'ACTIVE'
   AND is_online
   AND city = $1
 ORDER BY last_seen_at DESC NULLS LAST
 LIMIT $2 OFFSET $3;
-- expect: online riders freshest-first; empty set → escalate (extend TTL /
--   widen zone) rather than fail the delivery.

-- ----------------------------------------------------------------------------
-- Q2: queries/fulfillment/deliveries/02_offer_delivery_assignment.sql -- Usage: dispatch offer with reason + TTL, ONE TX with delivery (canonical; wrapper: functions/fulfillment_subscription.sql S1)
-- (body below preserved VERBATIM from supabase/queries/fulfillment/deliveries/02_offer_delivery_assignment.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 02_offer_delivery_assignment.sql — dispatch offer with reason + TTL (ONE TX with delivery)
-- ============================================================================
-- Truth   : supabase/migrations/0006_fulfillment.sql
--           (delivery_assignments, deliveries, delivery_status_history)
--           Guards: UNIQUE(delivery_id, rider_id, attempt_no),
--             assignments.idempotency_key UNIQUE
-- Role    : service_role (Spring Boot dispatch only)
-- Clients : R (offer appears in rider_app), A (tracking)
-- Params  : $1 :: uuid — deliveries.id · $2 :: uuid — riders.id ·
--           $3 :: int — attempt_no (1,2,3… escalates) ·
--           $4 :: timestamptz — offer expires_at (TTL) ·
--           $5 :: jsonb — assignment_reason {zone, distance_km, workload…} ·
--           $6 :: text — idempotency_key (unique per offer)
-- Effect  : OFFERED assignment + delivery → ASSIGNMENT_PENDING + history,
--           atomically; accept/reject/timeout are later transitions on the
--           assignment row (see functions/offer_delivery_dispatch_transaction.sql)
-- ============================================================================

BEGIN;

INSERT INTO public.delivery_assignments
  (delivery_id, rider_id, attempt_no, status, expires_at,
   assignment_reason, idempotency_key)
VALUES ($1, $2, $3, 'OFFERED', $4, $5, $6)
ON CONFLICT (idempotency_key) DO NOTHING;

UPDATE public.deliveries
   SET status = 'ASSIGNMENT_PENDING',
       rider_id = $2,
       updated_at = now()
 WHERE id = $1
   AND status IN ('AVAILABLE', 'ASSIGNMENT_PENDING');

INSERT INTO public.delivery_status_history
  (delivery_id, from_status, to_status, actor_role, change_reason,
   idempotency_key)
VALUES ($1, 'AVAILABLE', 'ASSIGNMENT_PENDING', 'SYSTEM', 'dispatch offer', $6)
ON CONFLICT (idempotency_key) DO NOTHING;

COMMIT;
-- expect: 1 assignment + delivery moved to ASSIGNMENT_PENDING + 1 history row.
-- TTL expiry without response → backend marks TIMEOUT and re-offers with
-- attempt_no+1 (escalation), never overwriting the timed-out row.

-- ----------------------------------------------------------------------------
-- Q3: queries/fulfillment/deliveries/03_verify_handover_code.sql -- Usage: hash-verified pickup/delivery confirm read step (sha256 compared server-side)
-- (body below preserved VERBATIM from supabase/queries/fulfillment/deliveries/03_verify_handover_code.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 03_verify_handover_code.sql — hash-verified pickup/delivery confirm (read step)
-- ============================================================================
-- Truth   : supabase/migrations/0006_fulfillment.sql (deliveries)
--           Columns: pickup_code_hash + pickup_code_expires_at,
--             delivery_code_hash + delivery_code_expires_at
--             (HASHES only — never plaintext; §12)
-- Role    : service_role (Spring Boot only — sha256(code) compared server-side)
-- Clients : R (rider enters code), C (customer shares code), A (support override)
-- Params  : $1 :: uuid — deliveries.id
--           $2 :: text — which handover: 'pickup' or 'delivery'
-- Backend : fetch hashes + expiry with this query, then in code:
--             ok = sha256(user_supplied_code) = <code_hash>
--                  AND now() < <expires_at> AND status = <expected>
--           On ok → transition delivery (PICKED_UP / DELIVERED) + history in ONE
--           TX (same pattern as commerce/orders/03_transition_order_status.sql).
--           On miss → 422, no write (rate-limit attempts in Redis).
-- ============================================================================

SELECT id,
       status,
       pickup_code_hash,
       pickup_code_expires_at,
       delivery_code_hash,
       delivery_code_expires_at,
       (CASE WHEN $2 = 'pickup' THEN pickup_code_expires_at
             ELSE delivery_code_expires_at END) AS relevant_expires_at,
       (CASE WHEN $2 = 'pickup' THEN (now() < pickup_code_expires_at)
             ELSE (now() < delivery_code_expires_at) END) AS not_expired
  FROM public.deliveries
 WHERE id = $1;
-- expect: 1 row; not_expired=false → code rotated/expired → re-issue.
-- NEVER log or return the raw code — only hashes leave the database.
