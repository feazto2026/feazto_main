-- ============================================================================
-- 06_payments_refunds.sql -- lookup + log attempt + refund (USAGE-GROUPED)
-- ============================================================================
-- Truth   : supabase/migrations/0004_commerce.sql (payments, payment_attempts,
--           refunds)
-- Role    : service_role (Spring Boot webhook/payment service only; frontend
--           callbacks are hints -- SUCCESS requires server verification).
-- Clients : C, A
-- Guards  : payments.provider_payment_id/idempotency_key UNIQUE (replay-safe);
--           UNIQUE(payment_id, attempt_no) + attempt idempotency UNIQUE
--           (append-only, never UPDATE); refunds idempotency_key +
--           provider_refund_id UNIQUE. Refund amounts computed server-side.
-- Sections: Q1..Q3. Q1+Q2 canonical for wrapper functions/order_lifecycle.sql
--           S2. Bodies VERBATIM.
-- History : consolidated 2026-10-03 from queries/commerce/payments/
--           01_lookup_payment_for_verification.sql +
--           02_log_payment_attempt.sql + queries/commerce/refunds/
--           01_create_refund_request.sql (deleted).
-- See     : supabase/queries/README.md, docs/database/schema.md Query-paths.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Q1: queries/commerce/payments/01_lookup_payment_for_verification.sql -- Usage: webhook replay-safe lookup by provider id (fallback: idempotency key)
-- (body below preserved VERBATIM from supabase/queries/commerce/payments/01_lookup_payment_for_verification.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_lookup_payment_for_verification.sql — webhook replay-safe lookup (provider id OR idempotency)
-- ============================================================================
-- Truth   : supabase/migrations/0004_commerce.sql (payments)
--           Guards: provider_payment_id UNIQUE, idempotency_key UNIQUE,
--             payments_subject_check (order_id OR subscription_id NOT NULL)
-- Role    : service_role (Spring Boot webhook handler only — frontend
--           callbacks are hints; SUCCESS requires server verification)
-- Clients : C, A
-- Params  : $1 :: text — provider_payment_id (e.g. Razorpay pay_…) — replay key
--           $2 :: text — idempotency_key (fallback when provider id unknown)
-- ============================================================================

-- Primary: replay-safe webhook lookup (same event delivered twice → same row).
SELECT * FROM public.payments WHERE provider_payment_id = $1;

-- Fallback: client-retry / pre-provider lookup before the provider id exists.
-- SELECT * FROM public.payments WHERE idempotency_key = $2;

-- On verified SUCCESS the handler (ONE TX — see functions/verify_payment_webhook_transaction.sql):
--   UPDATE payments SET status='SUCCESS', verified_at=now(), provider_payment_id=$1
--     WHERE idempotency_key=$2 AND status <> 'SUCCESS';
-- then transitions the order (PAYMENT_CONFIRMED) + emits outbox event.
-- expect: 0..1 rows; row with status='SUCCESS' already → acknowledge webhook,
--   do nothing (replay absorbed, no double-credit).

-- ----------------------------------------------------------------------------
-- Q2: queries/commerce/payments/02_log_payment_attempt.sql -- Usage: per-try provider request/response log (append-only; kept, not merged)
-- (body below preserved VERBATIM from supabase/queries/commerce/payments/02_log_payment_attempt.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 02_log_payment_attempt.sql — per-try provider request/response log (append-only)
-- ============================================================================
-- Truth   : supabase/migrations/0004_commerce.sql (payment_attempts)
--           Guards: UNIQUE(payment_id, attempt_no), idempotency_key UNIQUE
-- Role    : service_role (Spring Boot payment service only)
-- Clients : C, A
-- Params  : $1 :: uuid — payments.id · $2 :: int — attempt_no (1,2,3…) ·
--           $3 :: text — provider_reference · $4 :: text — status
--             (INITIATED/PENDING/SUCCESS/FAILED/CANCELLED) ·
--           $5 :: jsonb — request_payload · $6 :: jsonb — response_payload ·
--           $7 :: text — idempotency_key (unique per attempt) · $8 :: text — error_code
-- ============================================================================

INSERT INTO public.payment_attempts
  (payment_id, attempt_no, provider_reference, status,
   request_payload, response_payload, idempotency_key, error_code)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
ON CONFLICT (idempotency_key) DO NOTHING;
-- expect: 1 row per attempt; retries reuse $7 (no duplicate attempt rows).
-- History is append-only: failures are new rows, never UPDATEs.

-- ----------------------------------------------------------------------------
-- Q3: queries/commerce/refunds/01_create_refund_request.sql -- Usage: server-computed refund request REQUESTED (approval is a separate step)
-- (body below preserved VERBATIM from supabase/queries/commerce/refunds/01_create_refund_request.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_create_refund_request.sql — server-computed refund request (auditable)
-- ============================================================================
-- Truth   : supabase/migrations/0004_commerce.sql (refunds)
--           Guards: idempotency_key UNIQUE, provider_refund_id UNIQUE
-- Role    : service_role (Spring Boot only — eligibility + amount computed
--           server-side from order/payment snapshots, never client-supplied)
-- Clients : C, A (requested_by/approved_by are platform_users ids; approval is
--           a separate privileged step: REQUESTED → APPROVED → PROCESSING → COMPLETED)
-- Params  : $1 :: uuid — orders.id · $2 :: uuid — payments.id (must be SUCCESS) ·
--           $3 :: text — idempotency_key · $4 :: bigint — amount_paise (> 0,
--             ≤ refundable balance — computed server-side) · $5 :: text — reason ·
--           $6 :: uuid — requested_by
-- ============================================================================

INSERT INTO public.refunds
  (order_id, payment_id, idempotency_key, amount_paise, reason, requested_by,
   status)
VALUES ($1, $2, $3, $4, $5, $6, 'REQUESTED')
ON CONFLICT (idempotency_key) DO NOTHING
RETURNING id;
-- expect: 1 row (status REQUESTED); replay with same $3 returns existing row.
-- provider_refund_id is filled when the provider confirms (PROCESSING step).
