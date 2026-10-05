import { useState } from 'react';
import { DataTable, Pager } from '../components/DataTable';
import { StatusBadge } from '../components/StatusBadge';
import { listCustomers } from '../api/admin';
import type { Customer } from '../api/admin';
import { useServerList } from '../hooks/useServerList';

const STATUSES = ['', 'ACTIVE', 'BLOCKED'];

export function CustomersPage() {
  const [status, setStatus] = useState('');
  const list = useServerList<Customer, { status: string }>({
    filters: { status },
    fetcher: ({ page, pageSize, search, status: st, signal }) =>
      listCustomers({ page, pageSize, search, status: st || undefined, signal })
  });

  return (
    <div>
      <h2 style={{ margin: '0 0 4px' }}>Customers</h2>
      <p className="muted">Read-only directory. Mutations belong to the customer domain; admin edits are audited.</p>
      <div className="toolbar">
        <input type="search" aria-label="Search customers" placeholder="Search name / phone…" value={list.search} onChange={(e) => list.setSearch(e.target.value)} />
        <select aria-label="Filter by status" value={status} onChange={(e) => { setStatus(e.target.value); list.setPage(0); }}>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s || 'All statuses'}
            </option>
          ))}
        </select>
      </div>
      <DataTable<Customer>
        columns={[
          { key: 'name', header: 'Name' },
          { key: 'phone', header: 'Phone' },
          { key: 'city', header: 'City', render: (r) => r.city ?? '—' },
          { key: 'ordersCount', header: 'Orders', render: (r) => String(r.ordersCount) },
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
