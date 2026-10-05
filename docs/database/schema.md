# Database Schema — Codewild Food Platform (Supabase PostgreSQL)

Migrations: `supabase/migrations/0001..0009` (apply in numeric order,
overview: `supabase/migrations/00_overview.md`) ·
Seed: `supabase/seed/demo.sql` (+ `supabase/seeds/verify_seed_counts.sql`) ·
RLS: `supabase/config/rls.md` · Storage: `supabase/config/storage.md` ·
Auth hook: `supabase/auth-hooks/sync-platform-user.sql`.
Workspace index: `supabase/README.md` · Runbook: `supabase/00_RUNBOOK.md` ·
Query library: `supabase/queries/` (12 usage-grouped `service_role` files,
`01_auth`–`12_admin`, sections `Q1..Qn` — see `supabase/queries/README.md`) ·
TX wrappers: `supabase/functions/` (`order_lifecycle.sql`,
`fulfillment_subscription.sql`, compose the queries) ·
Reporting: `supabase/views/reporting.sql` (V1–V4) · RLS/storage audit:
`supabase/policies/access.sql` · Trigger reference: `supabase/triggers/automation.sql`.

Conventions (MASTER_PROMPT_ENHANCED.md §10–§11, §21):

- **UUID PKs** (`gen_random_uuid()`), explicit **FKs**, `created_at`/`updated_at`
  (`timestamptz`, auto-maintained by `set_updated_at()` trigger) on every
  mutable table. History/audit tables are **append-only** (no `updated_at`).
- **Money in paise** (`bigint`), never float. Commission rates in basis points.
- **Statuses are `TEXT + CHECK`**, not native enums, so lifecycles evolve
  without enum migrations. All state changes are **server-side transitions**
  that append a `*_status_history` row; clients never write `status`.
- **Financial snapshots**: orders freeze item names/prices, discounts, taxes,
  fees and commission at creation. Reports read the snapshot, never live menus.
- **Idempotency**: `orders`, `payments`, `payment_attempts`, `refunds`,
  `subscriptions`, `subscription_daily_orders`, `deliveries`,
  `delivery_assignments`, `vendor/rider_payouts`, `outbox_events` and the
  `idempotency_keys` ledger all carry `UNIQUE` idempotency keys.
- **Soft-delete** only where history must survive user edits
  (`platform_users.deleted_at`, `vendors.deleted_at`, `menu_items.deleted_at`).
  Financial/fulfillment rows are never deleted (`RESTRICT` FKs).

## Entity graph

```text
platform_users ─┬─ user_roles ── roles ── role_permissions ── permissions
                │
                ├─ customer_profiles ─┬─ addresses
                │                     │
                │                     ├─ carts ── cart_items
                │                     ├─ orders ─┬─ order_items (snapshots)
                │                     │          ├─ order_status_history
                │                     │          ├─ payments ─┬─ payment_attempts
                │                     │          │           └─ refunds
                │                     │          ├─ deliveries ─┬─ delivery_assignments
                │                     │          │             └─ delivery_status_history
                │                     │          └─ reviews ── coupons/coupon_usages
                │                     └─ subscriptions ─┬─ subscription_schedules
                │                                       └─ subscription_daily_orders ── orders
                │
                ├─ vendors ─┬─ vendor_regions ── regions
                │           ├─ vendor_cuisines ── cuisines
                │           ├─ vendor_verifications / vendor_documents
                │           ├─ menus ── menu_categories ── menu_items ── menu_item_availability
                │           ├─ vendor_slots ── slots
                │           ├─ vendor_service_zones ── service_zones
                │           ├─ meal_plans
                │           └─ commissions / vendor_payouts(+items)
                │
                └─ riders ──┬─ rider_documents / rider_availability
                            └─ rider_payouts(+items)

Cross-cutting (SHARED/OPERATIONS): support_tickets(+messages), audit_logs,
notifications(+preferences), outbox_events, idempotency_keys
```

## Table inventory by migration

