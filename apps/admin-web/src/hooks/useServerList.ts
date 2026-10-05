import { useCallback, useEffect, useRef, useState } from 'react';
import type { Page } from '../api/client';

export interface ServerListParams {
  page: number;
  pageSize: number;
  search: string;
  [key: string]: string | number | boolean | undefined;
}

interface UseServerListOptions<T, TFilters extends Record<string, unknown>> {
  pageSize?: number;
  filters?: TFilters;
  fetcher: (params: { page: number; pageSize: number; search: string } & TFilters & { signal: AbortSignal }) => Promise<Page<T>>;
  debounceMs?: number;
}

/**
 * Reusable server-pagination + debounced-search state machine.
 * Pages call with their domain fetcher; hook owns page/search/loading/error/total.
 */
export function useServerList<T, TFilters extends Record<string, unknown> = Record<string, unknown>>(
  options: UseServerListOptions<T, TFilters>
) {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [rows, setRows] = useState<T[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const filtersRef = useRef(options.filters);
  filtersRef.current = options.filters;

  const pageSize = options.pageSize ?? 20;
  const debounceMs = options.debounceMs ?? 400;

  useEffect(() => {
    const t = setTimeout(() => {
      setDebouncedSearch(search);
      setPage(0);
    }, debounceMs);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search, debounceMs]);

  const reload = useCallback(async () => {
    const ctrl = new AbortController();
    setLoading(true);
    setError(null);
    try {
      const res = await options.fetcher({
        page,
        pageSize,
        search: debouncedSearch,
        ...(filtersRef.current as TFilters),
        signal: ctrl.signal
      });
      setRows(res.content ?? []);
      setTotal(res.totalElements ?? 0);
      setTotalPages(res.totalPages ?? 0);
    } catch (e) {
      if ((e as Error).name === 'AbortError') return;
      setError(e instanceof Error ? e.message : 'Failed to load');
      setRows([]);
    } finally {
      setLoading(false);
    }
    return () => ctrl.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, pageSize, debouncedSearch, JSON.stringify(options.filters)]);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { page, setPage, search, setSearch, rows, total, totalPages, pageSize, loading, error, reload };
}
