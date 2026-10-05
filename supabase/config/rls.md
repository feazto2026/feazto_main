# RLS Policy — Codewild Food Platform

Source of truth: `supabase/migrations/0008_indexes_rls.sql` + `supabase/migrations/0009_fixes.sql`
(re-asserts deny-all, adds `fk_orders_schedule`, payout net CHECKs, missing indexes,
storage flag correction). Principle: **the database is not the API**
(MASTER_PROMPT_ENHANCED.md §3.2, ADR-010).

## Default posture: deny everything client-side

| Role | SELECT | INSERT | UPDATE | DELETE |
|---|---|---|---|---|
| `anon` | ❌ denied | ❌ denied | ❌ denied | ❌ denied |
| `authenticated` | ❌ denied | ❌ denied | ❌ denied | ❌ denied |
| `service_role` (Spring Boot backend) | ✅ bypasses RLS | ✅ | ✅ | ✅ |

Implementation: every business table has `ENABLE ROW LEVEL SECURITY` plus a
single policy:

```sql
CREATE POLICY deny_all_client_access ON public.<table>
  FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);
```

With RLS enabled and no permissive policy evaluating to true, Postgres denies
the statement. `service_role` bypasses RLS entirely, so backend behaviour is
unaffected.

## Tables covered (56)

Identity: `roles`, `permissions`, `role_permissions`, `platform_users`, `user_roles`
Customer: `customer_profiles`, `addresses`
Marketplace: `regions`, `cuisines`, `vendors`, `vendor_regions`, `vendor_cuisines`,
`vendor_verifications`, `vendor_documents`, `menus`, `menu_categories`, `menu_items`,
`slots`, `vendor_slots`, `service_zones`, `vendor_service_zones`, `menu_item_availability`
Commerce: `coupons`, `coupon_usages`, `carts`, `cart_items`, `orders`, `order_items`,
`order_status_history`, `payments`, `payment_attempts`, `refunds`
Subscription: `meal_plans`, `subscriptions`, `subscription_schedules`,
`subscription_daily_orders`, `subscription_status_history`
Fulfillment: `riders`, `rider_documents`, `rider_availability`, `deliveries`,
`delivery_assignments`, `delivery_status_history`
Operations/Finance/Shared: `support_tickets`, `support_ticket_messages`, `audit_logs`,
`notifications`, `notification_preferences`, `reviews`, `commissions`,
`vendor_payouts`, `vendor_payout_items`, `rider_payouts`, `rider_payout_items`,
`outbox_events`, `idempotency_keys`

## Append-only tables (no UPDATE path by convention + backend enforcement)

`order_status_history`, `subscription_status_history`, `delivery_status_history`,
`payment_attempts`, `audit_logs`, `support_ticket_messages`, `commissions`
(except `REVERSED` transition via backend job), `coupon_usages`, payout items,
`*_daily_orders` (beyond generator transitions). The backend exposes no update
endpoint for these; corrections are new rows (reversal / re-accrual).

## Opening a safe client-direct path later (requires ADR-010 review)

1. Add a narrow `FOR SELECT TO authenticated USING (…ownership check…)` policy.
2. Never add client `INSERT/UPDATE/DELETE` on business tables — writes go
   through `POST /api/v1/...` so state machines, snapshots and idempotency hold.
3. Realtime subscriptions must re-check the same ownership predicate as the API.

## Verification queries

Run with `service_role` (or a superuser) after `supabase db reset`. Every query
states its expected result. Queries are read-only except §8 (run §8 with a
user JWT to prove denial).

