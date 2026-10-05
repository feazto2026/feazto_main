# Stable Error Codes

Clients branch on `error.code`, never on message text. HTTP mapping in brackets.

## Auth / session (`401` unless noted)

`AUTH_REQUIRED [401]` · `AUTH_EXPIRED [401]` · `AUTH_INVALID [401]` ·
`OTP_REQUIRED` · `OTP_INVALID` · `OTP_EXPIRED [410]` · `OTP_ATTEMPTS_EXCEEDED [429]` ·
`ACCOUNT_SUSPENDED [403]` · `VERIFICATION_PENDING [403]` (`403` — approved-role check)

## Validation / generic (`400/404/409`)

`VALIDATION_ERROR [400]` · `NOT_FOUND [404]` · `PERMISSION_DENIED [403]` ·
`STATE_CONFLICT [409]` (illegal transition) · `DUPLICATE_REQUEST [409]` ·
`RATE_LIMITED [429]` · `SERVER_ERROR [500]`

## Discovery / serviceability (`422`)

`ADDRESS_NOT_SERVICEABLE` · `VENDOR_NOT_AVAILABLE` · `VENDOR_NOT_APPROVED` ·
`SLOT_INVALID` · `ORDER_SLOT_FULL [409]` · `CAPACITY_EXCEEDED [409]` ·
`CART_VENDOR_MISMATCH` · `CART_EMPTY` · `COUPON_INVALID` · `PRICE_MISMATCH`
(recompute; client total ignored)

## Orders / fulfilment (`422`, conflicts `409`)

`ORDER_NOT_FOUND [404]` · `ORDER_NOT_CANCELLABLE` · `ORDER_ALREADY_COMPLETED` ·
`PICKUP_QR_INVALID` · `PICKUP_QR_EXPIRED [410]` · `DELIVERY_PROOF_INVALID` ·
`RIDER_NOT_AVAILABLE` · `ASSIGNMENT_EXPIRED [410]` · `DISPATCH_ESCALATED`
(ops queue) · `REVIEW_NOT_ELIGIBLE` · `REVIEW_DUPLICATE [409]`

## Payments / refunds (`402/422`)

`PAYMENT_REQUIRED` · `PAYMENT_FAILED [402]` · `PAYMENT_VERIFICATION_FAILED [402]` ·
`PAYMENT_ALREADY_CONFIRMED [409]` · `WEBHOOK_SIGNATURE_INVALID [401]` ·
`REFUND_NOT_ELIGIBLE` · `REFUND_ALREADY_PROCESSED [409]` ·
`SUBSCRIPTION_PAYMENT_FAILED`

## Subscriptions (`422`)

`SUBSCRIPTION_NOT_ACTIVE` · `SUBSCRIPTION_PAUSED` · `MEAL_ALREADY_GENERATED [409]` ·
`MEAL_SLOT_CLOSED` · `VENDOR_UNAVAILABLE_FOR_SLOT`

Example:

```json
{
  "success": false,
  "error": { "code": "ORDER_SLOT_FULL", "message": "The selected meal slot is full.", "details": { "slotId": "lunch", "date": "2026-10-03" } },
  "requestId": "req_9f2…"
}
```

Rules: codes are UPPER_SNAKE, immutable once shipped (deprecate, never rename);
`details` carries structured context (ids, slot, reason); secrets/PII never in errors.
