import { useState } from 'react';
import { DataTable, Pager } from '../components/DataTable';
import { StatusBadge } from '../components/StatusBadge';
import { RequireScope } from '../auth/guards';
import { approveVendor, listVendorVerifications, rejectVendor } from '../api/admin';
import type { Vendor } from '../api/admin';
import { useServerList } from '../hooks/useServerList';
import { ApiError } from '../api/client';

export function VendorVerificationPage() {
  const [notice, setNotice] = useState<string | null>(null);
  const list = useServerList<Vendor, Record<string, never>>({
    fetcher: ({ page, pageSize, search, signal }) => listVendorVerifications({ page, pageSize, search, signal })
  });

  async function act(id: string, kind: 'approve' | 'reject') {
    const note = window.prompt(kind === 'approve' ? 'Approval note (optional):' : 'Rejection reason (required, audited):') ?? '';
    if (kind === 'reject' && !note) return;
    try {
      if (kind === 'approve') await approveVendor(id, note || undefined);
      else await rejectVendor(id, note);
      setNotice(`Vendor ${id} ${kind === 'approve' ? 'approved' : 'rejected'}.`);
      void list.reload();
    } catch (e) {
      setNotice(e instanceof ApiError ? `${e.message} (${e.code})` : 'Action failed');
    }
  }

  return (
    <div>
      <h2 style={{ margin: '0 0 4px' }}>Vendor verification</h2>
      <p className="muted">PENDING queue. Approve / reject / suspend are permission-gated (VENDOR_APPROVE) and audit-logged with a reason.</p>
      {notice ? <div className="alert info" role="status">{notice}</div> : null}
      <div className="toolbar">
        <input type="search" aria-label="Search pending vendors" placeholder="Search name / phone…" value={list.search} onChange={(e) => list.setSearch(e.target.value)} />
      </div>
      <DataTable<Vendor>
        columns={[
          { key: 'name', header: 'Vendor' },
          { key: 'phone', header: 'Phone' },
          { key: 'city', header: 'City', render: (r) => r.city ?? '—' },
          { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
          {
            key: 'actions', header: 'Decision', render: (r) => (
              <RequireScope scopes={['VENDOR_APPROVE']} fallback={<span className="muted">No permission</span>}>
                <span style={{ display: 'inline-flex', gap: 8 }}>
                  <button className="btn sm primary" onClick={() => void act(r.id, 'approve')}>Approve</button>
                  <button className="btn sm danger" onClick={() => void act(r.id, 'reject')}>Reject</button>
                </span>
              </RequireScope>
            )
          }
        ]}
        rows={list.rows}
        loading={list.loading}
        error={list.error}
        emptyText="Verification queue is clear."
        getRowKey={(r) => r.id}
      />
      <Pager page={list.page} pageSize={list.pageSize} total={list.total} totalPages={list.totalPages} onPage={list.setPage} />
    </div>
  );
}
