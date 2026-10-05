import { DataTable, Pager } from '../components/DataTable';
import { listAuditLogs } from '../api/admin';
import type { AuditLog } from '../api/admin';
import { useServerList } from '../hooks/useServerList';

export function AuditLogsPage() {
  const list = useServerList<AuditLog, Record<string, never>>({
    fetcher: ({ page, pageSize, search, signal }) => listAuditLogs({ page, pageSize, search, signal })
  });

  return (
    <div>
      <h2 style={{ margin: '0 0 4px' }}>Audit logs</h2>
      <p className="muted">Append-only record of privileged admin actions — who did what, to which entity, with what reason.</p>
      <div className="toolbar">
        <input type="search" aria-label="Search audit logs" placeholder="Search actor / action / entity…" value={list.search} onChange={(e) => list.setSearch(e.target.value)} />
      </div>
      <DataTable<AuditLog>
        columns={[
          { key: 'at', header: 'At' },
          { key: 'actor', header: 'Actor' },
          { key: 'action', header: 'Action' },
          { key: 'entity', header: 'Entity', render: (r) => (r.entityId ? `${r.entity} · ${r.entityId.slice(0, 8)}…` : r.entity) },
          { key: 'reason', header: 'Reason', render: (r) => r.reason ?? '—' }
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