```sql
-- 0. Migration order guard: 0001..0009 applied, seed runnable ----------------
-- expect: 9 rows (0001..0009) when using supabase_migrations, or at least
-- set_updated_at() + 56 tables existing (local psql fallback).
SELECT count(*) AS business_tables
FROM pg_tables WHERE schemaname = 'public'
  AND tablename IN (
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
    'outbox_events','idempotency_keys');
-- expect: 56

-- 1. Every business table has RLS enabled (deny-by-default) ------------------
SELECT tablename AS missing_rls FROM pg_tables
WHERE schemaname = 'public' AND tablename NOT LIKE 'pg_%'
  AND tablename IN (
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
SELECT c.relname AS tablename FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relrowsecurity = true;
-- expect: zero rows

-- 2. Every business table has exactly one deny-all policy for anon/authenticated
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
-- expect: zero rows

-- 3. UUID PKs on every business table (join tables use composite UUID PKs) ----
SELECT table_name AS non_uuid_pk FROM information_schema.columns
WHERE table_schema = 'public' AND column_name = 'id'
GROUP BY table_name HAVING count(*) = 1
EXCEPT
SELECT c.relname FROM pg_attribute a
JOIN pg_class c ON c.oid = a.attrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND a.attname = 'id' AND format_type(a.atttypid, NULL) = 'uuid';
-- expect: zero rows (every `id` is uuid; composite-PK join tables have no `id`)

-- 4. Critical UNIQUE guards: idempotency + subscription anti-double-run ------
SELECT conname, contype FROM pg_constraint
WHERE conname IN (
  'orders_idempotency_key_key',            -- orders.idempotency_key UNIQUE
  'payments_idempotency_key_key',
  'payment_attempts_idempotency_key_key',
  'refunds_idempotency_key_key',
  'subscriptions_idempotency_key_key',
  'subscription_daily_orders_idempotency_key_key',
  'deliveries_idempotency_key_key',
  'delivery_assignments_idempotency_key_key',
  'vendor_payouts_idempotency_key_key',
  'rider_payouts_idempotency_key_key',
  'outbox_events_idempotency_key_key',
  'idempotency_keys_scope_key_key',        -- idempotency_keys(scope,key)
  'subscription_daily_orders_subscription_id_service_date_meal_slot_id_key')
  AND contype = 'u';
-- expect: 13 rows (names vary by Postgres auto-naming; if zero, check via:)
-- fallback: SELECT tablename FROM pg_indexes WHERE indexdef ILIKE '%UNIQUE%'
--   AND indexdef ILIKE '%idempotency_key%'; -- expect >= 12 indexes
SELECT constraint_name FROM information_schema.table_constraints
WHERE table_schema = 'public' AND constraint_type = 'UNIQUE'
  AND table_name = 'subscription_daily_orders';
-- expect: includes UNIQUE(subscription_id, service_date, meal_slot_id)

-- 5. CHECKs: order total consistency + slot/availability capacity -------------
SELECT conname FROM pg_constraint
WHERE conname IN (
  'orders_total_consistency_check',        -- total = subtotal-discount+tax+fees
  'vendor_slots_capacity_check',           -- capacity_reserved <= capacity_total
  'menu_item_availability_reserved_check', -- quantity_reserved <= quantity_available
  'vendor_payouts_net_check',              -- 0009: net = gross-comm-refunds+adj
  'rider_payouts_net_check',
  'vendor_payouts_period_check',
  'rider_payouts_period_check',
  'subscriptions_date_order_check');
-- expect: 8 rows

-- 6. Forward FKs attached (nullable-first, guarded DO blocks) ----------------
SELECT conname FROM pg_constraint
WHERE conname IN ('fk_customer_hometown_region','fk_orders_subscription',
                  'fk_payments_subscription','fk_orders_schedule');
-- expect: 4 rows

-- 7. Hot-path indexes present (0008 + 0009) -----------------------------------
SELECT indexname FROM pg_indexes WHERE schemaname = 'public'
  AND indexname IN (
    'uq_addresses_single_default','uq_carts_single_active',
    'uq_vendor_verifications_single_open','uq_subscriptions_single_live',
    'ix_orders_customer_created','ix_orders_vendor_status',
    'ix_outbox_pending','ix_idempotency_expiry',
    'ix_vendor_documents_vendor','ix_rider_documents_rider',
    'ix_subscription_status_history_sub',
    'ix_vendor_payout_items_payout','ix_rider_payout_items_payout',
    'ix_coupons_validity','ix_menu_item_avail_slot_date');
-- expect: 15 rows

-- 8. Client denial proof (run with anon/authenticated JWT, NOT service_role) ---
-- INSERT INTO public.orders (order_number, idempotency_key, customer_id, vendor_id,
--   subtotal_paise, total_paise, delivery_address_snapshot,
--   commission_bps_snapshot, platform_commission_paise_snapshot,
--   vendor_payout_paise_snapshot)
-- VALUES ('PROBE-01','probe-key-01','00000000-0000-0000-0000-000000000000',
--   '00000000-0000-0000-0000-000000000000',0,0,'{}',0,0,0);
-- expect: ERROR: new row violates row-level security policy for table "orders"
-- SELECT * FROM public.vendors LIMIT 1;
-- expect: zero rows visible (policy USING(false) denies SELECT too)

-- 9. Storage: 3 public + 4 private buckets, public-read-only ------------------
SELECT id, public FROM storage.buckets WHERE id IN (
  'vendor-profile-images','menu-images','customer-avatars',
  'vendor-documents','rider-documents','delivery-proofs','support-attachments')
ORDER BY id;
-- expect: 7 rows; public=true for the first three, false for the last four
SELECT policyname, cmd FROM pg_policies
WHERE schemaname = 'storage' AND tablename = 'objects';
-- expect: public_bucket_read / SELECT present; NO INSERT/UPDATE/DELETE policies
-- for anon/authenticated (client writes denied; backend uses service_role).

-- 10. Seed idempotency: run seed twice, counts must not change ---------------
-- psql "$DATABASE_URL" -f supabase/seed/demo.sql   -- run 1, note counts
-- psql "$DATABASE_URL" -f supabase/seed/demo.sql   -- run 2, counts identical
SELECT (SELECT count(*) FROM public.regions) AS regions,
       (SELECT count(*) FROM public.orders) AS orders,
       (SELECT count(*) FROM public.payments) AS payments,
       (SELECT count(*) FROM public.deliveries) AS deliveries,
       (SELECT count(*) FROM public.subscription_daily_orders) AS daily,
       (SELECT count(*) FROM public.outbox_events) AS outbox;
-- expect: regions=5, orders=1, payments=1, deliveries=1, daily=1, outbox=1
-- after both runs (no duplicates). Full reset path:
-- supabase db reset   # applies 0001..0009 + seed; then re-run seed file once more
-- and re-run this query: counts unchanged.
```
