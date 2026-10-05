# Idempotency

## 1. Rule

Every retriable mutation is safe to retry: same `Idempotency-Key` + same
payload → same logical result, exactly-once effect. Keys are per-operation
(`POST /orders` key ≠ `POST /payments` key).

## 2. Required coverage

```
POST /orders · POST /payments · payment webhooks (provider event id) ·
POST /payments/{id}/refund · POST /subscriptions (+ daily tick key
sub:{subId}:{date}:{slot}) · POST /deliveries/{id}/pickup ·
POST /deliveries/{id}/complete · POST /orders/{id}/cancel
```

## 3. Protocol

- Client generates UUIDv4 per *intent* (new order = new key; retry = same key)
  and sends `Idempotency-Key: <uuid>`. Missing key on required endpoints →
  `400 VALIDATION_ERROR`.
- Server stores `idempotency:{op}:{key} → {status, responseHash, entityId}`
  (Redis, TTL 24–72h) **plus** DB unique guards:
  `payments(provider, provider_event_id)`, `orders(idempotency_key)`,
  `daily_orders(subscription_id, service_date, meal_slot)`.
- Flow: `lookup → IN_PROGRESS (return 409 DUPLICATE_REQUEST or await) →
  execute once in TX → store result → replay identical result on duplicates`.
- Payload mismatch on same key → `422` (don't silently swap intents).
- Double-tap, timeout retry, webhook redelivery, scheduler double-fire are the
  test cases — all must yield one charge / one order / one daily meal.

## 4. Storage & TTLs

| Record | Store | TTL |
|--------|-------|-----|
| Key → response | Redis `idempotency:*` | 24h (webhooks/refunds 72h) |
| In-flight lock | Redis `lock:order:*`, `lock:slot:{slot}:{date}` | seconds–minutes |
| Durable guard | Postgres unique constraints (above) | permanent |
| Outbox relay | Postgres `outbox_event` + publisher | until SENT |

## 5. Client guidance

- Generate the key when the user confirms (checkout/pay/accept/pickup/deliver),
  persist until success screen; reuse across app restarts for that intent.
- On `503/timeout/network error`: retry **same key** with backoff; on
  `409 DUPLICATE_REQUEST`: fetch the referenced entity instead of re-posting.
- Never reuse a key for a different cart, amount, slot, or delivery.
