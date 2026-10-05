/**
 * Shared fetch-based API client for Customer / Vendor / Rider apps.
 *
 * Drop-in WITHOUT rewriting screens:
 *
 *   import { createApiClient } from '@feazto/mobile-shared/api-client';
 *   import { tokenStore } from '@feazto/mobile-shared/auth-refresh';
 *   export const api = createApiClient({ getAccessToken: () => tokenStore.accessToken });
 *   const order = await api.post('/orders', body, { idempotencyKey: uuid() });
 *
 * Guarantees:
 * - baseURL from env (apiBase()), JWT injected when present
 * - `Idempotency-Key` sent on mutating calls (caller passes or one is generated)
 * - `X-Request-Id` on every call; JSON envelope handling
 * - errors normalised to ApiError with stable `kind`:
 *   NETWORK / AUTH / VALIDATION / BUSINESS / PAYMENT / NOT_FOUND / PERMISSION / SERVER
 */
import { apiBase } from './env';

export type ApiErrorKind =
  | 'NETWORK'
  | 'AUTH'
  | 'VALIDATION'
  | 'BUSINESS'
  | 'PAYMENT'
  | 'NOT_FOUND'
  | 'PERMISSION'
  | 'SERVER';

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly code: string;
  readonly status: number;
  readonly requestId?: string;
  readonly details?: unknown;

  constructor(opts: {
    kind: ApiErrorKind;
    code: string;
    message: string;
    status: number;
    requestId?: string;
    details?: unknown;
  }) {
    super(opts.message);
    this.name = 'ApiError';
    this.kind = opts.kind;
    this.code = opts.code;
    this.status = opts.status;
    this.requestId = opts.requestId;
    this.details = opts.details;
  }

  /** True when the access token likely expired and a refresh+retry is worthwhile. */
  get isExpiredToken(): boolean {
    return this.status === 401 && (this.code === 'TOKEN_EXPIRED' || this.code === 'TOKEN_INVALID');
  }
}

export interface RequestOptions {
  idempotencyKey?: string;
  requestId?: string;
  signal?: AbortSignal;
  /** Extra headers (never put tokens here — use getAccessToken). */
  headers?: Record<string, string>;
  /** Called on 401 with an expired token; should refresh and return true to retry once. */
  onUnauthorized?: () => Promise<boolean>;
}

interface ClientOptions {
  getAccessToken?: () => string | null | undefined;
  baseUrl?: string;
  fetchImpl?: typeof fetch;
}

const uuid = (): string =>
  'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });

function mapToKind(status: number, code: string): ApiErrorKind {
  if (status === 0) return 'NETWORK';
  if (status === 401) return 'AUTH';
  if (status === 403) return 'PERMISSION';
  if (status === 404) return 'NOT_FOUND';
  if (status === 422 || status === 400) return 'VALIDATION';
  if (status === 402 || code.startsWith('PAYMENT_')) return 'PAYMENT';
  if (status >= 500) return 'SERVER';
  return 'BUSINESS';
}

export function createApiClient(options: ClientOptions = {}) {
  const baseUrl = (options.baseUrl ?? apiBase()).replace(/\/+$/, '');
  const doFetch = options.fetchImpl ?? fetch;

  async function request<T>(method: string, path: string, body?: unknown, opts: RequestOptions = {}): Promise<T> {
    const url = path.startsWith('http') ? path : `${baseUrl}${path.startsWith('/') ? '' : '/'}${path}`;
    const requestId = opts.requestId ?? uuid();
    const headers: Record<string, string> = {
      Accept: 'application/json',
      'X-Request-Id': requestId,
      ...(opts.headers ?? {}),
    };
    const token = options.getAccessToken?.();
    if (token) headers['Authorization'] = `Bearer ${token}`;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method.toUpperCase())) {
      headers['Idempotency-Key'] = opts.idempotencyKey ?? uuid();
    }

    let res: Response;
    try {
      res = await doFetch(url, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: opts.signal,
      });
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : 'Network request failed';
      throw new ApiError({ kind: 'NETWORK', code: 'NETWORK_ERROR', message, status: 0, requestId });
    }

    // 204 — nothing to parse
    if (res.status === 204) return undefined as unknown as T;

    let json: any = null;
    try {
      json = await res.json();
    } catch {
      // Non-JSON (gateway/proxy) — map by status only.
      if (!res.ok) {
        throw new ApiError({
          kind: mapToKind(res.status, ''),
          code: res.status === 401 ? 'UNAUTHENTICATED' : `HTTP_${res.status}`,
          message: `Request failed (${res.status})`,
          status: res.status,
          requestId: res.headers.get('X-Request-Id') ?? requestId,
        });
      }
      return undefined as unknown as T;
    }

    const rid: string | undefined = json?.requestId ?? res.headers.get('X-Request-Id') ?? requestId;
    if (res.ok && json?.success !== false) {
      return (('data' in json ? json.data : json) ?? undefined) as T;
    }

    const code: string = json?.error?.code ?? `HTTP_${res.status}`;
    const message: string = json?.error?.message ?? 'Request failed';
    const err = new ApiError({
      kind: mapToKind(res.status, code),
      code,
      message,
      status: res.status,
      requestId: rid,
      details: json?.error?.details,
    });

    if (err.isExpiredToken && opts.onUnauthorized) {
      const refreshed = await opts.onUnauthorized();
      if (refreshed) {
        return request<T>(method, path, body, { ...opts, onUnauthorized: undefined });
      }
    }
    throw err;
  }

  return {
    request,
    get: <T>(path: string, opts?: RequestOptions) => request<T>('GET', path, undefined, opts),
    post: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
      request<T>('POST', path, body, opts),
    put: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
      request<T>('PUT', path, body, opts),
    patch: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
      request<T>('PATCH', path, body, opts),
    delete: <T>(path: string, opts?: RequestOptions) => request<T>('DELETE', path, undefined, opts),
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
