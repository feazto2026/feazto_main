import { useState } from 'react';
import { DataTable, Pager } from '../components/DataTable';
import { StatusBadge } from '../components/StatusBadge';
import { RequireScope } from '../auth/guards';
import { assignTicket, listTickets, resolveTicket } from '../api/admin';
import type { Ticket } from '../api/admin';
import { useServerList } from '../hooks/useServerList';
import { ApiError } from '../api/client';

const STATUSES = ['', 'OPEN', 'IN_PROGRESS', 'RESOLVED'];

export function SupportPage() {
  const [status, setStatus] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  const list = useServerList<Ticket, { status: string }>({
    filters: { status },
    fetcher: ({ page, pageSize, search, status: st, signal }) =>
      listTickets({ page, pageSize, search, status: st || undefined, signal })
  });

  async function onAssign(id: string) {
    const assignee = window.prompt('Assign to (admin email):');
    if (!assignee) return;
    try {
      await assignTicket(id, assignee);
      setNotice(`Ticket ${id} assigned.`);
      void list.reload();
    } catch (e) {
      setNotice(e instanceof ApiError ? `${e.message} (${e.code})` : 'Assign failed');
    }
  }

  async function onResolve(id: string) {
    const resolution = window.prompt('Resolution summary (required):');
    if (!resolution) return;
    try {
      await resolveTicket(id, resolution);
      setNotice(`Ticket ${id} resolved.`);
      void list.reload();
    } catch (e) {
      setNotice(e instanceof ApiError ? `${e.message} (${e.code})` : 'Resolve failed');
    }
  }

  return (
    <div>
      <h2 style={{ margin: '0 0 4px' }}>Support</h2>
      <p className="muted">Triage queue across customers, vendors and riders. Assignment gated by SUPPORT_ASSIGN.</p>
      {notice ? <div className="alert info" role="status">{notice}</div> : null}
      <div className="toolbar">
        <input type="search" aria-label="Search tickets" placeholder="Search subject / requester…" value={list.search} onChange={(e) => list.setSearch(e.target.value)} />
        <select aria-label="Filter by status" value={status} onChange={(e) => { setStatus(e.target.value); list.setPage(0); }}>
          {STATUSES.map((s) => (
            <option key={s} value={s}>{s || 'All statuses'}</option>
          ))}
        </select>
      </div>
      <DataTable<Ticket>
        columns={[
          { key: 'subject', header: 'Subject' },
          { key: 'requester', header: 'Requester' },
          { key: 'priority', header: 'Priority', render: (r) => <StatusBadge status={r.priority} /> },
          { key: 'assignee', header: 'Assignee', render: (r) => r.assignee ?? 'Unassigned' },
          { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
          {
            key: 'actions', header: 'Actions', render: (r) => (
              <RequireScope scopes={['SUPPORT_ASSIGN']} fallback={<span className="muted">—</span>}>
                <span style={{ display: 'inline-flex', gap: 6 }}>
                  <button className="btn sm" onClick={() => void onAssign(r.id)}>Assign</button>
                  <button className="btn sm primary" onClick={() => void onResolve(r.id)}>Resolve</button>
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
