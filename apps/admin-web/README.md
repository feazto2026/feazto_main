# Feazto Admin Web — Operations Control Plane

React + Vite + TypeScript client of the Spring Boot modular monolith.
It performs **no direct business-table writes** — every privileged action
goes through `/api/v1/admin/*` with JWT + permission scopes, and is audit-logged
server-side with a reason where required.

## Prerequisites

- Node.js 18+
- Backend running (default `http://localhost:8080`)

## Startup

```bash
cd apps/admin-web
cp .env.example .env.local   # set VITE_API_BASE_URL (+ Supabase anon vars)
npm install
npm run dev                  # http://localhost:5174
```

| Script | Purpose |
|---|---|
| `npm run dev` | Vite dev server (proxies `/api` → `VITE_API_BASE_URL`) |
| `npm run build` | Typecheck + production build |
| `npm run preview` | Serve production build |

## Environment

Only `VITE_*` vars are bundled. See `.env.example`:

- `VITE_API_BASE_URL` — Spring Boot origin (no trailing slash)
- `VITE_SUPABASE_URL` / `VITE_SUPABASE_ANON_KEY` — client-safe anon usage only
  (session helpers / storage previews). No privileged DB access from the browser.

## Structure

```
src/
  main.tsx  App.tsx (Router)
  api/client.ts      # envelope handling, JWT, Idempotency-Key
  api/admin.ts       # typed /api/v1/admin/* domain functions
  auth/AuthContext.tsx guards.tsx   # JWT session + scope gates (UX only)
  components/Layout.tsx DataTable.tsx StatusBadge.tsx
  hooks/useServerList.ts utils.ts    # server pagination + debounce
  pages/ Login Dashboard Customers Vendors VendorVerification Orders
         OrderDetail Subscriptions Deliveries ServiceZones Payments
         Payouts Support AuditLogs Settings
```

## Conventions

- **Envelopes**: success `{ success:true, data, message?, requestId? }`,
  error `{ success:false, error:{ code, message, details? }, requestId? }`.
  `ApiError` carries `code/status/details/requestId`.
- **Idempotency**: `apiFetch` auto-sends `Idempotency-Key` (UUID) on every
  POST/PUT/PATCH/DELETE; callers may pass `idempotencyKey` explicitly for
  retries (cancel, refund, payout release).
- **Lists**: every list page uses `useServerList` — `page/pageSize/search`
  query params, 400 ms debounced search, `<Pager>` controls.
- **Permissions**: `RequireScope` hides actions without
  `VENDOR_* / ORDER_* / RIDER_* / FINANCE_* / PAYOUT_MANAGE / SUPPORT_* / AUDIT_VIEW / SETTINGS_MANAGE`.
  Roles: `SUPER_ADMIN / OPS_ADMIN / FINANCE_ADMIN / SUPPORT_ADMIN` (see `ROLE_SCOPES`).
  Backend re-enforces on every call.
- **Dashboard**: single `GET /api/v1/admin/dashboard/summary?date=YYYY-MM-DD`
  → orders, subscriptions, vendors, pending approvals, riders, deliveries,
  GOV, commissions, payouts, support.
- **Accessibility/responsive**: skip link, focus-visible rings, `role=status`
  live regions, labelled filters; sidebar becomes a drawer < 900 px; KPI grid
  collapses 4 → 2 → 1 columns.