### 0001_identity — Identity & Access (owner: IDENTITY)

| Table | Purpose | Key constraints |
|---|---|---|
| `roles` | System roles (CUSTOMER…FINANCE_ADMIN) | `code` unique, upper-case |
| `permissions` | Scopes (`VENDOR_APPROVE`, `PAYOUT_MANAGE`…) | `code` unique |
| `role_permissions` | Least-privilege mapping | PK(`role_id`,`permission_id`) |
| `platform_users` | App-level user (links `auth_user_id` → Supabase Auth) | `auth_user_id`/`phone`/`email` unique; ≥1 contact |
| `user_roles` | Multi-role grants | PK(`user_id`,`role_id`) |

Seeded: 8 roles, 22 permissions, SUPER_ADMIN←all + least-privilege maps.

### 0002_customer — Customer (owner: CUSTOMER)

| Table | Purpose | Key constraints |
|---|---|---|
| `customer_profiles` | Profile + hometown/discovery prefs (FK to `regions` attached in 0003) | `user_id` unique |
| `addresses` | Address book; frozen into orders at purchase | partial unique: one default per customer |

### 0003_marketplace — Marketplace (owner: MARKETPLACE; verification co-owned OPERATIONS)

| Table | Purpose | Key constraints |
|---|---|---|
| `regions` | STATE/DISTRICT/CITY/CULTURAL_REGION taxonomy | `code` unique; self `parent_id` |
| `cuisines` | Regional/community/meal-type taxonomy | `code` unique |
| `vendors` | Home-kitchen profile + lifecycle + capacity | `user_id` unique; status CHECK |
| `vendor_regions` / `vendor_cuisines` | NATIVE/SPECIALTY/SERVED links | composite PKs |
| `vendor_verifications` | KYC/FSSAI/ADDRESS/BANK/DOCUMENT reviews | partial unique: one open request per vendor+type |
| `vendor_documents` | Document objects (private bucket) | — |
| `menus` / `menu_categories` / `menu_items` | Catalogue (prices in paise) | `UNIQUE(menu_id,name)`; trigram + GIN tag indexes (0008) |
| `slots` | Platform slot templates (Breakfast/Lunch/…) | `code` unique; `starts_at < ends_at` |
| `vendor_slots` | Per-vendor capacity + service days (DB authoritative) | `UNIQUE(vendor_id,slot_id)`; `reserved ≤ total` |
| `service_zones` | CITY/AREA/PIN/RADIUS/POLYGON serviceability data | `code` unique |
| `vendor_service_zones` | Which vendor serves which zone (+fee override) | composite PK |
| `menu_item_availability` | Per-day quantities (`service_date` NULL = recurring) | `UNIQUE(menu_item_id,vendor_slot_id,service_date)`; `reserved ≤ available` |

### 0004_commerce — Commerce (owner: COMMERCE; payments co-owned FINANCE)

| Table | Purpose | Key constraints |
|---|---|---|
| `coupons` / `coupon_usages` | PERCENT/FLAT/FREE_DELIVERY + redemption log | `code` unique; `UNIQUE(coupon_id,order_id)` via `coupon_usages.order_id` unique |
| `carts` / `cart_items` | Single-vendor cart; price snapshot per line | partial unique: one ACTIVE cart per (customer,vendor); `UNIQUE(cart_id,menu_item_id,customization_hash)` |
| `orders` | Order + full financial snapshot + frozen address | `order_number`/`idempotency_key` unique; `total = subtotal − discount + taxes/fees` CHECK |
| `order_items` | Immutable line snapshots (`SET NULL` to menu item) | FK `order_id` CASCADE |
| `order_status_history` | Append-only transitions | optional `idempotency_key` unique |
| `payments` | Provider-agnostic; SUCCESS only after server verify | `provider_payment_id`/`idempotency_key` unique; ≥1 subject (order/subscription) |
| `payment_attempts` | Per-try request/response log | `UNIQUE(payment_id,attempt_no)`; `idempotency_key` unique |
| `refunds` | Server-computed amounts, auditable | `idempotency_key`/`provider_refund_id` unique |

