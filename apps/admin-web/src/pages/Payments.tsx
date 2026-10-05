import { useState } from 'react';
import { DataTable, Pager } from '../components/DataTable';
import { StatusBadge } from '../components/StatusBadge';
import { listPayments } from '../api/admin';
import type { Payment } from '../api/admin';
import { useServerList } from '../hooks/useServerList';
import { inr } from '../hooks/utils';

const STATUSES = ['', 'SUCCEEDED', 'PENDING', 'FAILED', 'REFUNDED'];

export function PaymentsPage() {
  const [status, setStatus] = useState('');
  const list = useServerList<Payment, { status: string }>({
    filters: { status },
    fetcher: ({ page, pageSize, search, status: st, signal }) =>
      listPayments({ page, pageSize, search, status: st || undefined, signal })
  });

  return (
    <div>
      <h2 style={{ margin: '0 0 4px' }}>Payments</h2>
      <p className="muted">Immutable transaction records. Success is confirmed server-side via provider webhooks; refunds are idempotent.</p>
      <div className="toolbar">
        <input type="search" aria-label="Search payments" placeholder="Search payment / order id…" value={list.search} onChange={(e) => list.setSearch(e.target.value)} />
        <select aria-label="Filter by status" value={status} onChange={(e) => { setStatus(e.target.value); list.setPage(0); }}>
          {STATUSES.map((s) => (
            <option key={s} value={s}>{s || 'All statuses'}</option>
          ))}
        </select>
      </div>
      <DataTable<Payment>
        columns={[
          { key: 'id', header: 'Payment' },
          { key: 'orderId', header: 'Order' },
          { key: 'amount', header: 'Amount', render: (r) => inr(r.amount) },
          { key: 'provider', header: 'Provider', render: (r) => r.provider ?? '—' },
          { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> }
        ]}
        rows={list.rows}
        loading={list.loading}
        error={list.error}
        getRowKey={(r) => r.id}
      />
      <Pager page={list.page} pageSize={list.pageSize} total={list.total} totalPages={list.totalPages} onPage={list.setPage} />
    </div>
  );
}
