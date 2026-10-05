export interface Column<T> {
  key: string;
  header: string;
  render?: (row: T) => React.ReactNode;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[];
  loading: boolean;
  error: string | null;
  emptyText?: string;
  getRowKey: (row: T, index: number) => string;
  caption?: string;
}

/**
 * Accessible server-driven table. Pagination/filtering live in the page
 * (see hooks/useServerList); this component only renders states.
 */
export function DataTable<T>({ columns, rows, loading, error, emptyText, getRowKey, caption }: DataTableProps<T>) {
  if (loading) return <p role="status" aria-live="polite">Loading…</p>;
  if (error)
    return (
      <div className="alert error" role="alert">
        {error}
      </div>
    );
  if (rows.length === 0) return <p className="muted">{emptyText ?? 'No records found.'}</p>;

  return (
    <div className="table-wrap">
      <table className="data">
        {caption ? <caption className="muted">{caption}</caption> : null}
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} scope="col">
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={getRowKey(row, i)}>
              {columns.map((c) => (
                <td key={c.key}>{c.render ? c.render(row) : (row as Record<string, unknown>)[c.key] as React.ReactNode}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

interface PagerProps {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
  onPage: (page: number) => void;
}

export function Pager({ page, pageSize, total, totalPages, onPage }: PagerProps) {
  return (
    <div className="pager" role="navigation" aria-label="Pagination">
      <span aria-live="polite">
        Page {totalPages === 0 ? 0 : page + 1} of {totalPages} · {total} result{total === 1 ? '' : 's'} · {pageSize}/page
      </span>
      <button className="btn sm" disabled={page <= 0} onClick={() => onPage(page - 1)} aria-label="Previous page">
        ‹ Prev
      </button>
      <button className="btn sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)} aria-label="Next page">
        Next ›
      </button>
    </div>
  );
}
