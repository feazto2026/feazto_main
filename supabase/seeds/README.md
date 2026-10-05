# Seeds

> Canonical demo seed: **`../seed/demo.sql`** (singular `seed/` — kept as-is for
> Supabase CLI compatibility). This folder adds verification around it; it does
> not duplicate seed data.

| File | Purpose |
|---|---|
| `../seed/demo.sql` | Canonical chain: `Anbu Kongu Kitchen` vendor + `WELCOME50` + ACTIVE cart + `ORD-DEMO-0001` + `pay_demo_01` + `SUB-DEMO-0001` + `DEL-DEMO-0001` + commission + both payouts + review + ticket + notification + audit + outbox + idempotency rows. Fixed UUIDs + fixed `2026-10-*` dates + `ON CONFLICT DO NOTHING` → re-seed is a no-op. |
| `verify_seed_counts.sql` | Seed-twice proof: run after each `demo.sql` run; counts must be identical. Mirrors `supabase/config/rls.md` §10. |

## Rules

1. Never generate random UUIDs / `now()` dates in seed data (breaks idempotency).
2. Every seed `INSERT` uses `ON CONFLICT DO NOTHING` on its natural unique key.
3. New demo rows go into `../seed/demo.sql` behind the same guards, and the
   expected `NOTICE` counts + `verify_seed_counts.sql` grow together.
4. KYC/document paths use `vendor-documents/demo/...` placeholders only.
