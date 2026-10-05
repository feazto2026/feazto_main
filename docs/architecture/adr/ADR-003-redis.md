# ADR-003 — Redis Responsibilities

## Context

Need rate limiting, OTP throttling, idempotency, capacity-race protection,
cache, and short-lived dispatch coordination without burdening Postgres.

## Decision

Redis is **supporting infrastructure, never the record**:
OTP throttle, rate limits, short-lived tokens/nonces (pickup QR, PoD OTP),
`idempotency:{op}:{key}` results, distributed locks (`lock:order:*`,
`lock:slot:*`), cache (`cache:vendor:*`, `cache:serviceability:*`), dispatch
offer TTL + presence. Every key gets a deliberate TTL; all financial/fulfilment
facts live in Postgres.

## Alternatives

- **Postgres-only**: rejected — hot-path contention (slot reservation) and
  rate-limit write amplification.
- **Redis as primary for orders/dispatch**: rejected — data-loss risk, breaks
  "business truth" hierarchy.
- **No Redis (in-memory)**: rejected — multi-instance unsafe, no shared locks.

## Consequences

- (+) Cheap contention control; lock-then-confirm-in-DB pattern prevents oversell.
- (+) Idempotency and throttling survive restarts independently of app nodes.
- (−) Two systems to operate; Redis treated as **reconstructable** — runbooks
  must cover cold-cache/lost-lock recovery; Postgres remains authoritative.
- (−) Key-naming + TTL discipline required; undocumented keys are a defect.
