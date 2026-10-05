# Event Architecture

## 1. Rule

State change first (in transaction), side effects second (via events).
Business state must **never** depend on notification/realtime success.

```
COMMAND → Application Service → Domain state change → TX commit
                                                        ↓
                                                   Domain event
                                                        ↓
                        ┌──────────┬──────────┬─────────┴──────────┬──────────┐
                        ▼          ▼          ▼                    ▼          ▼
                   Notification Finance   Realtime            Search/    Analytics
                                                          cache-inval   (fire-and-forget)
```

## 2. Event catalogue (stable names — do not rename casually)

```
Identity/Marketplace:
  UserRegistered, VendorSubmitted, VendorApproved, VendorSuspended,
  MenuPublished, SlotCapacityChanged
Commerce:
  OrderCreated, PaymentSucceeded, PaymentFailed, OrderAccepted,
  OrderPreparing, OrderReady, OrderCompleted, OrderCancelled,
  RefundCreated, RefundCompleted
Fulfillment:
  RiderAssigned, AssignmentExpired, OrderPickedUp, OrderOutForDelivery,
  OrderDelivered
Subscription:
  SubscriptionActivated, SubscriptionPaused, SubscriptionSkipped,
  SubscriptionCancelled, SubscriptionPaymentFailed, DailyMealOrderGenerated
Finance:
  VendorPayoutCreated, RiderPayoutCreated (projections of the above)
```

Payload minimum: `eventId, eventType, occurredAt, aggregateType,
aggregateId, actorId, idempotencyKey, data`. Handlers must be idempotent
(guard on `eventId` / target-state check).

## 3. Delivery mechanism (start simple, stay reliable)

1. **In-process event bus** (Spring `ApplicationEventPublisher`) for all
   non-critical reactions (notifications, realtime fan-out, analytics).
2. **Transactional outbox** for anything that must survive a crash:
   `PaymentSucceeded`, `DailyMealOrderGenerated`, payout triggers.
   - Same TX writes `business row + outbox_event(status=PENDING)`.
   - Background publisher relays via Redis Streams/pub-sub or DB polling,
     marks `SENT`, retries with backoff, dead-letters to ops queue.
   - Prevents "DB committed, event lost on crash".

Do **not** introduce Kafka/RabbitMQ in Phase 1. Redis Streams (or a
`pg → publisher → Redis` bridge) is sufficient; extract a broker only on
measured throughput need.

## 4. Handler map (excerpt)

| Event | Notification | Finance | Realtime | Other |
|-------|--------------|---------|----------|-------|
| `OrderCreated` | vendor "new order" push | — | vendor order list | — |
| `PaymentSucceeded` | customer receipt | commission projection | order timeline | analytics `PAYMENT_SUCCESS` |
| `OrderReady` | rider pool + customer "being packed" | — | vendor/rider/customer | dispatch trigger |
| `RiderAssigned` | rider offer (TTL) | — | rider + vendor | dispatch timeout timer |
| `OrderPickedUp` | customer "on its way" | — | tracking | — |
| `OrderDelivered` → `OrderCompleted` | review prompt | vendor+rider earnings | timeline close | review eligibility |
| `DailyMealOrderGenerated` | vendor+customer | — | subscription calendar | — |
| `RefundCompleted` | customer | ledger | order timeline | support link |

## 5. Redis role in events

Redis holds **ephemeral coordination only**: dispatch offer TTL, pickup-QR
nonce cache, realtime presence, notification dedupe windows. The outbox table
in Postgres is the durable log; Redis is never the log.

## 6. Failure semantics

- Handler throws → retry with backoff; business row already committed, so
  handler must check "already applied?" before re-applying.
- Publisher down → outbox rows stay `PENDING`; ops alert fires; replay on recovery.
- Notification provider down → order stays `READY`; retry queue, no state rollback.
- Duplicate delivery (at-least-once) → dedupe by `eventId`.

## 7. What to implement first

1. `DomainEvent` + `OutboxEvent` entity/migration.
2. `EventPublisher` abstraction (sync now, swappable later).
3. Outbox writer in Commerce + Subscription services.
4. Relay worker + `notification` handlers for order lifecycle.
5. Realtime adapter (WebSocket/SSE or Supabase Realtime on server-authored channels, auth-checked).
