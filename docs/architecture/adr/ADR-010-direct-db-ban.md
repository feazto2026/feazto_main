# ADR-010 — Direct DB Access Ban (Clients Must Not Write Business Tables)

## Context

Supabase exposes PostgREST; it is tempting to let apps write `orders` directly.
That would bypass pricing, capacity, state machines, idempotency, audit.

## Decision

**Ban**: no client (mobile/web) performs business mutations via direct DB
access. All business reads/writes go through Spring Boot. Narrow, explicit
exceptions only: Supabase Auth session handling (sign-in/OTP/refresh) and
backend-issued storage uploads (signed URLs for menu/KYC images). RLS is
deny-by-default and enforces the ban even if a key leaks. See
`docs/database/rls.md`.

## Alternatives

- **RLS-only, no backend**: rejected — pricing/capacity/dispatch/audit can't
  live in policies; payment secrets would leak.
- **Read-through PostgREST for catalog**: rejected for Phase 1 — one rule
  ("API for data") is simpler to audit; revisit read-only catalog via RLS only
  with explicit ADR amendment + load justification.
- **service_role in clients**: never — key compromise = full DB.

## Consequences

- (+) Single enforcement point; testable authz; secrets stay server-side.
- (+) RLS as second layer: leaked anon key still can't mutate business facts.
- (−) Backend must implement even simple reads initially (more endpoints);
  accepted as correctness cost.
