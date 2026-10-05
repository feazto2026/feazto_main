# ADR-004 — Event / Outbox Strategy

## Context

Order/payment/subscription/dispatch fan out to notifications, finance,
realtime, analytics. Crash between "DB commit" and "event send" must not lose
money-critical facts. Team cannot operate a broker yet.

## Decision

**In-process domain events** for reactions + **transactional outbox in Postgres**
for durable facts (`PaymentSucceeded`, `DailyMealOrderGenerated`, payout
triggers). Same-TX `business row + outbox_event(PENDING)`; background publisher
relays via Redis Streams/pub-sub (or DB poll), with retry + dead-letter to ops.
Handlers idempotent on `eventId`. No Kafka/RabbitMQ in Phase 1.

## Alternatives

- **Direct synchronous calls**: rejected — notification failure would roll back
  business state; tight coupling.
- **Broker from day one**: rejected — ops burden before throughput justifies it.
- **DB polling only, no abstraction**: rejected — harder to swap transport later.

## Consequences

- (+) No lost `PaymentSucceeded`; safe retries; clear seam to add a broker later.
- (+) Business code depends on `EventPublisher` abstraction, not Redis APIs.
- (−) At-least-once delivery — every handler must dedupe/re-check state.
- (−) Publisher worker + outbox monitoring are mandatory, not optional.
