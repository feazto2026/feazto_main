# Order Workflow (normal / one-time order)

## 1. ASCII flow

```
Customer                    Spring Boot                    Vendor/Rider
   │                            │                               │
   ├─ OTP login → profile/address                                │
   ├─ discover (region/cuisine) → vendor/menu                   │
   ├─ POST /cart/items ──────► validate vendor+slot+capacity    │
   ├─ POST /orders (Idem-Key) ► price recompute · capacity hold │
   │                            │  create Order(CREATED→PAYMENT_PENDING)
   ├─ pay via provider ──────► intent → webhook+verify ──► Payment(SUCCESS)
   │                            │  Order→PLACED · event OrderCreated
   │                            │── push "new order" ──────────►│ Vendor
   │                            │◄─ POST /vendor/orders/{id}/accept
   │                             Order→VENDOR_ACCEPTED→PREPARING │
   │                            │◄─ POST .../ready               │
   │                             Order→READY_FOR_PICKUP · dispatch│
   │                            │── offer (TTL) ───────────────►│ Rider
   │                            │◄─ accept · QR pickup verify   │
   │                             PICKED_UP→OUT_FOR_DELIVERY      │
   │                            │◄─ PoD verify (OTP/QR/button)   │
   │                             DELIVERED→COMPLETED · finance   │
   ◄─ timeline + review prompt ─┤  events → notify/analytics     │
```

## 2. Order state machine (server-enforced)

```
CREATED → PAYMENT_PENDING → PLACED → VENDOR_ACCEPTED → PREPARING
  → READY_FOR_PICKUP → RIDER_ASSIGNED → PICKED_UP → OUT_FOR_DELIVERY
  → DELIVERED → COMPLETED
              ↘ CANCEL_REQUESTED → CANCELLED → REFUND_PENDING → REFUNDED
PAYMENT_FAILED / FAILED terminal (retry = new intent + new key)
```

- Transitions only via explicit endpoints
  (`/accept /start-preparing /ready /cancel /complete`), each appending
  `order_status_history`. `COMPLETED→anything`, `CANCELLED→COMPLETED` rejected
  with `STATE_CONFLICT`.
- Cancellation/refund eligibility + amount computed server-side per stage
  (pre-accept / preparing / ready / post-pickup / post-delivery policy).
- Capacity: `lock:slot:{slot}:{date)` → DB capacity check → hold; oversell
  returns `409 ORDER_SLOT_FULL`.

## 3. Key endpoints

`POST /cart/items · POST /orders · POST /payments · POST /payments/webhooks/{provider} ·
POST /vendor/orders/{id}/accept|start-preparing|ready · POST /orders/{id}/cancel ·
POST /reviews` — see `packages/api-contracts/openapi.yaml`.
