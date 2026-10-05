import { createClient, tokenStore, configureRefresh, readEnvLenient, refreshAccessToken } from '../../../packages/mobile-shared/src/index.js';
import type { SharedClient } from '../../../packages/mobile-shared/src/index.js';

// Vendor native app — shared client singleton (kitchen role).
// JWT from tokenStore, Idempotency-Key on mutations, X-Request-Id always,
// expired-token refresh-and-retry once. Mirrors legacy vendor_app navigation
// (Dashboard, Orders Queue, Menu, Availability, Earnings) without demo data.
let instance: SharedClient | null = null;

function buildBase(): string {
  const lenient = readEnvLenient();
  const origin = (lenient.API_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
  const v = (lenient.API_VERSION || 'v1').trim() || 'v1';
  return `${origin}/api/${v}`;
}

export function getAppClient(): SharedClient {
  if (!instance) {
    try {
      const lenient = readEnvLenient();
      configureRefresh({ supabaseUrl: lenient.SUPABASE_URL, supabaseAnonKey: lenient.SUPABASE_ANON_KEY });
    } catch {
      /* boot without supabase env */
    }
    const raw = createClient({
      baseUrl: buildBase(),
      tokenStore: {
        get: async () => tokenStore.accessToken,
        set: async (t) => {
          if (t === null) await tokenStore.clear();
        },
      },
    });
    const wrap = (method: 'get' | 'post' | 'put' | 'patch' | 'delete') => {
      const orig = (raw as unknown as Record<string, (...a: never[]) => Promise<unknown>>)[method].bind(raw);
      return (...args: never[]) => {
        if (method === 'get') {
          const [path, query, opts] = args as unknown as [string, unknown, { onUnauthorized?: () => Promise<boolean> } | undefined];
          const merged = { ...(opts ?? {}), onUnauthorized: opts?.onUnauthorized ?? (() => refreshAccessToken()) };
          return (orig as (...a: unknown[]) => Promise<unknown>)(path, query, merged);
        }
        const last = args[args.length - 1] as { onUnauthorized?: () => Promise<boolean> } | undefined;
        const hasOpts = last && typeof last === 'object' && ('onUnauthorized' in last || 'idempotencyKey' in last || 'signal' in last);
        if (hasOpts) {
          const opts = { ...last, onUnauthorized: last?.onUnauthorized ?? (() => refreshAccessToken()) };
          return orig(...([...args.slice(0, -1), opts] as never[]));
        }
        return orig(...([...args, { onUnauthorized: () => refreshAccessToken() }] as never[]));
      };
    };
    instance = {
      ...raw,
      get: wrap('get') as SharedClient['get'],
      post: wrap('post') as SharedClient['post'],
      put: wrap('put') as SharedClient['put'],
      patch: wrap('patch') as SharedClient['patch'],
      delete: wrap('delete') as SharedClient['delete'],
    };
  }
  return instance;
}
