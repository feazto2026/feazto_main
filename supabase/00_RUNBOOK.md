# Supabase runbook — reset, push, seed twice, verify

> All commands assume the Supabase CLI is installed and you are in the repo root.
> Anything marked `service_role` needs `SERVICE_ROLE_KEY` (server-only, never
> shipped to mobiles/web). Anything marked `user JWT` is the denial proof and
> must FAIL.

## 0. Prerequisites

```bash
supabase --version
supabase status                 # local stack up (DB, Auth, Storage)
# For remote work:
# supabase link --project-ref <ref>   # once per clone
```

## 1. Fresh reset (local) — migrations 0001..0009 + seed

```bash
supabase db reset
# expect: applies migrations/0001..0009 in order, then seed/demo.sql
# expect NOTICE: demo seed ok: regions=5, cuisines=4, zones=4, slots=4,
#   items=4, orders=1, payments=1, deliveries=1, subs=1, daily=1,
#   payouts=1, reviews=1, tickets=1, notifs=1, outbox=1, idem=1
```

Manual equivalent (vanilla Postgres / CI without the CLI):

```bash
for f in supabase/migrations/000?.sql supabase/migrations/0009_fixes.sql; do
  psql "$DATABASE_URL" -f "$f"
done
psql "$DATABASE_URL" -f supabase/seed/demo.sql
psql "$DATABASE_URL" -f supabase/auth-hooks/sync-platform-user.sql
```

Order matters: `0001..0009` numeric → `seed/demo.sql` → auth hook.
Never apply `queries/`, `functions/`, `views/`, `policies/`, `triggers/` or
`seeds/verify_seed_counts.sql` as migrations — they are a read-only library.

## 2. Push to linked remote

```bash
supabase db push                 # pushes pending migrations 0001..0009 only
psql "$DATABASE_URL" -f supabase/seed/demo.sql            # demo data (staging/dev)
psql "$DATABASE_URL" -f supabase/auth-hooks/sync-platform-user.sql
```

New change? Add `supabase/migrations/0010_<scope>.sql` (never edit `0001..0009`),
test with `supabase db reset`, then `supabase db push`. See
`migrations/00_overview.md` rules.

## 3. Seed twice — idempotency proof (counts must be IDENTICAL)

```bash
psql "$DATABASE_URL" -f supabase/seed/demo.sql
psql "$DATABASE_URL" -f supabase/seed/demo.sql
psql "$DATABASE_URL" -f supabase/seeds/verify_seed_counts.sql
# expect: regions=5, orders=1, payments=1, deliveries=1, daily=1, outbox=1
# (both runs; full table-count query lives in seeds/verify_seed_counts.sql)
```

Seed uses fixed UUIDs + fixed `2026-10-*` dates + `ON CONFLICT DO NOTHING`, so
re-seeding is a no-op. If any count grows on the second run, the seed regressed.

## 4. Verify — constraints + RLS + storage (read-only, `service_role`)

Run every query block in `supabase/config/rls.md` Verification (§0–§10):

```bash
# §0..§7, §9, §10 need service_role / superuser:
# expect: 56 business tables, 4 forward FKs, 8 CHECKs, 15 listed indexes,
#   deny-all policies on all 56, 7 buckets (3 public + 4 private).
```

Then the denial proof (§8) with a **user JWT** (`anon`/`authenticated`), not
`service_role`:

```sql
-- expect: ERROR new row violates row-level security policy for table "orders"
-- expect: SELECT * FROM public.vendors LIMIT 1  →  zero rows visible
```

Quick health snapshot (any `service_role` session):

```bash
psql "$DATABASE_URL" -f supabase/queries/12_admin.sql
```

## 5. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `relation "public.platform_users" does not exist` from hook | Hook applied before 0001 | Apply migrations first, then the hook |
| Seed counts grow on re-run | Seed edited without `ON CONFLICT DO NOTHING` / fixed UUIDs | Restore conflict guards (see `seeds/README.md`) |
| `violates row-level security` in backend | Backend used `ANON_KEY` instead of `SERVICE_ROLE_KEY` | Switch backend to `service_role`; clients stay on `anon` |
| Storage write denied for backend | Backend used anon client for upload | Upload via backend with `service_role` or mint signed upload URLs |
| `duplicate key: subscription_daily_orders…` on generator | Normal — generator raced; `ON CONFLICT DO NOTHING` absorbed it | No action (see `functions/fulfillment_subscription.sql` S2) |
| New table visible to clients | Forgot deny-all on the new table | Add it to the 0008/0009 loop pattern in a new `0010` migration, then re-run `policies/access.sql` P1 audit |