### 0005_subscription — Subscription (owner: SUBSCRIPTION)

| Table | Purpose | Key constraints |
|---|---|---|
| `meal_plans` | Vendor plan catalogue | days ⊆ 0..6 |
| `subscriptions` | Recurring contract + frozen price | `subscription_number`/`idempotency_key` unique; partial unique: one live (ACTIVE/PAUSED) per (customer,vendor,plan) |
| `subscription_schedules` | Weekday × slot firing rules | `UNIQUE(subscription_id,weekday,meal_slot_id)` |
| `subscription_daily_orders` | Idempotent generation log → `orders` | **`UNIQUE(subscription_id,service_date,meal_slot_id)`** + `idempotency_key` unique |
| `subscription_status_history` | Append-only | — |

Also attaches `orders.subscription_id`, `payments.subscription_id` FKs (columns
created nullable in 0004 to preserve dependency order).

### 0006_fulfillment — Fulfillment (owner: FULFILLMENT)

| Table | Purpose | Key constraints |
|---|---|---|
| `riders` | Partner profile + KYC/vehicle/online state | `user_id` unique |
| `rider_documents` | KYC objects (private bucket) | — |
| `rider_availability` | Weekly windows | `UNIQUE(rider_id,weekday,start,end)` |
| `deliveries` | 1:1 with order (v1); code **hashes** + snapshots | `order_id` unique; `delivery_number`/`idempotency_key` unique |
| `delivery_assignments` | Dispatch offers (zone/availability/distance/workload in `assignment_reason`) | `UNIQUE(delivery_id,rider_id,attempt_no)`; `idempotency_key` unique |
| `delivery_status_history` | Append-only | — |

### 0007_operations — Operations / Finance / Shared

| Table | Owner | Key constraints |
|---|---|---|
| `support_tickets` / `support_ticket_messages` | OPERATIONS | `ticket_number` unique |
| `audit_logs` | OPERATIONS (append-only) | indexed (entity, actor, time) |
| `notifications` / `notification_preferences` | OPERATIONS | `dedupe_key` unique |
| `reviews` | OPERATIONS | `order_id` unique (one review per order) |
| `commissions` | FINANCE (accrue from order snapshot; correct via REVERSED) | `order_id` unique |
| `vendor_payouts` / `vendor_payout_items` | FINANCE | `payout_number`/`provider_reference`/`idempotency_key` unique; `UNIQUE(payout_id,order_id)` |
| `rider_payouts` / `rider_payout_items` | FINANCE | same pattern on `(payout_id,delivery_id)` |
| `outbox_events` | SHARED (transactional relay) | `idempotency_key` unique; pending-scan index |
| `idempotency_keys` | SHARED (`UNIQUE(scope,key)`) | expiry-sweep index |

### 0008_indexes_rls — Indexes, RLS, Storage

- ~45 indexes: dispatch/tracking hot paths, generator scans (`ACTIVE` window,
  due daily orders), outbox pending scan, trigram search on vendor/item names,
  GIN on tags/PINs, partial uniques (default address, active cart, open
  verification, live subscription).
- RLS: enabled on all 56 business tables + `deny_all_client_access`
  (`FOR ALL TO anon, authenticated USING (false)`) — see `supabase/config/rls.md`.
  Compatibility preamble creates `anon`/`authenticated` roles when absent
  (no-op on Supabase) and skips storage setup when the storage schema is
  absent (vanilla PG verification).
- Storage buckets + public-read-only policy — see `supabase/config/storage.md`.

### 0009_fixes — Consistency fixes + missing guards (audit close-out)

