# ADR-008 — Delivery Assignment (Dispatch) Strategy

## Context

`READY_FOR_PICKUP` orders need a rider quickly and fairly; strategy will evolve
(zone → distance/ETA → batching → ML), but Order must not be rewritten each time.

## Decision

Hide dispatch behind `DeliveryAssignmentService`: eligible riders =
`service-zone ∩ online ∩ capacity ∩ capability`; rank by
`distance + current workload (+ rating tiebreak)`; offer with TTL
(`dispatch:offer:{deliveryId}`, Redis); accept/reject/timeout → retry next
candidate → escalate to ops queue after N misses. QR pickup + configurable PoD
(OTP/QR/button) validated server-side. Strategy is swappable config/code, Order
untouched.

## Alternatives

- **Broadcast to all riders**: rejected — spam, stampede, unfair.
- **Manual admin assignment only**: rejected — doesn't scale past pilot.
- **External dispatch engine now**: rejected — cost/complexity before volume.

## Consequences

- (+) Replaceable ranking without Order changes; fair, auditable offers.
- (+) Timeout/escalation path keeps food from stranding.
- (−) Needs rider location cadence + battery discipline; location staleness
  degrades ranking — monitor `assignment_rate` and `time-to-accept`.
