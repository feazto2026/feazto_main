# ADR-009 — API Versioning

## Context

Four clients ship independently (store review lag). Breaking changes must not
brick installed apps.

## Decision

Prefix all routes `/api/v1`. Additive changes stay in-version (new optional
fields/endpoints). Breaking changes → `/api/v2` (or controlled migration with
deprecation headers + sunset window + client-min-version enforcement).
Every mutation documents auth, roles, schemas, validation, business errors,
idempotency. Machine contract lives at `packages/api-contracts/openapi.yaml`;
clients generate/align types from it.

## Alternatives

- **Unversioned routes**: rejected — any rename breaks field apps.
- **Header-only versioning**: rejected — harder to observe/route/cache; mobile
  intermediaries strip headers.
- **Per-client endpoints**: rejected — multiplies surface, kills "one contract".

## Consequences

- (+) Safe independent client releases; explicit deprecation path.
- (+) OpenAPI diff in CI can catch accidental breaks.
- (−) Old versions must be operated during sunset; version-removal checklist required.
