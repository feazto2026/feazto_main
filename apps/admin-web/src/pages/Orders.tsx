import { useState } from 'react';
import { Link } from 'react-router-dom';
import { DataTable, Pager } from '../components/DataTable';
import { StatusBadge } from '../components/StatusBadge';
import { RequireScope } from '../auth/guards';
import { cancelOrder, listOrders } from '../api/admin';
import type { Order } from '../api/admin';
import { useServerList } from '../hooks/useServerList';
import { inr } from '../hooks/utils';
import { ApiError } from '../api/client';

const STATUSES = ['', 'CREATED', 'PAYMENT_PENDING', 'PLACED', 'VENDOR_ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP', 'RIDER_ASSIGNED', 'PICKED_UP', 'OUT_FOR_DELIVERY', 'DELIVERED', 'COMPLETED', 'CANCELLED'];

export function OrdersPage() {
  const [status, setStatus] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  const list = useServerList<Order, { status: string }>({
    filters: { status },
    fetcher: ({ page, pageSize, search, status: st, signal }) =>
      listOrders({ page, pageSize, search, status: st || undefined, signal })
  });

  async function onCancel(id: string) {
    const reason = window.prompt('Cancellation reason (required, audited):');
    if (!reason) return;
    try {
      await cancelOrder(id, reason);
      setNotice(`Order ${id} cancelled.`);
      void list.reload();
    } catch (e) {
      setNotice(e instanceof ApiError ? `${e.message} (${e.code})` : 'Cancel failed');
    }
  }

  return (
    <div>
      <h2 style={{ margin: '0 0 4px' }}>Orders</h2>
      <p className="muted">State-machine transitions only (cancel / refund actions). Never free-form status writes.</p>
      {notice ? <div className="alert info" role="status">{notice}</div> : null}
      <div className="toolbar">
        <input type="search" aria-label="Search orders" placeholder="Search id / customer / vendor…" value={list.search} onChange={(e) => list.setSearch(e.target.value)} />
        <select aria-label="Filter by status" value={status} onChange={(e) => { setStatus(e.target.value); list.setPage(0); }}>
          {STATUSES.map((s) => (
            <option key={s} value={s}>{s || 'All statuses'}</option>
          ))}
        </select>
      </div>
      <DataTable<Order>
        columns={[
          { key: 'id', header: 'Order', render: (r) => <Link to={`/orders/${r.id}`}>{r.id.slice(0, 8)}…</Link> },
          { key: 'customerName', header: 'Customer' },
          { key: 'vendorName', header: 'Vendor' },
          { key: 'total', header: 'Total', render: (r) => inr(r.total) },
          { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
          {
            key: 'actions', header: 'Actions', render: (r) => (
              <RequireScope scopes={['ORDER_CANCEL']} fallback={<span className="muted">—</span>}>
                <button className="btn sm danger" onClick={() => void onCancel(r.id)}>Cancel</button>
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
