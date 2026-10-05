# ADR-006 — Role / Permission Model

## Context

`ADMIN = everything` is unsafe: finance, vendor approval, rider suspension,
refunds need separation of duty + audit.

## Decision

Two-level model: **roles** (`CUSTOMER, VENDOR, RIDER, ADMIN, SUPER_ADMIN,
OPS_ADMIN, FINANCE_ADMIN, SUPPORT_ADMIN`) map to fine-grained **permission
scopes** enforced server-side on every endpoint:

```
VENDOR_VIEW/APPROVE/SUSPEND · ORDER_VIEW/CANCEL/REFUND · RIDER_VIEW/APPROVE/SUSPEND
FINANCE_VIEW · PAYOUT_MANAGE · SUPPORT_VIEW/ASSIGN · AUDIT_VIEW · SETTINGS_MANAGE
```

Checks = `authentication + role + permission + resource ownership + resource
state`. Frontend hiding is UX only. Sensitive admin writes require `reason`
and emit `audit_logs` rows.

## Alternatives

- **Single ADMIN flag**: rejected — no least-privilege, unauditable blast radius.
- **Ownership-only (no scopes)**: rejected — can't separate finance vs support duties.
- **Per-row ACL engine**: rejected — overkill; scope+ownership covers marketplace needs.

## Consequences

- (+) Least privilege; auditable; reversible where possible.
- (+) New ops roles added by mapping, not code changes.
- (−) Permission matrix must be maintained + tested; missing scope = silent
  denial — needs clear `PERMISSION_DENIED` errors and admin UX.
