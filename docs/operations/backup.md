# Backup & recovery

> Hierarchy (MASTER_PROMPT §34/§42): **PostgreSQL via Supabase is the system of
> record.** Redis is reconstructable support state. Back up Postgres like money
> depends on it (it does); treat Redis as a cache you can warm again.

## 1. What gets backed up

| Data | Authority | Strategy |
|---|---|---|
| Postgres business records (users, vendors, menus, carts, orders, payments, subscriptions, deliveries, payouts, audit) | Supabase Postgres | Automated daily backups + point-in-time recovery (PITR) on staging/prod; manual `pg_dump` before migrations |
| Supabase Auth users / Storage objects (menus, KYC docs, delivery proofs) | Supabase | Included in project backups; plus Storage bucket replication/export policy per data-retention rules |
| Redis (rate limits, OTP throttle, idempotency windows, locks, cache, dispatch scratch) | Ephemeral | RDB + AOF on the volume (`redis-data`) for crash restart only — **not** a substitute for Postgres. Rebuild by warming caches / re-issuing idempotency windows |
| Secrets / keys | Vault / provider dashboards | Versioned in the secret manager with rotation history; never in git |

Financial rows must be immutable snapshots (`*_snapshot` columns at order time —
MASTER_PROMPT §11). Never rewrite history; issue reversal/refund rows instead.

## 2. Postgres backup

```bash
# Manual pre-migration snapshot (run BEFORE any supabase/migrations change):
pg_dump "$DATABASE_URL" -Fc -f "backups/pre-migrate-$(date +%F-%H%M).dump"

# Restore to a scratch database for verification (never overwrite prod blindly):
pg_restore -d "$SCRATCH_DATABASE_URL" --clean --if-exists "backups/pre-migrate-<ts>.dump"
```

- Supabase dashboard: confirm daily backups + PITR retention for the environment.
- Migration rollback: each migration needs a tested `down` (or compensating)
  migration; rehearse restore on staging before touching prod.
- Keep at least one known-good dump per release tag.

## 3. Redis recovery

Redis data is **reconstructable by design**:

```bash
docker compose stop redis && docker compose up redis
docker exec feazto-redis redis-cli ping   # PONG
```

- Normal restarts replay `appendonly.aof` + `dump.rdb` from `redis-data`.
- `docker compose down -v` drops the volume — acceptable in local/dev because
  idempotency windows re-form, caches re-warm, and locks expire by TTL.
- Never promote Redis to source of truth for orders/payments/subscriptions
  (MASTER_PROMPT §12). If any flow cannot recover after a Redis flush, that is
  an application bug — fix the flow, not the backup policy.

## 4. Restore procedure (order matters)

1. **Stop writers:** scale API to 0 / maintenance mode so no new orders/payments land mid-restore.
2. **Restore Postgres** to the target (PITR or verified dump → scratch first, then promote).
3. **Flush / restart Redis** so stale cache/locks cannot shadow restored rows.
4. **Replay outbox:** let the API's outbox publisher re-emit domain events that
   post-date the restore point (MASTER_PROMPT §13); reconcile payment provider
   state via `verifyPayment` before flipping orders to paid.
5. **Verify:** `./scripts/verify.sh --strict`, then order/payment/refund smoke
   tests and finance reconciliation (orders ↔ payments ↔ payouts).
6. **Re-open traffic** and monitor error rate, payment success rate, dispatch rate.

## 5. Secret rotation

- **On schedule:** rotate `OTP_SECRET`, payment keys per provider guidance;
  Supabase keys on personnel change.
- **On suspected leak:** rotate immediately at the provider, update hosting env,
  `docker compose up -d --force-recreate api`, verify with `scripts/verify.sh`.
- Never store the new value in chat, tickets, or git — secret manager only.

## 6. Incident checklist

- [ ] Which restore point? (PITR timestamp / dump file + checksum)
- [ ] Writers stopped before restore?
- [ ] Redis flushed/restarted after Postgres restore?
- [ ] Payment reconciliation completed (no double-charge, no lost webhook)?
- [ ] Subscription daily-order generator re-run / backfill verified?
- [ ] Audit log entry written for the restore itself?
- [ ] Post-mortem filed with root cause + backup-gap fix?
