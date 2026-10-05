# ADR-007 — Subscription Daily-Order Model

## Context

Subscriptions are recurring contracts (meal plan + days + slot + vendor), not
deliveries themselves. Pausing/skipping must not corrupt already-created meals;
scheduler retries must not duplicate meals.

## Decision

Split the models: **`Subscription` (contract + schedule)** vs **`Order`
(operational meal, `subscription_id` FK)**. A scheduled generator
(daily job) finds ACTIVE subscriptions due today, validates vendor/slot/
serviceability/capacity, then creates **one** daily order guarded by unique
`(subscription_id, service_date, meal_slot)` + idempotency key
`sub:{id}:{date}:{slot}`. Pause/skip/cancel stops *future* generation only.

Subscription states: `DRAFT PAYMENT_PENDING ACTIVE PAUSED SKIPPED EXPIRED
CANCELLED PAYMENT_FAILED`. Daily orders flow through the normal order/dispatch
state machine.

## Alternatives

- **Subscription = repeating order row**: rejected — pause/cancel semantics ambiguous, history muddled.
- **Pre-create all future orders**: rejected — unbounded rows, capacity frozen wrongly.
- **Client-side generation**: rejected — duplicate-prone, untrusted.

## Consequences

- (+) Clean pause/skip/cancel; per-day vendor/dispatch flexibility; idempotent tick.
- (+) Finance can separate subscription revenue from one-off revenue.
- (−) Scheduler + unique-constraint + failure alerting mandatory; missed-tick
  recovery runbook required.