| Fix | Detail |
|---|---|
| `fk_orders_schedule` | Attaches the last forward FK missed in 0005: `orders.subscription_schedule_id → subscription_schedules(id) ON DELETE SET NULL` (guarded `DO … IF NOT EXISTS` + `ix_orders_schedule` partial index). |
| Payout net CHECKs | `vendor_payouts_net_check` (`net = gross − commission − refunds + adjustments`, `net ≥ 0`), `rider_payouts_net_check` (`net = gross + adjustments`), plus `*_payout_items_amount_check (≥ 0)`. Mirrors `orders_total_consistency_check`. |
| Missing indexes | `ix_vendor_documents_vendor`, `ix_rider_documents_rider`, `ix_subscription_status_history_sub`, `ix_vendor/rider_payout_items_{payout,order/delivery}`, `ix_coupons_validity`, `ix_menu_item_avail_slot_date`, `ix_support_tickets_{order,delivery}`, `ix_notifications_dedupe`, `ix_commissions_status`. All `IF NOT EXISTS`. |
| Storage hardening | Re-upserts all 7 buckets with `ON CONFLICT DO UPDATE SET public = EXCLUDED.public` (repairs drift) + re-asserts `public_bucket_read` SELECT-only policy. |
| RLS re-assert | Re-runs the 56-table `ENABLE RLS + deny_all_client_access` loop (additive, repairs manually dropped policies). No new tables; `service_role` unaffected. |

Auth hook (`supabase/auth-hooks/sync-platform-user.sql`, not a numbered
migration): provisions `platform_users(auth_user_id)` + `CUSTOMER` grant from
`roles(code)` on `auth.users` insert. Rewritten to match 0001 (the previous
version referenced legacy `public.users` / `role TEXT` and is removed). Guarded
with a `platform_users`-exists check, `OR REPLACE` function, `ON CONFLICT`
upserts, and `deny_all_client_access` re-assert.

## Query paths — every app element has a table + backend query

All reads/writes go through Spring Boot with `service_role` (RLS denies direct
client access). Each row names the authoritative table(s) and the canonical
backend query pattern the API must use (list/detail + write guard). The `Files`
column names the canonical `supabase/queries/` single-statement source(s) and,
where one exists, the `supabase/functions/` multi-statement TX wrapper.
Client column shows which of the 3 mobiles + admin web each path serves:
C = customer_app, V = vendor_app, R = rider_app, A = admin web.

