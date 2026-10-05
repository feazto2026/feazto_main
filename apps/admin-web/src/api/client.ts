/**
 * API client for Feazto Admin Web.
 *
 * Conventions (per platform spec §25, §30, §28):
 * - Base URL from VITE_API_BASE_URL (same-origin /api proxy in dev).
 * - Success envelope: { success:true, data, message?, requestId? }
 * - Error envelope:   { success:false, error:{ code, message, details? }, requestId? }
 * - Auth: Bearer JWT from Supabase Auth via Spring Boot (stored in localStorage).
 * - Idempotency: every POST/PUT/PATCH/DELETE sends `Idempotency-Key`
 *   (auto-generated UUID when caller doesn't supply one).
 */

export interface ApiSuccess<T> {
  success: true;
  data: T;
  message?: string;
  requestId?: string;
}

export interface ApiFailure {
  success: false;
  error: { code: string; message: string; details?: unknown };
  requestId?: string;
}

export type ApiEnvelope<T> = ApiSuccess<T> | ApiFailure;

export interface Page<T> {
  content: T[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

export class ApiError extends Error {
  code: string;
  status: number;
  details?: unknown;
  requestId?: string;
  constructor(code: string, message: string, status: number, details?: unknown, requestId?: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.details = details;
    this.requestId = requestId;
  }
}

const TOKEN_KEY = 'feazto_admin_token';

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string | null): void {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* storage unavailable (private mode) — ignore */
  }
}

function baseUrl(): string {
  const raw = import.meta.env.VITE_API_BASE_URL?.trim();
  if (!raw) return '';
  return raw.replace(/\/$/, '');
}

function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  /** Override / supply idempotency key for mutating requests. */
  idempotencyKey?: string | null;
  signal?: AbortSignal;
}

export async function apiFetch<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const method = opts.method ?? 'GET';
  const url = new URL(`${baseUrl()}${path}`, window.location.origin);

  if (opts.query) {
    for (const [k, v] of Object.entries(opts.query)) {
      if (v === undefined || v === null || v === '') continue;
      url.searchParams.set(k, String(v));
    }
  }

  // Same-origin relative fetch when baseUrl is '' (dev proxy handles /api).
  const finalUrl = baseUrl() ? url.toString() : `${path}${url.search}`;
  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  const mutating = method !== 'GET';
  if (mutating) {
    headers['Content-Type'] = 'application/json';
    headers['Idempotency-Key'] = opts.idempotencyKey === null ? '' : (opts.idempotencyKey ?? newIdempotencyKey());
    if (headers['Idempotency-Key'] === '') delete headers['Idempotency-Key'];
  }

  let res: Response;
  try {
    res = await fetch(finalUrl, {
      method,
      headers,
      signal: opts.signal,
      body: mutating && opts.body !== undefined ? JSON.stringify(opts.body) : undefined
    });
  } catch (err) {
    throw new ApiError('NETWORK_ERROR', 'Cannot reach the API. Check VITE_API_BASE_URL and backend status.', 0, err);
  }

  const requestId = res.headers.get('x-request-id') ?? undefined;
  let envelope: ApiEnvelope<T> | null = null;
  try {
    envelope = (await res.json()) as ApiEnvelope<T>;
  } catch {
    if (!res.ok) throw new ApiError('HTTP_ERROR', `Request failed (${res.status})`, res.status, undefined, requestId);
    return undefined as T;
  }

  if (envelope && typeof envelope === 'object' && 'success' in envelope) {
    if (envelope.success) return (envelope as ApiSuccess<T>).data;
    const f = envelope as ApiFailure;
    if (res.status === 401) setToken(null);
    throw new ApiError(
      f.error?.code ?? 'UNKNOWN_ERROR',
      f.error?.message ?? 'Request failed',
      res.status,
      f.error?.details,
      f.requestId ?? requestId
    );
  }

  // Tolerate bare payloads (non-enveloped) for forward-compat.
  if (!res.ok) throw new ApiError('HTTP_ERROR', `Request failed (${res.status})`, res.status, envelope, requestId);
  return envelope as T;
}

export const get = <T>(path: string, query?: RequestOptions['query'], signal?: AbortSignal) =>
  apiFetch<T>(path, { method: 'GET', query, signal });

export const post = <T>(path: string, body?: unknown, opts?: Omit<RequestOptions, 'method' | 'body'>) =>
  apiFetch<T>(path, { ...opts, method: 'POST', body });

export const put = <T>(path: string, body?: unknown, opts?: Omit<RequestOptions, 'method' | 'body'>) =>
  apiFetch<T>(path, { ...opts, method: 'PUT', body });

export const patch = <T>(path: string, body?: unknown, opts?: Omit<RequestOptions, 'method' | 'body'>) =>
  apiFetch<T>(path, { ...opts, method: 'PATCH', body });

export const del = <T>(path: string, body?: unknown, opts?: Omit<RequestOptions, 'method' | 'body'>) =>
  apiFetch<T>(path, { ...opts, method: 'DELETE', body });
