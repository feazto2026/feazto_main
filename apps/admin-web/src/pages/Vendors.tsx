import { useState } from 'react';
import { Link } from 'react-router-dom';
import { DataTable, Pager } from '../components/DataTable';
import { StatusBadge } from '../components/StatusBadge';
import { RequireScope } from '../auth/guards';
import { listVendors, suspendVendor } from '../api/admin';
import type { Vendor } from '../api/admin';
import { useServerList } from '../hooks/useServerList';
import { ApiError } from '../api/client';

const STATUSES = ['', 'PENDING', 'APPROVED', 'SUSPENDED', 'REJECTED'];

export function VendorsPage() {
  const [status, setStatus] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  const list = useServerList<Vendor, { status: string }>({
    filters: { status },
    fetcher: ({ page, pageSize, search, status: st, signal }) =>
      listVendors({ page, pageSize, search, status: st || undefined, signal })
  });

  async function onSuspend(id: string) {
    const reason = window.prompt('Suspension reason (required, recorded in audit log):');
    if (!reason) return;
    try {
      await suspendVendor(id, reason);
      setNotice(`Vendor ${id} suspended.`);
      void list.reload();
    } catch (e) {
      setNotice(e instanceof ApiError ? `${e.message} (${e.code})` : 'Suspend failed');
    }
  }

  return (
    <div>
      <h2 style={{ margin: '0 0 4px' }}>Vendors</h2>
      <p className="muted">
        Marketplace directory. Approvals happen in <Link to="/verifications">Vendor Verification</Link>.
      </p>
      {notice ? <div className="alert info" role="status">{notice}</div> : null}
      <div className="toolbar">
        <input type="search" aria-label="Search vendors" placeholder="Search name / phone…" value={list.search} onChange={(e) => list.setSearch(e.target.value)} />
        <select aria-label="Filter by status" value={status} onChange={(e) => { setStatus(e.target.value); list.setPage(0); }}>
          {STATUSES.map((s) => (
            <option key={s} value={s}>{s || 'All statuses'}</option>
          ))}
        </select>
      </div>
      <DataTable<Vendor>
        columns={[
          { key: 'name', header: 'Vendor' },
          { key: 'city', header: 'City', render: (r) => r.city ?? '—' },
          { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
          {
            key: 'actions', header: 'Actions', render: (r) => (
              <RequireScope scopes={['VENDOR_SUSPEND']} fallback={<span className="muted">—</span>}>
                <button className="btn sm danger" disabled={r.status === 'SUSPENDED'} onClick={() => void onSuspend(r.id)}>
                  Suspend
                </button>
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