| Client(s) | App element | Table(s) | Canonical query path (backend, `service_role`) | Files |
|---|---|---|---|---|
| C,V,R,A | Auth / users / roles | `platform_users`, `roles`, `user_roles`, `permissions`, `role_permissions` | `SELECT pu.*, array_agg(r.code) FROM platform_users pu LEFT JOIN user_roles ur ON ur.user_id=pu.id LEFT JOIN roles r ON r.id=ur.role_id WHERE pu.auth_user_id=$1 GROUP BY pu.id` — resolve `auth_user_id → platform_user → roles/permissions`; approval grants insert `user_roles` + `audit_logs` in one TX. | `queries/01_auth.sql` Q1, Q2 |
| C | Customer profiles / addresses | `customer_profiles`, `addresses` | Profile: `SELECT * FROM customer_profiles WHERE user_id=$1`; address book: `SELECT * FROM addresses WHERE customer_id=$1 AND is_active ORDER BY is_default DESC`; default enforced by `uq_addresses_single_default`. | `queries/02_customers.sql` Q1–Q3 |
| C,V,A | Regions / cuisines (discovery taxonomy) | `regions`, `cuisines` | `SELECT * FROM regions WHERE kind IN ('CITY','CULTURAL_REGION') ORDER BY name`; `SELECT * FROM cuisines ORDER BY name` — cached; vendor discovery filters by these codes, never hard-coded cities. | (taxonomy — no dedicated query file; cached) |
| C,V,A | Vendors / verifications / docs | `vendors`, `vendor_verifications`, `vendor_documents` | Listing: `SELECT * FROM vendors WHERE status='APPROVED' AND is_active AND city=$1` (`ix_vendors_status_active`); verification queue: `SELECT * FROM vendor_verifications WHERE status='PENDING'`; docs via signed URLs from `vendor-documents` bucket. Open-request guard: `uq_vendor_verifications_single_open`. | `queries/03_marketplace.sql` Q1, Q2 |
| C,V | Menus / categories / items / availability | `menus`, `menu_categories`, `menu_items`, `menu_item_availability` | Menu: `SELECT mi.* FROM menu_items mi JOIN menus m ON m.id=mi.menu_id WHERE m.vendor_id=$1 AND mi.is_active AND mi.deleted_at IS NULL` (`ix_menu_items_vendor_avail` + trigram `ix_menu_items_name_trgm` for search); day quantities: `SELECT * FROM menu_item_availability WHERE menu_item_id=$1 AND (service_date=$2 OR service_date IS NULL)`. | `queries/03_marketplace.sql` Q3–Q5 |
| C,V | Slots / vendor slots | `slots`, `vendor_slots` | Templates: `SELECT * FROM slots WHERE is_active ORDER BY starts_at`; capacity check: `SELECT capacity_total-capacity_reserved AS left FROM vendor_slots WHERE vendor_id=$1 AND slot_id=$2` (`vendor_slots_capacity_check` + `ix_vendor_slots_vendor`); holds via `lock:slot:*` + DB check. | `queries/03_marketplace.sql` Q6, Q7 |
| C,V,A | Service zones | `service_zones`, `vendor_service_zones` | Serviceability: `SELECT sz.* FROM service_zones sz JOIN vendor_service_zones vsz ON vsz.service_zone_id=sz.id WHERE vsz.vendor_id=$1 AND sz.is_active` (`ix_service_zones_city`, GIN `ix_service_zones_pins`); PIN lookup uses `postal_codes @>`. | `queries/03_marketplace.sql` Q8 + wrapper `functions/order_lifecycle.sql` S3 |
| C | Carts / cart items / coupons | `carts`, `cart_items`, `coupons`, `coupon_usages` | Active cart: `SELECT * FROM carts WHERE customer_id=$1 AND status='ACTIVE'` (`uq_carts_single_active` enforces one per vendor); lines: `SELECT * FROM cart_items WHERE cart_id=$1`; coupon validate: `SELECT * FROM coupons WHERE code=$1 AND is_active AND (valid_from IS NULL OR valid_from<=now())` (`ix_coupons_validity`). | `queries/04_cart.sql` Q1–Q3 |
| C,V,A | Orders / items / history / snapshots | `orders`, `order_items`, `order_status_history` | Detail: `SELECT * FROM orders WHERE id=$1` + `SELECT * FROM order_items WHERE order_id=$1` + `SELECT * FROM order_status_history WHERE order_id=$1 ORDER BY created_at`; transition writes `orders.status` + history row in one TX with `idempotency_key`; totals guarded by `orders_total_consistency_check`; snapshots frozen at creation (see Finance snapshot map). | `queries/05_orders.sql` Q1 + wrapper `functions/order_lifecycle.sql` S1; Q2, Q3 |
| C,A | Payments / attempts / refunds | `payments`, `payment_attempts`, `refunds` | Verify: `SELECT * FROM payments WHERE provider_payment_id=$1` (webhook replay-safe) or `WHERE idempotency_key=$1`; attempts log per try (`UNIQUE(payment_id,attempt_no)`); refund eligibility computed server-side, inserted with `idempotency_key` + `provider_refund_id` UNIQUE. | `queries/06_payments_refunds.sql` Q1 + wrapper `functions/order_lifecycle.sql` S2; Q2; Q3 |
| C,V,A | Subscriptions / schedules / daily orders | `subscriptions`, `subscription_schedules`, `subscription_daily_orders`, `subscription_status_history`, `meal_plans` | Plans: `SELECT * FROM meal_plans WHERE vendor_id=$1 AND is_active`; live guard: `uq_subscriptions_single_live`; generator tick: `INSERT INTO subscription_daily_orders … ON CONFLICT (subscription_id,service_date,meal_slot_id) DO NOTHING` then `INSERT INTO orders …` linked via `orders.subscription_id` + `fk_orders_schedule`; pause never touches generated `orders`. | `queries/07_subscriptions.sql` Q1–Q3 + wrapper `functions/fulfillment_subscription.sql` S2 |
| R,A | Riders / availability / deliveries / assignments / history | `riders`, `rider_documents`, `rider_availability`, `deliveries`, `delivery_assignments`, `delivery_status_history` | Online riders: `SELECT * FROM riders WHERE status='ACTIVE' AND is_online AND city=$1` (`ix_riders_status_online`); dispatch: `INSERT INTO delivery_assignments …` with `assignment_reason {zone, distance, workload}` + TTL; pickup/delivery verify `sha256(code)=*_code_hash AND now()<*_expires_at` server-side (`pickup/delivery_code_hash + *_expires_at`, never plaintext). | `queries/08_fulfillment.sql` Q1, Q2 + wrapper `functions/fulfillment_subscription.sql` S1; Q3 |
| V,R,A | Finance payouts / commissions | `commissions`, `vendor_payouts`, `vendor_payout_items`, `rider_payouts`, `rider_payout_items` | Accrue: `INSERT INTO commissions … FROM orders` frozen snapshot (`order_id` UNIQUE); settle: `INSERT INTO vendor_payouts …` + `vendor_payout_items(payout_id,order_id)` batch with `idempotency_key`; nets guarded by 0009 `*_net_check`; correct via `REVERSED + re-accrue`, never UPDATE. | `queries/09_finance.sql` Q1–Q3; views `views/reporting.sql` V1, V2 |
| C,V,R,A | Notifications / tickets / reviews / audit / outbox / idempotency | `notifications`, `notification_preferences`, `support_tickets`, `support_ticket_messages`, `reviews`, `audit_logs`, `outbox_events`, `idempotency_keys` | Inbox: `SELECT * FROM notifications WHERE recipient_user_id=$1 ORDER BY created_at DESC` (dedupe via `dedupe_key` UNIQUE); tickets: `SELECT * FROM support_tickets WHERE requester_id=$1` + messages thread; review: `INSERT INTO reviews …` (`order_id` UNIQUE = one per order); audit: `INSERT INTO audit_logs …` on every privileged write; events: `INSERT INTO outbox_events …` in the same TX as the business row, relayed via `ix_outbox_pending`; retries collapse on `idempotency_keys(scope,key)` UNIQUE. | `queries/11_notifications.sql` Q1, Q2; `queries/10_support_ops.sql` Q1–Q6 |

