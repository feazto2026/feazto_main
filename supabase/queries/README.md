# Query library — how to use

> Every file is a **canonical backend pattern** run by Spring Boot with
> `service_role` (RLS denies `anon`/`authenticated`). Copy the SQL into the
> corresponding repository/service — do not point clients at these files and do
> not apply them as migrations (they contain no DDL truth; the migrations own
> that — see `migrations/00_overview.md`).

## Reading a file

12 usage-grouped files (`01_`–`12_`), one per real usage flow. Each file header
states: **Truth** (owning migration), **Role**, **Clients** served, and the
**guards** (`UNIQUE` / `CHECK` / partial unique) that make the pattern safe
under retries and concurrency. Inside, numbered sections (`-- Q1`, `-- Q2`, …)
follow execution order; each section header states `-- Usage:` and the original
file name, and the SQL body below it is preserved **verbatim** (no logic change).
Paired flows also state the **Canonical / TX-wrapper** counterpart (see below).

## Naming convention

- Files: `NN_<flow>` in usage order (`01_auth` … `12_admin`) — the happy-path
  order is visible in the file list.
- Sections: `Qn` per use-case step (`Q1` read/guard first, writes next);
  `verb_noun` snake_case, no abbreviations (`list_` scans, `get_` single-row
  reads, `check_` pre-write guards, `create_/update_/write_/publish_/claim_/
  offer_/transition_/lookup_/log_/accrue_/send_/open_` writes).
- `queries/` files are **single-statement canonical sources** grouped by flow.
  `functions/` files are **multi-statement TX wrappers** (`order_lifecycle.sql`,
  `fulfillment_subscription.sql`) that compose the queries — they never
  duplicate query SQL, they `CALL` it (each wrapper header names its canonical
  sources, each query header names its wrapper where one exists).

## Map (`queries/` — 12 usage-grouped files)

| File | Sections (execution order) |
|---|---|
| `01_auth.sql` | Q1 resolve roles, Q2 grant role with approval |
| `02_customers.sql` | Q1 profile, Q2 address book, Q3 switch default |
| `03_marketplace.sql` | Q1 vendors, Q2 verification queue, Q3 menu, Q4 search, Q5 availability, Q6 slots, Q7 slot capacity, Q8 zone serviceability |
| `04_cart.sql` | Q1 active cart, Q2 lines, Q3 coupon validate |
| `05_orders.sql` | Q1 create order, Q2 order detail, Q3 transition status |
| `06_payments_refunds.sql` | Q1 lookup for verification, Q2 log attempt, Q3 refund request |
| `07_subscriptions.sql` | Q1 plans, Q2 active subscription, Q3 daily tick |
| `08_fulfillment.sql` | Q1 online riders, Q2 offer assignment, Q3 handover verify |
| `09_finance.sql` | Q1 accrue commission, Q2 vendor payout batch, Q3 rider payout batch |
| `10_support_ops.sql` | Q1 open ticket, Q2 ticket thread, Q3 review, Q4 audit, Q5 outbox publish, Q6 idempotency claim |
| `11_notifications.sql` | Q1 list, Q2 send |
| `12_admin.sql` | Q1 platform health snapshot |

## Canonical ↔ TX-wrapper pairs (one use-case, two layers)

| Use-case | Canonical query (single statement) | TX wrapper (multi-statement, `functions/`) |
|---|---|---|
| Zone serviceability | `queries/03_marketplace.sql` Q8 | `functions/order_lifecycle.sql` S3 (steps compose zone + slot + availability reads) |
| Place order | `queries/05_orders.sql` Q1 | `functions/order_lifecycle.sql` S1 |
| Payment webhook | `queries/06_payments_refunds.sql` Q1 (+ Q2) | `functions/order_lifecycle.sql` S2 |
| Subscription generation | `queries/07_subscriptions.sql` Q3 | `functions/fulfillment_subscription.sql` S2 |
| Dispatch offer | `queries/08_fulfillment.sql` Q2 | `functions/fulfillment_subscription.sql` S1 |

No file duplicates another: the wrapper references the canonical file and adds
orchestration (locks, history, outbox, capacity holds). `06_payments_refunds.sql`
Q2 is kept — it is the append-only per-try log (`UNIQUE(payment_id,attempt_no)`),
not covered by the verification lookup.

## Cross-cutting rules (from `docs/database/schema.md`)

1. `$2`-style `idempotency_key` on every write; retries reuse the key and hit
   `ON CONFLICT DO NOTHING`, then read back the existing row.
2. Status changes = `UPDATE status` + `*_status_history` row in the **same TX**;
   allowed edges live in backend code; clients never write `status`.
3. Money in paise; finance reads use frozen `*_snapshot` columns.
4. Codes verified as hashes server-side (`08_fulfillment.sql` Q3).
5. Business row + `outbox_events` row commit together (`10_support_ops.sql` Q5).
6. Multi-step flows run as the transaction scripts in `supabase/functions/`
   (`order_lifecycle.sql`, `fulfillment_subscription.sql`) — the single-file
   queries here are their building blocks.

## History

> 2026-10-03: consolidated from ~40 fragmented `queries/<domain>/NN_*.sql` files
> (per-subfolder `01_` numbering) to the 12 usage-grouped files above; bodies
> preserved verbatim under `-- Qn` sections. The old
> `queries/operations/support/` split (into `support_tickets/`, `reviews/`,
> `audit/`, `outbox/`, `idempotency/`) is carried forward as sections
> Q1/Q2/Q3/Q4/Q5/Q6 of `10_support_ops.sql`.
