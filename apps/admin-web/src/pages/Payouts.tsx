import { useState } from 'react';
import { DataTable, Pager } from '../components/DataTable';
import { StatusBadge } from '../components/StatusBadge';
import { RequireScope } from '../auth/guards';
import { listPayouts, payoutAction } from '../api/admin';
import type { Payout } from '../api/admin';
import { useServerList } from '../hooks/useServerList';
import { inr } from '../hooks/utils';
import { ApiError } from '../api/client';

const STATUSES = ['', 'PENDING', 'APPROVED', 'RELEASED', 'HOLD'];
const KINDS = ['', 'VENDOR', 'RIDER'];

export function PayoutsPage() {
  const [status, setStatus] = useState('');
  const [kind, setKind] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  const list = useServerList<Payout, { status: string; kind: string }>({
    filters: { status, kind },
    fetcher: ({ page, pageSize, search, status: st, kind: k, signal }) =>
      listPayouts({ page, pageSize, search, status: st || undefined, kind: k || undefined, signal })
  });

  async function act(id: string, action: 'approve' | 'release' | 'hold') {
    const note = window.prompt(`Payout ${action} note (audited):`) ?? '';
    try {
      await payoutAction(id, action, note || undefined);
      setNotice(`Payout ${id} → ${action}.`);
      void list.reload();
    } catch (e) {
      setNotice(e instanceof ApiError ? `${e.message} (${e.code})` : 'Action failed');
    }
  }

  return (
    <div>
      <h2 style={{ margin: '0 0 4px' }}>Payouts</h2>
      <p className="muted">Vendor / rider earnings. Approve → release is gated by PAYOUT_MANAGE and fully audited.</p>
      {notice ? <div className="alert info" role="status">{notice}</div> : null}
      <div className="toolbar">
        <input type="search" aria-label="Search payouts" placeholder="Search beneficiary…" value={list.search} onChange={(e) => list.setSearch(e.target.value)} />
        <select aria-label="Filter by status" value={status} onChange={(e) => { setStatus(e.target.value); list.setPage(0); }}>
          {STATUSES.map((s) => (
            <option key={s} value={s}>{s || 'All statuses'}</option>
          ))}
        </select>
        <select aria-label="Filter by kind" value={kind} onChange={(e) => { setKind(e.target.value); list.setPage(0); }}>
          {KINDS.map((s) => (
            <option key={s} value={s}>{s || 'Vendors + riders'}</option>
          ))}
        </select>
      </div>
      <DataTable<Payout>
        columns={[
          { key: 'beneficiary', header: 'Beneficiary' },
          { key: 'kind', header: 'Kind' },
          { key: 'amount', header: 'Amount', render: (r) => inr(r.amount) },
          { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
          {
            key: 'actions', header: 'Actions', render: (r) => (
              <RequireScope scopes={['PAYOUT_MANAGE']} fallback={<span className="muted">—</span>}>
                <span style={{ display: 'inline-flex', gap: 6 }}>
                  <button className="btn sm" onClick={() => void act(r.id, 'approve')}>Approve</button>
                  <button className="btn sm primary" onClick={() => void act(r.id, 'release')}>Release</button>
                  <button className="btn sm" onClick={() => void act(r.id, 'hold')}>Hold</button>
                </span>
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
