# Subscription Workflow (recurring meals)

## 1. Mental model

A **Subscription is a contract**, not a delivery. It *generates* daily Orders
that flow through the normal fulfilment machine.

```
Subscription (contract)                Daily Orders (operational)
──────────────────────                 ──────────────────────────
customer + vendor + meal plan          ┌─ 2026-10-03 lunch → Order #A
days[] + slot + address                ├─ 2026-10-04 lunch → Order #B (skip → none)
schedule + price snapshot              └─ 2026-10-05 lunch → Order #C
state: ACTIVE/PAUSED/…                    each: PLACED→…→COMPLETED independently
```

## 2. ASCII flow

```
Customer                    Spring Boot                      Vendor/Rider
   │                            │                                 │
   ├─ choose vendor+plan+days/slot                              │
   ├─ POST /subscriptions ──► validate · price · create(DRAFT)   │
   ├─ pay ──────────────────► verify ──► ACTIVE · event          │
   │                            │  schedule rows                 │
   │   daily scheduler (cron/Quartz):                           │
   │   find ACTIVE due today → validate vendor/slot/zone/       │
   │   capacity → INSERT daily order IF NOT EXISTS              │
   │   (unique subscription_id+date+slot; key sub:{id}:{d}:{s})  │
   │                            │── "today's meal" push ────────►│ Vendor prepares
   │                            │── dispatch ──────────────────►│ Rider delivers
   ◄─ meal calendar + tracking ─┤  DailyMealOrderGenerated       │
   ├─ POST /subscriptions/{id}/pause|skip|resume|cancel ──►     │
   │   stops FUTURE generation; existing daily orders untouched │
```

## 3. Subscription state machine

```
DRAFT → PAYMENT_PENDING → ACTIVE ⇄ PAUSED
  ACTIVE → SKIPPED (single date) → ACTIVE
  ACTIVE → EXPIRED (end date) · ACTIVE → CANCELLED (policy refund)
  PAYMENT_FAILED → (retry pay) → ACTIVE
```

Pause/skip/cancel never mutate already-generated daily orders (cancel them only
via the order-cancel policy path).

## 4. Idempotency & failure

- Tick guarded by DB unique + Redis tick lock; double scheduler run → one meal
  (`409 MEAL_ALREADY_GENERATED` on manual re-trigger).
- Failed subscription payment → `SubscriptionPaymentFailed` event, customer
  notified, generation suspended until healed (no silent meal debt).
- Missed tick → ops alert + catch-up runbook (generate-or-skip explicitly, never
  silently backfill deliveries).
