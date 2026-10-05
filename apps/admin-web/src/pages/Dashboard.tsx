import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchDashboard } from '../api/admin';
import type { DashboardSummary } from '../api/admin';
import { inr, todayLocal } from '../hooks/utils';

function Kpi({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="card">
      <h3>{label}</h3>
      <div className="big">{value}</div>
      {sub ? <div className="sub">{sub}</div> : null}
    </div>
  );
}

/**
 * Ops-today dashboard per platform spec: today's orders / subscriptions /
 * vendors / pending approvals / riders / deliveries / GOV / commissions /
 * payouts / support. Single ?date= query against /dashboard/summary.
 */
export function DashboardPage() {
  const [date, setDate] = useState(todayLocal());
  const [data, setData] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const ctrl = new AbortController();
    setLoading(true);
    setError(null);
    fetchDashboard(date, ctrl.signal)
      .then((d) => setData(d))
      .catch((e) => {
        if ((e as Error).name !== 'AbortError') setError(e instanceof Error ? e.message : 'Failed to load dashboard');
      })
      .finally(() => setLoading(false));
    return () => ctrl.abort();
  }, [date]);

  return (
    <div>
      <div className="toolbar" role="search">
        <h2 style={{ margin: '0 12px 0 0' }}>Today&apos;s operations</h2>
        <input type="date" aria-label="Business date" value={date} max={todayLocal()} onChange={(e) => setDate(e.target.value)} />
        {loading ? <span role="status">Loading…</span> : null}
      </div>

      {error ? (
        <div className="alert error" role="alert">
          {error}
        </div>
      ) : null}

      <div className="grid kpi" aria-live="polite">
        <Kpi label="Orders today" value={String(data?.ordersToday ?? '—')} sub={`${data?.deliveriesToday ?? 0} deliveries · ${data?.deliveriesPending ?? 0} pending`} />
        <Kpi label="GOV today" value={data ? inr(data.govToday) : '—'} sub={`Commission ${data ? inr(data.commissionToday) : '—'}`} />
        <Kpi label="Active subscriptions" value={String(data?.subscriptionsActive ?? '—')} sub="Recurring contracts (not daily orders)" />
        <Kpi label="Vendors" value={String(data?.vendorsTotal ?? '—')} sub={`${data?.vendorsPendingApproval ?? 0} pending approval`} />
        <Kpi label="Riders online" value={String(data?.ridersOnline ?? '—')} sub="Availability-gated dispatch pool" />
        <Kpi label="Payouts pending" value={String(data?.payoutsPending ?? '—')} sub={data ? inr(data.payoutsPendingAmount) : undefined} />
        <Kpi label="Support open" value={String(data?.supportOpen ?? '—')} sub={`${data?.supportUnassigned ?? 0} unassigned`} />
        <Kpi label="Payments today" value={String((data?.paymentsToday.succeeded ?? 0) + (data?.paymentsToday.failed ?? 0))} sub={`${data?.paymentsToday.succeeded ?? 0} ok · ${data?.paymentsToday.failed ?? 0} failed · ${data?.paymentsToday.refunded ?? 0} refunded`} />
      </div>

      <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit,minmax(280px,1fr))', marginTop: 14 }}>
        <div className="card">
          <h3>Orders by status</h3>
          {data?.ordersByStatus ? (
            <ul className="timeline">
              {Object.entries(data.ordersByStatus).map(([k, v]) => (
                <li key={k}>
                  <strong>{k}</strong> — {v}
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">No data.</p>
          )}
          <p>
            <Link to="/orders">Open orders →</Link>
          </p>
        </div>
        <div className="card">
          <h3>Needs attention</h3>
          <ul className="timeline">
            <li>
              <Link to="/verifications">{data?.vendorsPendingApproval ?? 0} vendor approvals pending</Link>
            </li>
            <li>
              <Link to="/payouts">{data?.payoutsPending ?? 0} payouts awaiting action</Link>
            </li>
            <li>
              <Link to="/support">{data?.supportUnassigned ?? 0} unassigned support tickets</Link>
            </li>
            <li>
              <Link to="/deliveries">{data?.deliveriesPending ?? 0} deliveries pending</Link>
            </li>
          </ul>
        </div>
      </div>
    </div>
  );
}
