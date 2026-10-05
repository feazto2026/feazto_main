# Migrations — ordered truth (DO NOT REORDER)

> Source of truth for ALL DDL. Files `0001..0009` apply in numeric order and
> are **frozen** — never edit in place for deployed environments. Evolve the
> schema with a new `0010_*.sql` migration (status CHECKs are extended, never
> rewritten; see `docs/database/schema.md` Migration safety notes).

| File | Domain / owner | Tables / content |
|---|---|---|
| `0001_identity.sql` | Identity & Access (IDENTITY) | `roles`, `permissions`, `role_permissions`, `platform_users`, `user_roles` + `set_updated_at()` helper + 8 roles / 22 permissions seed |
| `0002_customer.sql` | Customer (CUSTOMER) | `customer_profiles`, `addresses` (partial unique: one default per customer) |
| `0003_marketplace.sql` | Marketplace (MARKETPLACE; verification co-owned OPERATIONS) | `regions`, `cuisines`, `vendors`, `vendor_regions`, `vendor_cuisines`, `vendor_verifications`, `vendor_documents`, `menus`, `menu_categories`, `menu_items`, `slots`, `vendor_slots`, `service_zones`, `vendor_service_zones`, `menu_item_availability` |
| `0004_commerce.sql` | Commerce (COMMERCE; payments co-owned FINANCE) | `coupons`, `coupon_usages`, `carts`, `cart_items`, `orders` (+ snapshot + `orders_total_consistency_check`), `order_items`, `order_status_history`, `payments`, `payment_attempts`, `refunds` |
| `0005_subscription.sql` | Subscription (SUBSCRIPTION) | `meal_plans`, `subscriptions` (partial unique: one live per customer/vendor/plan), `subscription_schedules`, `subscription_daily_orders` (`UNIQUE(subscription_id,service_date,meal_slot_id)`), `subscription_status_history`; attaches `orders.subscription_id`, `payments.subscription_id`, `fk_orders_schedule` (completed in 0009) |
| `0006_fulfillment.sql` | Fulfillment (FULFILLMENT) | `riders`, `rider_documents`, `rider_availability`, `deliveries` (1:1 with order; code **hashes** + expiry), `delivery_assignments`, `delivery_status_history` |
| `0007_operations.sql` | Operations / Finance / Shared | `support_tickets`, `support_ticket_messages`, `audit_logs`, `notifications`, `notification_preferences`, `reviews`, `commissions`, `vendor_payouts`, `vendor_payout_items`, `rider_payouts`, `rider_payout_items`, `outbox_events`, `idempotency_keys` |
| `0008_indexes_rls.sql` | Indexes, RLS, Storage | ~45 indexes (dispatch/tracking, generator scans, outbox pending, trigram/GIN search) + `ENABLE RLS + deny_all_client_access` on all 56 tables + 7 storage buckets + `public_bucket_read` SELECT-only policy |
| `0009_fixes.sql` | Consistency close-out (additive only) | `fk_orders_schedule` + `ix_orders_schedule`, payout net CHECKs (`vendor_payouts_net_check`, `rider_payouts_net_check`, items `amount_check`), missing indexes, storage flag repair, RLS re-assert loop |

## Apply order

```bash
supabase db reset            # applies 0001..0009 in order + seed
# or, against a URL:
psql "$DATABASE_URL" -f supabase/migrations/0001_identity.sql
# ... 0002 .. 0009 in numeric order, then:
psql "$DATABASE_URL" -f supabase/seed/demo.sql
```

## Rules

1. New change = new file `0010_<scope>.sql` (never edit `0001..0009`).
2. All DDL uses `IF NOT EXISTS` / guarded `DO … IF NOT EXISTS (pg_constraint)`
   blocks so re-runs are safe.
3. Forward FKs land nullable first, constraints attach later (see 0005 → 0009).
4. RLS additions are additive; `service_role` bypasses RLS and is unaffected.
5. `supabase/auth-hooks/sync-platform-user.sql` is NOT a numbered migration —
   apply it after `0001..0009` (see `supabase/auth-hooks/README.md`).
6. `supabase/queries|functions|views|policies|triggers|seeds` are a **read-only
   library** derived from these migrations — they define no tables and must
   never be applied as migrations.