Seed coverage (`supabase/seed/demo.sql`): one fixed-UUID demo chain exercises
every row above — `Anbu Kongu Kitchen` vendor + `WELCOME50` coupon + `ACTIVE`
cart + `ORD-DEMO-0001` order/items/history + `pay_demo_01` payment/attempt +
`SUB-DEMO-0001` contract/schedule/daily row + `DEL-DEMO-0001`
delivery/assignment/history + commission + both payouts/items + review + ticket
thread + notification + audit + outbox + idempotency ledger + both document
rows. All inserts use `ON CONFLICT DO NOTHING` with fixed UUIDs and fixed
`2026-10-*` dates, so re-seeding is a no-op (see Verification below).

## Finance snapshot map (§11)

Frozen at order creation: `order_items.{item_name,unit_price,mrp,discount,tax,
cuisine_tags,customization}_snapshot`, `orders.{subtotal,discount,tax,
delivery_fee,platform_fee,packaging_fee,total,commission_bps,
platform_commission,vendor_payout,coupon_code}_snapshot`,
`deliveries.{delivery_fee,rider_payout}_snapshot`, `commissions.*_snapshot`.
Menu/commission/tax edits never rewrite history.

## Idempotency map (§30)

| Operation | Mechanism |
|---|---|
| Create order / payment / refund / subscription | `idempotency_key` UNIQUE + `idempotency_keys(scope,key)` ledger |
| Payment webhook | `provider_payment_id` UNIQUE (replay-safe) |
| Daily subscription generation | `UNIQUE(subscription_id,service_date,meal_slot_id)` |
| Pickup / delivery confirm | `deliveries.idempotency_key` + hash-verified codes (`*_code_hash` + expiry) |
| Dispatch offer | `delivery_assignments.idempotency_key` |
| Payout run | `payouts.idempotency_key` + `UNIQUE(payout,order/delivery)` items |
| Domain events | `outbox_events.idempotency_key` |
| Single-vendor cart / default address / open verification / live subscription | Partial uniques `uq_carts_single_active`, `uq_addresses_single_default`, `uq_vendor_verifications_single_open`, `uq_subscriptions_single_live` |

