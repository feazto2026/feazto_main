/**
 * Shared API transport for Customer / Vendor / Rider native apps.
 *
 * Contract (same as admin-web + root api-client.ts):
 * - Enveloped responses: { success:true, data } / { success:false, error:{code,message,details?} }
 * - JWT injected from the async TokenStore when present.
 * - `Idempotency-Key` auto-sent on POST/PUT/PATCH/DELETE (caller may supply one).
 * - `X-Request-Id` on every call; normalised ApiError with stable `kind`:
 *   NETWORK / AUTH / VALIDATION / BUSINESS / PAYMENT / NOT_FOUND / PERMISSION / SERVER.
 * - `onUnauthorized` hook lets callers refresh-and-retry once on expired tokens.
 */

export type ApiErrorKind =
  | 'NETWORK'
  | 'AUTH'
  | 'VALIDATION'
  | 'BUSINESS'
  | 'PAYMENT'
  | 'NOT_FOUND'
  | 'PERMISSION'
  | 'SERVER';

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

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly code: string;
  readonly status: number;
  readonly requestId?: string;
  readonly details?: unknown;

  constructor(opts: { kind: ApiErrorKind; code: string; message: string; status: number; requestId?: string; details?: unknown }) {
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
    return this.status === 401 && (this.code === 'TOKEN_EXPIRED' || this.code === 'TOKEN_INVALID' || this.code === 'UNAUTHENTICATED');
  }
}

export interface TokenStore {
  get(): Promise<string | null>;
  set(token: string | null): Promise<void>;
}

export const memoryTokenStore = (): TokenStore => {
  let token: string | null = null;
  return {
    get: async () => token,
    set: async (t) => {
      token = t;
    },
  };
};

export interface RequestOptions {
  idempotencyKey?: string | null;
  requestId?: string;
  signal?: AbortSignal;
  headers?: Record<string, string>;
  query?: Record<string, unknown>;
  /** Called on 401 with an expired token; return true to retry once after refresh. */
  onUnauthorized?: () => Promise<boolean>;
}

export interface ClientConfig {
  baseUrl: string;
  tokenStore?: TokenStore;
  fetchImpl?: typeof fetch;
}

function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    try {
      return (crypto as { randomUUID(): string }).randomUUID();
    } catch {
      /* fall through */
    }
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}-${Math.random().toString(36).slice(2, 10)}`;
}

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

export function createClient(config: ClientConfig) {
  const store = config.tokenStore ?? memoryTokenStore();
  const base = config.baseUrl.replace(/\/+$/, '');
  const doFetch = config.fetchImpl ?? fetch;

  async function request<T>(
    path: string,
    init: { method?: string; body?: unknown; query?: Record<string, unknown>; idempotencyKey?: string | null; requestId?: string; signal?: AbortSignal; headers?: Record<string, string>; onUnauthorized?: () => Promise<boolean> } = {},
  ): Promise<T> {
    const method = (init.method ?? 'GET').toUpperCase();
    const qs = new URLSearchParams();
    for (const [k, v] of Object.entries(init.query ?? {})) {
      if (v === undefined || v === null || v === '') continue;
      qs.set(k, String(v));
    }
    const url = `${base}${path.startsWith('/') ? '' : '/'}${path}${qs.toString() ? `?${qs}` : ''}`;
    const requestId = init.requestId ?? uuid();
    const headers: Record<string, string> = {
      Accept: 'application/json',
      'X-Request-Id': requestId,
      ...(init.headers ?? {}),
    };
    const token = await store.get();
    if (token) headers.Authorization = `Bearer ${token}`;
    if (method !== 'GET') {
      headers['Content-Type'] = 'application/json';
      if (init.idempotencyKey !== null) headers['Idempotency-Key'] = init.idempotencyKey ?? uuid();
    }

    let res: Response;
    try {
      res = await doFetch(url, {
        method,
        headers,
        body: method !== 'GET' && init.body !== undefined ? JSON.stringify(init.body) : undefined,
        signal: init.signal,
      });
    } catch (e: unknown) {
      throw new ApiError({
        kind: 'NETWORK',
        code: 'NETWORK_ERROR',
        message: 'Cannot reach the API. Check API_BASE_URL and backend status.',
        status: 0,
        requestId,
        details: e,
      });
    }

    if (res.status === 204) return undefined as unknown as T;

    let json: unknown = null;
    try {
      json = await res.json();
    } catch {
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

    const rec = json as Record<string, unknown> | null;
    const rid =
      (rec && typeof rec['requestId'] === 'string' ? (rec['requestId'] as string) : undefined) ??
      res.headers.get('X-Request-Id') ??
      requestId;

    if (rec && typeof rec === 'object' && 'success' in rec) {
      if ((rec as unknown as ApiSuccess<T>).success) {
        const data = 'data' in rec ? (rec as unknown as ApiSuccess<T>).data : (rec as unknown as T);
        return (data ?? undefined) as T;
      }
      const f = rec as unknown as ApiFailure;
      const code: string = f.error?.code ?? `HTTP_${res.status}`;
      const message: string = f.error?.message ?? 'Request failed';
      const err = new ApiError({ kind: mapToKind(res.status, code), code, message, status: res.status, requestId: rid, details: f.error?.details });
      if (err.isExpiredToken && init.onUnauthorized) {
        const refreshed = await init.onUnauthorized();
        if (refreshed) return request<T>(path, { ...init, onUnauthorized: undefined });
      }
      throw err;
    }

    if (!res.ok) {
      const err = new ApiError({
        kind: mapToKind(res.status, ''),
        code: `HTTP_${res.status}`,
        message: `Request failed (${res.status})`,
        status: res.status,
        requestId: rid,
        details: json,
      });
      if (err.isExpiredToken && init.onUnauthorized) {
        const refreshed = await init.onUnauthorized();
        if (refreshed) return request<T>(path, { ...init, onUnauthorized: undefined });
      }
      throw err;
    }
    return json as T;
  }

  return {
    tokenStore: store,
    request,
    get: <T>(path: string, query?: Record<string, unknown>, opts?: Omit<RequestOptions, 'query'>) =>
      request<T>(path, { method: 'GET', query, ...opts }),
    post: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
      request<T>(path, { method: 'POST', body, ...opts }),
    put: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
      request<T>(path, { method: 'PUT', body, ...opts }),
    patch: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
      request<T>(path, { method: 'PATCH', body, ...opts }),
    delete: <T>(path: string, opts?: RequestOptions) => request<T>(path, { method: 'DELETE', ...opts }),
  };
}

export type SharedClient = ReturnType<typeof createClient>;

/**
 * Drop-in transport alias matching the root `api-client.ts` signature:
 * `createApiClient({ getAccessToken, baseUrl, fetchImpl })`.
 */
export function createApiClient(options: {
  getAccessToken?: () => string | null | undefined;
  baseUrl: string;
  fetchImpl?: typeof fetch;
}): SharedClient {
  const syncStore: TokenStore = {
    get: async () => options.getAccessToken?.() ?? null,
    set: async () => {
      /* read-only view of the app token store — writes go through auth.ts tokenStore */
    },
  };
  return createClient({ baseUrl: options.baseUrl, tokenStore: syncStore, fetchImpl: options.fetchImpl });
}

export type ApiClient = SharedClient;
