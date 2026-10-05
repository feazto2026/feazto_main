# Payment Workflow

## 1. Methods & lifecycle

Methods: `ONLINE (UPI/CARD/NET_BANKING/WALLET)` + `COD`. Lifecycle:

```
CREATED → INITIATED → PENDING → SUCCESS | FAILED | CANCELLED
  SUCCESS → REFUND_PENDING → PARTIALLY_REFUNDED | REFUNDED
```

## 2. ASCII flow (online)

```
Customer          Spring Boot              Provider            Vendor/Rider
   │                  │                        │                     │
   ├─ POST /payments ─► create intent (PaymentProvider adapter)      │
   │  (Idem-Key)      │── createPayment ──► order w/ provider       │
   ◄─ client params ──┤                                             │
   ├─ pays at provider (UPI/card/…)                                 │
   │                  │◄── webhook {event id, signature} ──┤         │
   │                  │  verify signature → verifyPayment()         │
   │                  │  idempotent apply (provider event id unique)│
   │                  │  Payment→SUCCESS → Order→PLACED             │
   │                  ├── receipt push ──► Customer                  │
   │                  ├── "new order" ────────────────────────────►│ Vendor
   └── NEVER trust client "success" callback ── always re-verify ──┘
```

COD: `Payment(PENDING, method=COD)` → collected at handoff → rider confirms
amount → `SUCCESS`; same refund/cancel policy path on failure.

## 3. Rules

- Server verifies **every** success (webhook signature + `verifyPayment`);
  client callbacks are UX hints only.
- Webhook handler idempotent on provider event id; replay → same result.
- Double-tap checkout → one intent (idempotency key); timeout → retry same key.
- Refunds: `POST /payments/{id}/refund` (permission-checked, reason required) →
  provider refund → `Refund` ledger row → customer notified; amounts from
  server snapshots, never client input.
- Secrets (`PAYMENT_SECRET`, `PAYMENT_WEBHOOK_SECRET`) server-only; no SDK
  keys in mobile/web bundles.
