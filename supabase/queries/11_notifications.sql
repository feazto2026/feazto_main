-- ============================================================================
-- 11_notifications.sql -- list + send (USAGE-GROUPED library)
-- ============================================================================
-- Truth   : supabase/migrations/0007_operations.sql (notifications,
--           notification_preferences)
-- Role    : service_role (Spring Boot notification API / outbox consumer).
-- Clients : C, V, R, A (opt-outs enforced at SEND time, not by feed filter)
-- Guards  : notifications.dedupe_key UNIQUE (duplicate fires collapse).
-- Sections: Q1..Q2 in execution order. Each body preserved VERBATIM.
-- History : consolidated 2026-10-03 from queries/notifications/
--           01_list_notifications.sql + 02_send_notification.sql (deleted).
-- See     : supabase/queries/README.md, docs/database/schema.md Query-paths.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Q1: queries/notifications/01_list_notifications.sql -- Usage: recipient notification feed, newest first
-- (body below preserved VERBATIM from supabase/queries/notifications/01_list_notifications.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 01_list_notifications.sql — recipient notification feed (newest first)
-- ============================================================================
-- Truth   : supabase/migrations/0007_operations.sql
--           (notifications, notification_preferences)
--           Guard: notifications.dedupe_key UNIQUE (sender-side dedupe)
-- Role    : service_role (Spring Boot notification API; recipient checked in code)
-- Clients : C, V, R, A
-- Params  : $1 :: uuid — recipient_user_id (platform_users.id) ·
--           $2 :: int — LIMIT · $3 :: int — OFFSET
-- ============================================================================

SELECT n.*
  FROM public.notifications n
 WHERE n.recipient_user_id = $1
 ORDER BY n.created_at DESC
 LIMIT $2 OFFSET $3;
-- expect: newest first; preferences (opt-outs) are enforced at SEND time
--   (see 02_send_notification.sql), not by filtering this feed.

-- ----------------------------------------------------------------------------
-- Q2: queries/notifications/02_send_notification.sql -- Usage: deduped send honoring recipient preferences (silent skip on opt-out)
-- (body below preserved VERBATIM from supabase/queries/notifications/02_send_notification.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 02_send_notification.sql — deduped send honoring recipient preferences
-- ============================================================================
-- Truth   : supabase/migrations/0007_operations.sql
--           (notifications, notification_preferences)
--           Guard: notifications.dedupe_key UNIQUE
-- Role    : service_role (Spring Boot — outbox consumer / direct send)
-- Clients : C, V, R, A
-- Params  : $1 recipient_user_id · $2 channel (PUSH/SMS/EMAIL/IN_APP) ·
--           $3 template_code · $4 payload JSONB · $5 dedupe_key
--             (derive '<template>:<aggregate_id>'; retries reuse it) ·
--           $6 :: text — preference key checked (e.g. 'order_updates')
-- Effect  : skips silently when the recipient opted out of $6; otherwise
--           inserts once — duplicate fires collapse on dedupe_key.
-- ============================================================================

INSERT INTO public.notifications
  (recipient_user_id, channel, template_code, payload, status, dedupe_key)
SELECT $1, $2, $3, $4::jsonb, 'PENDING', $5
 WHERE NOT EXISTS (
         SELECT 1
           FROM public.notification_preferences np
          WHERE np.user_id = $1
            AND np.preference_key = $6
            AND np.is_enabled = false
       )
ON CONFLICT (dedupe_key) DO NOTHING;
-- expect: 1 row (PENDING → sender pipeline) or 0 rows (opted out / duplicate).
