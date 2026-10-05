-- ============================================================================
-- access.sql -- RLS AUDIT + RE-ASSERT and STORAGE AUDIT (mirrors 0008 + 0009)
-- ============================================================================
-- Truth   : 0008_indexes_rls.sql (ENABLE RLS + deny_all_client_access on all 56
--           tables; buckets) + 0009_fixes.sql (re-assert loops). Adds nothing
--           new. Human-readable rules: config/rls.md, config/storage.md.
-- Role    : owner / superuser for RE-ASSERT blocks; audits are reads.
--           service_role bypasses RLS and is unaffected.
-- Sections: P1 deny-all client access audit + repair; P2 public storage read
--           audit + repair. Bodies preserved VERBATIM.
-- History : consolidated 2026-10-03 from policies/deny_all_client_access.sql +
--           allow_public_storage_read.sql (deleted).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- P1: policies/deny_all_client_access.sql -- Usage: RLS audit (missing RLS / missing deny policy -> zero rows) + re-assert loop
-- (body below preserved VERBATIM from supabase/policies/deny_all_client_access.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- deny_all_client_access.sql — RLS AUDIT + RE-ASSERT (mirrors 0008 + 0009, adds nothing new)
-- ============================================================================
-- Truth   : supabase/migrations/0008_indexes_rls.sql (ENABLE RLS +
--           deny_all_client_access on all 56 tables) +
--           supabase/migrations/0009_fixes.sql (re-assert loop)
-- Role    : run as owner / superuser (DDL). service_role bypasses RLS and is
--           unaffected by these policies.
-- Purpose : (a) AUDIT — first two queries must return ZERO rows on a healthy DB;
--           (b) REPAIR — the loop re-asserts any manually dropped deny policy
--           (additive only; repairs drift, grants nothing).
-- New table? Add it to this list AND to a new 0010 migration (default = deny).
-- See     : supabase/config/rls.md (full verification §0–§10)
-- ============================================================================

-- A. AUDIT: tables missing RLS ------------------------------------------------
-- expect: zero rows
SELECT tablename AS missing_rls FROM pg_tables
WHERE schemaname = 'public' AND tablename IN (
    'roles','permissions','role_permissions','platform_users','user_roles',
    'customer_profiles','addresses','regions','cuisines','vendors',
    'vendor_regions','vendor_cuisines','vendor_verifications','vendor_documents',
    'menus','menu_categories','menu_items','slots','vendor_slots',
    'service_zones','vendor_service_zones','menu_item_availability',
    'coupons','coupon_usages','carts','cart_items','orders','order_items',
    'order_status_history','payments','payment_attempts','refunds',
    'meal_plans','subscriptions','subscription_schedules',
    'subscription_daily_orders','subscription_status_history',
    'riders','rider_documents','rider_availability','deliveries',
    'delivery_assignments','delivery_status_history',
    'support_tickets','support_ticket_messages','audit_logs','notifications',
    'notification_preferences','reviews','commissions','vendor_payouts',
    'vendor_payout_items','rider_payouts','rider_payout_items',
    'outbox_events','idempotency_keys')
EXCEPT
SELECT c.relname FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relrowsecurity = true;

-- B. AUDIT: tables missing the deny-all policy --------------------------------
-- expect: zero rows
SELECT tablename AS missing_deny_policy FROM pg_tables
WHERE schemaname = 'public' AND tablename IN (
    'roles','permissions','role_permissions','platform_users','user_roles',
    'customer_profiles','addresses','regions','cuisines','vendors',
    'vendor_regions','vendor_cuisines','vendor_verifications','vendor_documents',
    'menus','menu_categories','menu_items','slots','vendor_slots',
    'service_zones','vendor_service_zones','menu_item_availability',
    'coupons','coupon_usages','carts','cart_items','orders','order_items',
    'order_status_history','payments','payment_attempts','refunds',
    'meal_plans','subscriptions','subscription_schedules',
    'subscription_daily_orders','subscription_status_history',
    'riders','rider_documents','rider_availability','deliveries',
    'delivery_assignments','delivery_status_history',
    'support_tickets','support_ticket_messages','audit_logs','notifications',
    'notification_preferences','reviews','commissions','vendor_payouts',
    'vendor_payout_items','rider_payouts','rider_payout_items',
    'outbox_events','idempotency_keys')
EXCEPT
SELECT c.relname FROM pg_policy p
JOIN pg_class c ON c.oid = p.polrelid
WHERE p.polname = 'deny_all_client_access';

-- C. RE-ASSERT (same loop as 0009 — safe to re-run) ---------------------------
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'roles','permissions','role_permissions','platform_users','user_roles',
    'customer_profiles','addresses','regions','cuisines','vendors',
    'vendor_regions','vendor_cuisines','vendor_verifications','vendor_documents',
    'menus','menu_categories','menu_items','slots','vendor_slots',
    'service_zones','vendor_service_zones','menu_item_availability',
    'coupons','coupon_usages','carts','cart_items','orders','order_items',
    'order_status_history','payments','payment_attempts','refunds',
    'meal_plans','subscriptions','subscription_schedules',
    'subscription_daily_orders','subscription_status_history',
    'riders','rider_documents','rider_availability','deliveries',
    'delivery_assignments','delivery_status_history',
    'support_tickets','support_ticket_messages','audit_logs','notifications',
    'notification_preferences','reviews','commissions','vendor_payouts',
    'vendor_payout_items','rider_payouts','rider_payout_items',
    'outbox_events','idempotency_keys'] LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
    IF NOT EXISTS (SELECT 1 FROM pg_policy p JOIN pg_class c ON c.oid = p.polrelid
                   WHERE c.relname = t AND p.polname = 'deny_all_client_access') THEN
      EXECUTE format('CREATE POLICY deny_all_client_access ON public.%I '
                     'FOR ALL TO anon, authenticated USING (false) WITH CHECK (false)', t, t);
    END IF;
  END LOOP;
