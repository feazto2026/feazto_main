import { useState } from 'react';
import { DataTable, Pager } from '../components/DataTable';
import { StatusBadge } from '../components/StatusBadge';
import { RequireScope } from '../auth/guards';
import { listDeliveries, reassignDelivery } from '../api/admin';
import type { Delivery } from '../api/admin';
import { useServerList } from '../hooks/useServerList';
import { ApiError } from '../api/client';

const STATUSES = ['', 'PENDING', 'RIDER_ASSIGNED', 'PICKED_UP', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED'];

export function DeliveriesPage() {
  const [status, setStatus] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  const list = useServerList<Delivery, { status: string }>({
    filters: { status },
    fetcher: ({ page, pageSize, search, status: st, signal }) =>
      listDeliveries({ page, pageSize, search, status: st || undefined, signal })
  });

  async function onReassign(id: string) {
    const riderId = window.prompt('Replacement rider ID:');
    if (!riderId) return;
    try {
      await reassignDelivery(id, riderId);
      setNotice(`Delivery ${id} reassigned.`);
      void list.reload();
    } catch (e) {
      setNotice(e instanceof ApiError ? `${e.message} (${e.code})` : 'Reassign failed');
    }
  }

  return (
    <div>
      <h2 style={{ margin: '0 0 4px' }}>Deliveries</h2>
      <p className="muted">Fulfillment objects linked to orders. Dispatch strategy stays server-side and replaceable.</p>
      {notice ? <div className="alert info" role="status">{notice}</div> : null}
      <div className="toolbar">
        <input type="search" aria-label="Search deliveries" placeholder="Search order / rider…" value={list.search} onChange={(e) => list.setSearch(e.target.value)} />
        <select aria-label="Filter by status" value={status} onChange={(e) => { setStatus(e.target.value); list.setPage(0); }}>
          {STATUSES.map((s) => (
            <option key={s} value={s}>{s || 'All statuses'}</option>
          ))}
        </select>
      </div>
      <DataTable<Delivery>
        columns={[
          { key: 'orderId', header: 'Order' },
          { key: 'riderName', header: 'Rider', render: (r) => r.riderName ?? 'Unassigned' },
          { key: 'zone', header: 'Zone', render: (r) => r.zone ?? '—' },
          { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
          {
            key: 'actions', header: 'Actions', render: (r) => (
              <RequireScope scopes={['RIDER_VIEW', 'ORDER_VIEW']} fallback={<span className="muted">—</span>}>
                <button className="btn sm" onClick={() => void onReassign(r.id)}>Reassign</button>
              </RequireScope>
            )
          }
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
