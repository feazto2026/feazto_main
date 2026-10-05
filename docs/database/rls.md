# Database RLS & Access Policy

> Enforces ADR-010 (direct-DB ban). Postgres is the business truth; RLS is the
> second lock on the door, not the business layer.
> Source of truth for SQL: `supabase/migrations/0008_indexes_rls.sql` +
> `supabase/migrations/0009_fixes.sql`. Human-readable policy + verification:
> `supabase/config/rls.md` (this doc mirrors it; fix drift here first).

## 1. Doctrine

1. **Deny by default**: every business table (56) has `ENABLE ROW LEVEL SECURITY`
   + a single `deny_all_client_access` policy
   (`FOR ALL TO anon, authenticated USING (false) WITH CHECK (false)`).
2. **Backend owns business writes** with `service_role` (server-only, never shipped;
   bypasses RLS). All state machines, snapshots, idempotency live in Spring Boot.
3. **Clients use `anon`/`authenticated`** solely for: Supabase Auth
   (sign-in/OTP/refresh) and public-bucket reads (`vendor-profile-images`,
   `menu-images`, `customer-avatars`). No client INSERT/UPDATE/DELETE anywhere.
4. **KYC/finance/PII tables**: no direct client access at all (no SELECT policy
   for `authenticated`); reads go through `/api/v1` with scoped permissions.
5. **Storage**: public buckets only for shareable menu/vendor display images;
   KYC/rider docs/PoD/support attachments in **private buckets**, accessed via
   signed URLs minted by the backend after authz (+ `audit_logs` for admin views).

## 2. Table classes (all 56, Phase-1 posture = deny-all; Class B reads only if ADR-010 approves)

| Class | Tables | `authenticated` policy (today) |
|-------|--------|-------------------------------|
| A — no client access (default) | `orders`, `order_items`, `order_status_history`, `payments`, `payment_attempts`, `refunds`, `subscriptions`, `subscription_schedules`, `subscription_daily_orders`, `subscription_status_history`, `deliveries`, `delivery_assignments`, `delivery_status_history`, `commissions`, `vendor_payouts`, `vendor_payout_items`, `rider_payouts`, `rider_payout_items`, `audit_logs`, `outbox_events`, `idempotency_keys`, `vendor_verifications`, `vendor_documents`, `rider_documents`, `support_tickets`, `support_ticket_messages`, `notifications`, `notification_preferences`, `reviews` | **none** (deny all; backend `service_role` only) |
| B — backend-mediated (catalog/own-row reads only where approved) | `platform_users`, `customer_profiles`, `addresses`, `roles`, `permissions`, `role_permissions`, `user_roles`, `regions`, `cuisines`, `vendors`, `vendor_regions`, `vendor_cuisines`, `menus`, `menu_categories`, `menu_items`, `menu_item_availability`, `slots`, `vendor_slots`, `service_zones`, `vendor_service_zones`, `coupons`, `coupon_usages`, `carts`, `cart_items`, `meal_plans`, `riders`, `rider_availability` | **none today** (deny all). To open a read: narrow `FOR SELECT TO authenticated USING (ownership/published check)` + ADR-010 review; never client writes. |
| C — auth system | `auth.users` (managed by Supabase) | via Supabase Auth SDK only; platform row provisioned by `supabase/auth-hooks/sync-platform-user.sql` → `platform_users(auth_user_id)` + `CUSTOMER` grant. |

> Legacy names that must NOT be used: `users` (use `platform_users`),
> `user_roles(role TEXT)` (use `user_roles(role_id → roles)`),
> `dispatch_assignments` (use `delivery_assignments`), `payouts` (use
> `vendor_payouts`/`rider_payouts`), `outbox_event` (use `outbox_events`),
> `profiles` (use `customer_profiles`), `customer_addresses` (use `addresses`).

Phase-1 posture: **Class B = deny-all too**. Default new table = Class A.
Any future client-direct access MUST add an explicit allow-policy + ADR-010 review.

## 3. Reference policy shapes (deny-by-default; illustrative allow-shape only)

```sql
-- Deny-by-default (what 0008/0009 install on every business table):
CREATE POLICY deny_all_client_access ON public.orders
  FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);

-- Example Class-B own-row read (ONLY where API plan explicitly approves it):
-- create policy "own profile read"
-- on public.customer_profiles for select to authenticated
-- using (auth.uid() = auth_user_id);  -- adapt to platform_users linkage; review first!

-- No client INSERT/UPDATE/DELETE policies on Class A, ever.
-- Storage: private bucket, backend-minted signed URLs only:
--   select: none for anon/authenticated on private buckets (signed URLs),
--   insert/update/delete: none (backend writes with service_role).
```

## 4. Migration discipline

- Versioned migrations in `supabase/migrations/` (`0001_*.sql` … `0009_*.sql`,
  numeric order), applied via `supabase db push` / `supabase db reset`;
  backend entities map 1:1 to the 56 tables.
- Every migration: UUID PKs, FKs (nullable-first + guarded `DO … IF NOT EXISTS`
  for forward refs: `fk_customer_hometown_region`, `fk_orders_subscription`,
  `fk_payments_subscription`, `fk_orders_schedule`), unique guards
  (`orders(idempotency_key)`, `payments(provider_payment_id)`,
  `subscription_daily_orders(subscription_id,service_date,meal_slot_id)`,
  `idempotency_keys(scope,key)`), CHECKs (`orders_total_consistency_check`,
  `vendor_slots_capacity_check`, `*_net_check`, period/date guards), indexes for
  high-volume queries (vendor orders, rider assignments, outbox scan, trigram
  search), `created_at/updated_at` triggers via `set_updated_at()`, RLS
  enablement + `deny_all_client_access` review.
- Seed (`supabase/seed/demo.sql`) holds fixed-UUID demo data only
  (regions/cuisines/slots/zones + one end-to-end order chain) with
  `ON CONFLICT DO NOTHING` + fixed `2026-10-*` dates — never real PII.
  Re-running the seed is a no-op (identical counts).
- Rollback: each migration ships additive-only changes (new constraints/indexes
  with new names); finance/audit tables are append-only (no destructive
  rollback without payout freeze + `REVERSED` + re-accrue).
- Verification: run all queries in `supabase/config/rls.md` §0..§10 after
  `supabase db reset` + seed-twice check
  (`regions=5, orders=1, payments=1, deliveries=1, daily=1, outbox=1`).

## 5. Quick verification (full queries in `supabase/config/rls.md`)

```sql
-- RLS on all 56? expect zero missing:
-- (business-table list) EXCEPT (pg_class where relrowsecurity) → 0 rows
-- Deny policies on all 56? expect zero missing:
-- (business-table list) EXCEPT (pg_policy deny_all_client_access) → 0 rows
-- Buckets: 7 rows (3 public + 4 private); storage.objects has only
-- public_bucket_read SELECT, no client INSERT/UPDATE/DELETE.
-- Seed twice: counts unchanged (see docs/database/schema.md Verification).
```