## Verification (proves 56 tables + constraints + RLS + seed idempotency)

```bash
# 1. Fresh apply (local): migrations 0001..0009 in order + seed
supabase db reset
# expect: NOTICE demo seed ok: regions=5, cuisines=4, zones=4, slots=4,
#   items=4, orders=1, payments=1, deliveries=1, subs=1, daily=1,
#   payouts=1, reviews=1, tickets=1, notifs=1, outbox=1, idem=1

# 2. Seed-twice idempotency: re-run seed, counts must be IDENTICAL (no-op)
psql "$DATABASE_URL" -f supabase/seed/demo.sql
psql "$DATABASE_URL" -f supabase/seed/demo.sql
SELECT (SELECT count(*) FROM public.regions) AS regions,
       (SELECT count(*) FROM public.orders) AS orders,
       (SELECT count(*) FROM public.payments) AS payments,
       (SELECT count(*) FROM public.deliveries) AS deliveries,
       (SELECT count(*) FROM public.subscription_daily_orders) AS daily,
       (SELECT count(*) FROM public.outbox_events) AS outbox;
-- expect: regions=5, orders=1, payments=1, deliveries=1, daily=1, outbox=1
-- (both runs; full table-count query in supabase/config/rls.md §10)

# 3. Constraint + RLS audit (read-only, service_role):
#    run every query in supabase/config/rls.md Verification queries (§0..§10).
# expect: 56 tables, 4 forward FKs, 8 CHECKs, 15 listed indexes,
#   deny-all policies on all 56, 7 buckets (3 public + 4 private),
#   zero client-visible rows with anon/authenticated JWT (§8).
```

## Migration safety notes

- Files are prefix-ordered and dependency-safe: forward FKs
  (`customer→regions`, `orders/payments→subscriptions`, `orders→schedules`)
  are added as nullable columns first, constraints attached later via guarded
  `DO … IF NOT EXISTS (pg_constraint)` blocks — re-runnable, no rebuilds.
- All DDL uses `IF NOT EXISTS` / `DROP … IF EXISTS` + `ON CONFLICT DO NOTHING`
  seeds: `supabase db reset` and re-seeding are safe. 0009 only ADDS
  constraints/indexes/policies; it never drops tables or rewrites CHECKs in
  place (new CHECK names; deployed data validated on `ALTER`).
- No table is dropped or renamed; status CHECKs are extended with new migrations,
  never edited in place for deployed environments.
- RLS deny-policies are additive; enabling them cannot lock out `service_role`
  (bypasses RLS). Verify with the queries in `supabase/config/rls.md`.
- Seed uses fixed UUIDs + fixed `2026-10-*` dates + `ON CONFLICT DO NOTHING`:
  running `demo.sql` twice yields identical counts (no duplicates, no deletes).
- Existing apps untouched: this foundation adds only `supabase/` + `docs/`;
  `customer_app/`, `vendor_app/`, `rider_app/` are not modified.
- Legacy note: `services/api/.../V1__init.sql` (Flyway, TEXT-PK prototype) is
  superseded by `supabase/migrations/0001..0009` (UUID-PK source of truth).
  Do not merge them; the backend must map entities 1:1 to the Supabase schema.
