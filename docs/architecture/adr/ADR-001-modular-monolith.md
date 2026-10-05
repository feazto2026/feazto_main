# ADR-001 — Modular Monolith (not Microservices, not Big Ball of Mud)

## Context

Four clients (customer, vendor, rider, admin) share one business graph
(person → region → home-cook → slot → order → rider). Team is small, domain is
transactional (orders, payments, capacity), traffic is pre-scale. We need
microservice-quality boundaries without microservice operations cost.

## Decision

Build **one Spring Boot deployable** with strict logical modules
(`identity, customer, marketplace, commerce, subscription, fulfillment,
finance, operations, notification, shared/*`), each shaped
`api/application/domain/infrastructure`. Cross-module writes go through owning
application services or domain events. One API contract (`/api/v1`), one migration
chain, one deployment.

## Alternatives

- **Microservices per domain**: rejected — distributed transactions for
  order+payment+capacity, no ops capacity, premature.
- **Single layered monolith** (`controller/service/repository` global):
  rejected — no ownership, invites god-services.
- **Serverless-per-endpoint**: rejected — state machines and transactions suffer.

## Consequences

- (+) One deploy, ACID transactions, simple debugging/local dev, AI-friendly.
- (+) Extraction path preserved: module seams + events allow carving out
  dispatch/notifications/search later on metrics.
- (−) Must enforce boundaries by review (ArchUnit-style tests recommended);
  compiler won't stop a stray import.
- (−) Single runtime scaling; if one module saturates, scale whole unit until Phase 3.