END;
$$;

-- ----------------------------------------------------------------------------
-- P2: policies/allow_public_storage_read.sql -- Usage: storage audit (7 bucket flags + single SELECT policy) + re-assert
-- (body below preserved VERBATIM from supabase/policies/allow_public_storage_read.sql)
-- ----------------------------------------------------------------------------
-- ============================================================================
-- allow_public_storage_read.sql — STORAGE AUDIT (mirrors 0008 + 0009, adds nothing new)
-- ============================================================================
-- Truth   : buckets created in 0008_indexes_rls.sql, flag-repaired + policy
--           re-asserted in 0009_fixes.sql. Human-readable rules:
--           supabase/config/storage.md
-- Doctrine: 3 PUBLIC buckets (display images, SELECT-only for anon/
--           authenticated) + 4 PRIVATE buckets (KYC/PoD/support — service_role
--           or backend-minted signed URLs only). NO client INSERT/UPDATE/DELETE
--           policies anywhere. See supabase/config/rls.md §9 for expectations.
-- Role    : run as owner / superuser for the RE-ASSERT block; audits are reads.
-- ============================================================================

-- A. AUDIT: bucket flags -------------------------------------------------------
-- expect: 7 rows; public=true for vendor-profile-images, menu-images,
--   customer-avatars; public=false for vendor-documents, rider-documents,
--   delivery-proofs, support-attachments.
SELECT id, public FROM storage.buckets WHERE id IN (
  'vendor-profile-images','menu-images','customer-avatars',
  'vendor-documents','rider-documents','delivery-proofs','support-attachments')
ORDER BY id;

-- B. AUDIT: exactly one SELECT policy, no client write policies --------------
-- expect: public_bucket_read / SELECT present; NO INSERT/UPDATE/DELETE rows
--   for anon/authenticated on storage.objects.
SELECT policyname, cmd, roles FROM pg_policies
WHERE schemaname = 'storage' AND tablename = 'objects';

-- C. RE-ASSERT (same shape as 0009 — safe to re-run) ---------------------------
-- Buckets: upsert flags (repairs drift, e.g. a bucket flipped to public).
-- INSERT INTO storage.buckets (id, name, public)
-- VALUES ('vendor-profile-images','vendor-profile-images', true),
--        ('menu-images','menu-images', true),
--        ('customer-avatars','customer-avatars', true),
--        ('vendor-documents','vendor-documents', false),
--        ('rider-documents','rider-documents', false),
--        ('delivery-proofs','delivery-proofs', false),
--        ('support-attachments','support-attachments', false)
-- ON CONFLICT (id) DO UPDATE SET public = EXCLUDED.public;
-- Policy: SELECT-only on the three public buckets (drop + recreate keeps the
--   single-policy posture — never add INSERT/UPDATE/DELETE for anon/authed).
-- DROP POLICY IF EXISTS public_bucket_read ON storage.objects;
-- CREATE POLICY public_bucket_read ON storage.objects
--   FOR SELECT TO anon, authenticated
--   USING (bucket_id IN ('vendor-profile-images','menu-images','customer-avatars'));
