# ADR-005 — Payment Provider Abstraction

## Context

Launch needs UPI/Card/NetBanking/Wallet + COD. Provider (Razorpay/Stripe/…)
choice may change; domain must not lock to one SDK. Frontend "payment success"
callbacks are untrusted.

## Decision

Domain depends only on:

```java
interface PaymentProvider {
  PaymentIntent createPayment(CreatePaymentCommand cmd);
  PaymentVerification verifyPayment(String paymentReference);
  RefundResult refund(RefundCommand cmd);
}
```

Flow: client → backend intent → provider → **webhook + server-side verify** →
`Payment` state → `Order` state. Webhooks signature-checked and idempotent.
COD modelled as a payment method with `collect-on-delivery` settlement, same
state machine. Never trust client payment callbacks; never create duplicate
charges on retry (Idempotency-Key end to end).

## Alternatives

- **Direct SDK calls in services**: rejected — provider lock-in, untestable.
- **Client-confirmed payment**: rejected — trivially forgeable.
- **Separate gateway microservice now**: rejected — Phase-1 overkill; interface
  preserves the seam.

## Consequences

- (+) Swappable provider; unit-testable with fake; uniform refund path.
- (+) Single place for webhook verification + idempotency.
- (−) Adapter must be written per provider; feature lag on provider-specific
  methods (e.g. new wallet) until mapped.
