import { useState } from 'react';
import { DataTable, Pager } from '../components/DataTable';
import { StatusBadge } from '../components/StatusBadge';
import { listSubscriptions } from '../api/admin';
import type { Subscription } from '../api/admin';
import { useServerList } from '../hooks/useServerList';

const STATUSES = ['', 'ACTIVE', 'PAUSED', 'CANCELLED', 'COMPLETED'];

export function SubscriptionsPage() {
  const [status, setStatus] = useState('');
  const list = useServerList<Subscription, { status: string }>({
    filters: { status },
    fetcher: ({ page, pageSize, search, status: st, signal }) =>
      listSubscriptions({ page, pageSize, search, status: st || undefined, signal })
  });

  return (
    <div>
      <h2 style={{ margin: '0 0 4px' }}>Subscriptions</h2>
      <p className="muted">Recurring contracts. Pausing never mutates already-generated daily orders.</p>
      <div className="toolbar">
        <input type="search" aria-label="Search subscriptions" placeholder="Search customer / plan…" value={list.search} onChange={(e) => list.setSearch(e.target.value)} />
        <select aria-label="Filter by status" value={status} onChange={(e) => { setStatus(e.target.value); list.setPage(0); }}>
          {STATUSES.map((s) => (
            <option key={s} value={s}>{s || 'All statuses'}</option>
          ))}
        </select>
      </div>
      <DataTable<Subscription>
        columns={[
          { key: 'planName', header: 'Plan' },
          { key: 'customerName', header: 'Customer' },
          { key: 'vendorName', header: 'Vendor' },
          { key: 'nextDeliveryDate', header: 'Next delivery', render: (r) => r.nextDeliveryDate ?? '—' },
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
