-- ============================================================================
-- 10_support_ops.sql -- tickets + thread + review + audit + outbox +
--                      idempotency (USAGE-GROUPED library)
-- ============================================================================
-- Truth   : supabase/migrations/0007_operations.sql (support_tickets(+messages),
--           reviews, audit_logs, outbox_events, idempotency_keys)
--           + 0008 (ix_outbox_pending, ix_idempotency_expiry, ticket indexes)
-- Role    : service_role (Spring Boot only; first statement of every mutating
--           flow is Q6 claim; every state-changing TX publishes via Q5).
-- Clients : C, V, R, A (Q4 writers are backend flows, never mobiles)
-- Guards  : ticket_number UNIQUE; reviews.order_id UNIQUE (one per order);
--           outbox idempotency_key UNIQUE (business row + event commit
--           together); UNIQUE(scope,key) collapses retries BEFORE side effects.
-- Sections: Q1..Q6 (ticket open -> thread -> review -> audit -> outbox ->
--           idempotency). Bodies VERBATIM.
-- History : consolidated 2026-10-03 from queries/operations/support_tickets/
--           01,02 + reviews/01 + audit/01 + outbox/01 + idempotency/01
--           (deleted).
-- See     : supabase/queries/README.md, docs/database/schema.md Query-paths.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Q1: queries/operations/support_tickets/01_open_support_ticket.sql -- Usage: open support ticket + first message in ONE TX
-- (body below preserved VERBATIM from supabase/queries/operations/support_tickets/01_open_support_ticket.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_open_support_ticket.sql — support ticket + first message (ONE TX)
-- ============================================================================
-- Truth   : supabase/migrations/0007_operations.sql
--           (support_tickets, support_ticket_messages)
--           Guards: ticket_number UNIQUE · Hot indexes: ix_support_tickets_{order,delivery}
-- Role    : service_role (Spring Boot support API — requester checked in code)
-- Clients : C, V, R, A
-- Params  : $1 ticket_number · $2 requester_id (platform_users.id) ·
--           $3 subject · $4 category · $5 priority · $6 order_id (nullable) ·
--           $7 delivery_id (nullable) · $8 first message body · $9 idempotency_key
-- Effect  : ticket (OPEN) + opening message atomically; attachments live in the
--           `support-attachments/{ticket_id}/` bucket (never inline base64)
-- ============================================================================

BEGIN;

INSERT INTO public.support_tickets
  (ticket_number, requester_id, subject, category, priority,
   order_id, delivery_id, status, idempotency_key)
VALUES ($1, $2, $3, $4, $5, $6, $7, 'OPEN', $9)
ON CONFLICT (idempotency_key) DO NOTHING
RETURNING id;

INSERT INTO public.support_ticket_messages
  (ticket_id, sender_id, body, idempotency_key)
SELECT id, $2, $8, $9 || ':msg1'
  FROM public.support_tickets WHERE idempotency_key = $9
ON CONFLICT (idempotency_key) DO NOTHING;

COMMIT;
-- expect: 1 ticket + 1 message; retry with same $9 = no-op.

-- ----------------------------------------------------------------------------
-- Q2: queries/operations/support_tickets/02_get_ticket_thread.sql -- Usage: ticket + full message thread, chronological
-- (body below preserved VERBATIM from supabase/queries/operations/support_tickets/02_get_ticket_thread.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 02_get_ticket_thread.sql — ticket + full message thread (chronological)
-- ============================================================================
-- Truth   : supabase/migrations/0007_operations.sql
--           (support_tickets, support_ticket_messages — messages append-only)
-- Role    : service_role (Spring Boot support API; visibility scoped in code:
--           requester, assigned agent, admin)
-- Clients : C, V, R, A
-- Params  : $1 :: uuid — support_tickets.id
-- ============================================================================

SELECT * FROM public.support_tickets WHERE id = $1;

SELECT *
  FROM public.support_ticket_messages
 WHERE ticket_id = $1
 ORDER BY created_at;
-- expect: 1 ticket + n messages oldest-first (append-only: replies are INSERTs).

-- ----------------------------------------------------------------------------
-- Q3: queries/operations/reviews/01_create_order_review.sql -- Usage: one order-gated review per order (COMPLETED only)
-- (body below preserved VERBATIM from supabase/queries/operations/reviews/01_create_order_review.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_create_order_review.sql — one review per order (order-gated)
-- ============================================================================
-- Truth   : supabase/migrations/0007_operations.sql (reviews)
--           Guard: order_id UNIQUE (one review per order)
-- Role    : service_role (Spring Boot only — order must be COMPLETED and belong
--           to the caller; checked in code before this INSERT)
-- Clients : C, V, R, A (vendors/riders read; only the ordering customer writes)
-- Params  : $1 :: uuid — orders.id · $2 :: uuid — customer_profiles.id ·
--           $3 :: uuid — vendors.id · $4 :: int — rating ·
--           $5 :: text — comment · $6 :: text — idempotency_key
-- ============================================================================

INSERT INTO public.reviews
  (order_id, customer_id, vendor_id, rating, comment, idempotency_key)
VALUES ($1, $2, $3, $4, $5, $6)
ON CONFLICT (order_id) DO NOTHING;
-- expect: 1 row; second review for the same order inserts 0 rows (UNIQUE).

-- ----------------------------------------------------------------------------
-- Q4: queries/operations/audit/01_write_audit_event.sql -- Usage: privileged-action audit row, same TX as the action (INSERT-only)
-- (body below preserved VERBATIM from supabase/queries/operations/audit/01_write_audit_event.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_write_audit_event.sql — privileged-action audit row (same TX as the action)
-- ============================================================================
-- Truth   : supabase/migrations/0007_operations.sql (audit_logs, append-only)
--           Hot index: (entity, actor, time) scan
-- Role    : service_role (Spring Boot — called INSIDE the privileged TX, e.g.
--           role grants, refund approvals, payout runs, signed-URL issuance)
-- Clients : A (read-only views); writers are backend flows, never mobiles
-- Params  : $1 actor_id · $2 actor_role · $3 action (e.g. 'ROLE_GRANTED') ·
--           $4 entity_type · $5 entity_id · $6 detail JSONB
-- Rule    : INSERT-only. No UPDATE/DELETE path exists by convention; retention
--           purges (if ever) are a reviewed migration, not app code.
-- ============================================================================

INSERT INTO public.audit_logs
  (actor_id, actor_role, action, entity_type, entity_id, detail)
VALUES ($1, $2, $3, $4, $5, $6::jsonb);
-- expect: exactly 1 row; runs in the same TX as the action it records so an
--   action without audit cannot commit (and vice versa).

-- ----------------------------------------------------------------------------
-- Q5: queries/operations/outbox/01_publish_outbox_event.sql -- Usage: transactional domain event, same TX as business row (relay polls ix_outbox_pending)
-- (body below preserved VERBATIM from supabase/queries/operations/outbox/01_publish_outbox_event.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_publish_outbox_event.sql — transactional domain event (same TX as business row)
-- ============================================================================
-- Truth   : supabase/migrations/0007_operations.sql (outbox_events)
--           Guards: idempotency_key UNIQUE · Hot index: ix_outbox_pending
-- Role    : service_role (Spring Boot — every state-changing TX publishes here)
-- Clients : all (async consumers: notifications, search sync, analytics)
-- Params  : $1 aggregate_type (e.g. 'orders') · $2 aggregate_id ·
--           $3 event_type (e.g. 'order.placed') · $4 payload JSONB ·
--           $5 idempotency_key (derive from the business key: '<scope>:<id>')
-- Rule    : business row + outbox row commit together or not at all — never
--           publish-then-write. Relay polls ix_outbox_pending (status=PENDING).
--           Optional pg_notify fast-path: see supabase/triggers/notify_outbox_event_trigger.sql
-- ============================================================================

INSERT INTO public.outbox_events
  (aggregate_type, aggregate_id, event_type, payload, status, idempotency_key)
VALUES ($1, $2, $3, $4::jsonb, 'PENDING', $5)
ON CONFLICT (idempotency_key) DO NOTHING;
-- expect: 1 row (PENDING); replay with same $5 = 0 rows (relay dedupes too).

-- ----------------------------------------------------------------------------
-- Q6: queries/operations/idempotency/01_claim_idempotency_key.sql -- Usage: collapse retries on (scope,key) BEFORE side effects (first statement of every mutating flow)
-- (body below preserved VERBATIM from supabase/queries/operations/idempotency/01_claim_idempotency_key.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_claim_idempotency_key.sql — collapse retries on (scope, key) BEFORE side effects
-- ============================================================================
-- Truth   : supabase/migrations/0007_operations.sql (idempotency_keys)
--           Guards: UNIQUE(scope, key) · Hot index: ix_idempotency_expiry
--             (sweep expired claims)
-- Role    : service_role (Spring Boot — first statement of every mutating flow:
--           create order/payment/refund/subscription/payout/dispatch)
-- Clients : all mutating paths
-- Params  : $1 :: text — scope (e.g. 'create_order', 'webhook:razorpay') ·
--           $2 :: text — key (client-supplied idempotency key) ·
--           $3 :: timestamptz — expires_at (claim TTL, e.g. now()+24h)
-- Returns : claimed=true → caller proceeds to do the work; claimed=false →
--           a retry is already in flight / done → return the existing result
--           (look up the business row by the same key) instead of re-executing.
-- ============================================================================

INSERT INTO public.idempotency_keys (scope, key, expires_at)
VALUES ($1, $2, $3)
ON CONFLICT (scope, key) DO NOTHING
RETURNING true AS claimed;
-- expect: 1 row (claimed=true) on first attempt; 0 rows (claimed=false) on
--   retry → fetch existing outcome, do NOT re-run provider calls.
-- Sweeper (periodic): DELETE FROM public.idempotency_keys WHERE expires_at < now();
