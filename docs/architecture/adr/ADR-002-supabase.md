# ADR-002 — Supabase as PostgreSQL / Auth / Storage

## Context

Need managed Postgres, phone-OTP auth, and object storage for menu/KYC images
without running our own infra. Clients are mobile + web.

## Decision

Use **Supabase** for: PostgreSQL (system of record), **Supabase Auth**
(phone OTP + JWT issuance), and **Storage** (menu/vendor/rider/customer images,
KYC docs in private buckets via signed URLs / backend-mediated access).
Schema managed by versioned migrations (`supabase/migrations`, Flyway-compatible).
Backend validates Supabase JWTs and maps `auth_user_id → platform_user`.

## Alternatives

- **Self-hosted Postgres + custom OTP**: rejected — OTP delivery, token security, storage undifferentiated work.
- **Firebase Auth + separate DB**: rejected — two identity sources, mapping drift.
- **Auth0/Cognito**: rejected — cost/complexity with no food-domain benefit.

## Consequences

- (+) No password handling; fast local dev; realtime/storage primitives available.
- (+) A valid Supabase user ≠ approved vendor/rider — backend keeps
  `account_status/verification_status` authoritatively.
- (−) Must enforce **direct-DB ban** (ADR-010) + RLS deny-by-default, else
  clients bypass business rules.
- (−) Provider coupling in auth/storage; mitigated by backend seams
  (`FileStorageProvider`, JWT validator abstraction).
