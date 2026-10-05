# Dispatch & Delivery Workflow

## 1. ASCII flow

```
Order READY_FOR_PICKUP
   │
   ▼
DeliveryAssignmentService: eligible = zone ∩ online ∩ capacity ∩ capability
   │  rank: distance + workload (+ rating tiebreak)
   ▼
offer → Rider (Redis dispatch:offer:{deliveryId}, TTL ~60-90s)
   ├── ACCEPT ──► Delivery ASSIGNED → ARRIVED_AT_VENDOR
   ├── REJECT/TIMEOUT ──► next candidate (bounded retries)
   └── EXHAUSTED ──► ops escalation queue (DISPATCH_ESCALATED)
   │
   ▼
QR pickup: vendor shows order-specific QR (nonce+expiry)
   rider scans → POST /deliveries/{id}/pickup {qrNonce}
   server checks: assignment == caller · order == READY/RIDER_ASSIGNED ·
                  QR valid+unexpired+unused → PICKED_UP (QR single-use)
   │
   ▼
EN_ROUTE → ARRIVED_AT_CUSTOMER → PoD verify (OTP | QR | confirm button;
   photo/signature only if ops-required) → DELIVERED → order COMPLETED
```

## 2. Delivery state machine

```
AVAILABLE → ASSIGNMENT_PENDING → ASSIGNED → ACCEPTED → ARRIVED_AT_VENDOR
  → PICKUP_VERIFICATION_PENDING → PICKED_UP → EN_ROUTE
  → ARRIVED_AT_CUSTOMER → DELIVERY_VERIFICATION_PENDING → DELIVERED
  ↘ CANCELLED / FAILED (re-queue or ops per policy)
```

Order mirrors: `READY_FOR_PICKUP → RIDER_ASSIGNED → PICKED_UP →
OUT_FOR_DELIVERY → DELIVERED → COMPLETED`. Both histories appended.

## 3. Rules

- Strategy swappable behind `DeliveryAssignmentService`; Order untouched.
- Never trust client order-ID for pickup — QR nonce + assignment + state.
- Idempotent pickup/complete (`Idempotency-Key`); rider crash mid-accept keeps
  persisted assignment (re-offer, don't duplicate delivery).
- Location shared minimally: rider sees vendor + customer drop only for active
  assignment; customer sees rider proximity only for own order.
- Metrics: `assignment_rate`, `time-to-accept`, `escalation_rate` (see overview §7).
