# Supabase — database workspace index

> PostgreSQL (Supabase) is the business truth; Spring Boot with `service_role`
> is the only writer (`Database is not the API`, ADR-010). Full table inventory,
> query paths, finance snapshots, idempotency + verification live in
> `docs/database/schema.md`. Human-readable RLS/storage policy:
> `supabase/config/rls.md`, `supabase/config/storage.md`.

## Layout

| Path | What lives here | Apply? |
|---|---|---|
| `migrations/0001..0009.sql` | **Ordered truth (frozen).** All DDL + RLS + buckets. See `migrations/00_overview.md`. | ✅ in numeric order (`supabase db reset`) |
| `seed/demo.sql` | Canonical demo chain (fixed UUIDs, `ON CONFLICT DO NOTHING`, re-seed safe). | ✅ after migrations |
| `auth-hooks/sync-platform-user.sql` | `auth.users → platform_users` + `CUSTOMER` grant trigger. See `auth-hooks/README.md`. | ✅ once, after migrations |
| `config/rls.md`, `config/storage.md` | Human-readable policy + verification queries (§0–§10). | 📖 docs (SQL inside is verify-only) |
| `queries/` | Canonical backend query library (`service_role`), 12 usage-grouped files (`01_auth`–`12_admin`), sections `Q1..Qn` in execution order. No DDL. | 📖 copy into Spring repositories |
| `functions/` | Multi-statement backend TX wrappers (`order_lifecycle.sql` S1–S3: create order, verify webhook, serviceability; `fulfillment_subscription.sql` S1–S2: dispatch offer, subscription generation). Run from Spring, not as migrations. Each wrapper header names its canonical `queries/` sources. | 📖 backend reference |
| `views/` | Opt-in reporting `SELECT`s / `CREATE OR REPLACE VIEW`s over frozen snapshots (`reporting.sql` V1–V4: `v_vendor_earnings`, `v_rider_earnings`, `v_order_timeline`, `v_dashboard_summary`). | 📖 optional analytics |
| `policies/` | Re-assert / audit snippets mirroring 0008+0009 (`access.sql` P1 deny-all, P2 storage read). | 📖 audit / repair only |
| `triggers/` | Trigger-pattern reference (`automation.sql` T1 `set_updated_at()`, T2 optional outbox `NOTIFY`). Truth stays in migrations. | 📖 reference (already installed by migrations) |
| `seeds/` | `README.md` + `verify_seed_counts.sql` (seed-twice proof). Canonical seed file stays at `seed/demo.sql`. | 📖 verify |

## Query library map (`queries/`)

Derived 1:1 from the `docs/database/schema.md` Query-paths table. Every file
states its truth migration, role (`service_role`), clients served, params and
guards. Clients never run these directly — RLS denies `anon`/`authenticated`.
Numbered `NN_` prefix = usage-flow order; `Qn` = section order within the file.
Full convention + canonical↔wrapper pairs: see `queries/README.md`.

| File | Use-cases |
|---|---|
| `queries/01_auth.sql` | Q1 resolve roles (auth_user_id → user + roles/permissions), Q2 approval grant + audit in one TX |
| `queries/02_customers.sql` | Q1 profile, Q2 address book, Q3 switch default |
| `queries/03_marketplace.sql` | Q1 vendors, Q2 verification queue, Q3 menu, Q4 search, Q5 availability, Q6 slots, Q7 slot capacity, Q8 zone serviceability (canonical; wrapper: `functions/order_lifecycle.sql` S3) |
| `queries/04_cart.sql` | Q1 active cart, Q2 lines, Q3 coupon validate |
| `queries/05_orders.sql` | Q1 create order (canonical; wrapper: `functions/order_lifecycle.sql` S1), Q2 order detail, Q3 transition status |
| `queries/06_payments_refunds.sql` | Q1 lookup for verification (canonical; wrapper: `functions/order_lifecycle.sql` S2), Q2 log attempt (append-only per-try log — kept, not merged), Q3 refund request |
| `queries/07_subscriptions.sql` | Q1 plans, Q2 active subscription, Q3 daily tick (canonical; wrapper: `functions/fulfillment_subscription.sql` S2) |
| `queries/08_fulfillment.sql` | Q1 online riders, Q2 offer assignment (canonical; wrapper: `functions/fulfillment_subscription.sql` S1), Q3 handover verify (hash-verified pickup/delivery) |
| `queries/09_finance.sql` | Q1 accrue commission, Q2 vendor payout batch, Q3 rider payout batch |
| `queries/10_support_ops.sql` | Q1 open ticket, Q2 ticket thread, Q3 review, Q4 audit, Q5 outbox publish, Q6 idempotency claim |
| `queries/11_notifications.sql` | Q1 list, Q2 send |
| `queries/12_admin.sql` | Q1 platform health snapshot (56-table / RLS / seed health snapshot) |

> History (2026-10-03): consolidated from ~40 fragmented
> `queries/<domain>/NN_*.sql` files to the 12 files above; bodies preserved
> verbatim under `-- Qn` sections.

## Transaction wrappers (`functions/`), views, policies, triggers

| Folder | Files |
|---|---|
| `functions/` | `order_lifecycle.sql` (S1 create order, S2 verify webhook, S3 serviceability), `fulfillment_subscription.sql` (S1 dispatch offer, S2 subscription generation) |
| `views/` | `reporting.sql`: V1 `v_vendor_earnings`, V2 `v_rider_earnings`, V3 `v_order_timeline`, V4 `v_dashboard_summary` |
| `policies/` | `access.sql`: P1 deny-all client access (56-table audit + re-assert), P2 public storage read (storage audit) |
| `triggers/` | `automation.sql`: T1 `set_updated_at()` canonical body, T2 optional outbox `NOTIFY` fast-path |

## Conventions (all library files)

- Money in **paise** (`bigint`); commission in **basis points**.
- Statuses are `TEXT + CHECK` — transitions append a `*_status_history` row in
  the same TX; clients never write `status`.
- Every mutating pattern carries an `idempotency_key` (`UNIQUE`) and, where the
  schema has one, respects the partial-unique guard (`uq_*`).
- Financial reads use the frozen `*_snapshot` columns, never live menus.
- Pickup/delivery codes are verified as `sha256(code) = *_code_hash` with expiry
  checks, server-side only — never plaintext, never client-side.

## Runbook

Day-to-day commands (reset, push, seed-twice, verify): see `00_RUNBOOK.md`.
