/**
 * Token storage + single-flight Supabase refresh for React Native apps.
 *
 * Adopt WITHOUT rewriting screens — wire once at the app root:
 *
 *   import { tokenStore, refreshAccessToken, configureRefresh } from '@feazto/mobile-shared/auth-refresh';
 *   import { createApiClient } from '@feazto/mobile-shared/api-client';
 *
 *   configureRefresh({ supabaseUrl: env.SUPABASE_URL, supabaseAnonKey: env.SUPABASE_ANON_KEY });
 *   export const api = createApiClient({
 *     getAccessToken: () => tokenStore.accessToken,
 *   });
 *
 *   // per request:
 *   api.get('/orders', { onUnauthorized: () => refreshAccessToken() });
 *
 * Storage rules (security):
 * - access token: memory ONLY (never persisted).
 * - refresh token: SecureStore (expo-secure-store) when available, else an
 *   injectable async adapter — NEVER AsyncStorage/unencrypted disk.
 * - refresh rotation: new refresh token replaces the old one atomically;
 *   reuse failures clear the session (possible theft).
 */
import { env } from './env';

declare const require: ((id: string) => any) | undefined;

export interface SecureAdapter {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  deleteItem(key: string): Promise<void>;
}

const REFRESH_KEY = 'feazto.refresh_token';

let refreshConfig = {
  supabaseUrl: '',
  supabaseAnonKey: '',
};

let secureAdapter: SecureAdapter | null = null;

function memoryAdapter(): SecureAdapter {
  // Fallback only (tests / web). Warn: memory does not survive restarts.
  let v: string | null = null;
  return {
    getItem: async () => v,
    setItem: async (val: string) => {
      void val;
      v = val;
    },
    deleteItem: async () => {
      v = null;
    },
  };
}

async function secure(): Promise<SecureAdapter> {
  if (secureAdapter) return secureAdapter;
  try {
    const req = typeof require !== 'undefined' ? require : undefined;
    const SecureStore = req?.('expo-secure-store');
    if (SecureStore?.getItemAsync) {
      secureAdapter = {
        getItem: (k) => SecureStore.getItemAsync(k),
        setItem: (k, v) => SecureStore.setItemAsync(k, v),
        deleteItem: (k) => SecureStore.deleteItemAsync(k),
      };
      return secureAdapter;
    }
  } catch {
    // expo-secure-store not installed — caller should inject one.
  }
  secureAdapter = memoryAdapter();
  return secureAdapter;
}

/** Inject a custom encrypted adapter (e.g. Keychain-backed) — preferred for bare RN. */
export function setSecureAdapter(adapter: SecureAdapter): void {
  secureAdapter = adapter;
}

export function configureRefresh(opts: { supabaseUrl?: string; supabaseAnonKey?: string }): void {
  refreshConfig = {
    supabaseUrl: opts.supabaseUrl ?? env.SUPABASE_URL,
    supabaseAnonKey: opts.supabaseAnonKey ?? env.SUPABASE_ANON_KEY,
  };
}

/** In-memory session. Persisted refresh token is loaded via `restore()`. */
export const tokenStore: {
  accessToken: string | null;
  expiresAtSec: number | null;
  /** True when the access token is missing or within 60 s of expiry. */
  needsRefresh(): boolean;
  setSession(accessToken: string, refreshToken: string, expiresInSec?: number): Promise<void>;
  clear(): Promise<void>;
  restore(): Promise<boolean>;
} = {
  accessToken: null,
  expiresAtSec: null,

  needsRefresh(): boolean {
    if (!tokenStore.accessToken) return true;
    if (!tokenStore.expiresAtSec) return false;
    return Date.now() / 1000 > tokenStore.expiresAtSec - 60;
  },

  async setSession(accessToken: string, refreshToken: string, expiresInSec = 3600): Promise<void> {
    tokenStore.accessToken = accessToken;
    tokenStore.expiresAtSec = Math.floor(Date.now() / 1000) + expiresInSec;
    await (await secure()).setItem(REFRESH_KEY, refreshToken);
  },

  async clear(): Promise<void> {
    tokenStore.accessToken = null;
    tokenStore.expiresAtSec = null;
    await (await secure()).deleteItem(REFRESH_KEY);
    inFlight = null;
  },

  async restore(): Promise<boolean> {
    const rt = await (await secure()).getItem(REFRESH_KEY);
    if (!rt) return false;
    return refreshAccessTokenWith(rt);
  },
};

let inFlight: Promise<boolean> | null = null;

/** Single-flight refresh using the stored refresh token. Returns true on success. */
export function refreshAccessToken(): Promise<boolean> {
  if (inFlight) return inFlight;
  inFlight = (async () => {
    const rt = await (await secure()).getItem(REFRESH_KEY);
    if (!rt) return false;
    return refreshAccessTokenWith(rt);
  })().finally(() => {
    inFlight = null;
  });
  return inFlight;
}

async function refreshAccessTokenWith(refreshToken: string): Promise<boolean> {
  const { supabaseUrl, supabaseAnonKey } = refreshConfig;
  const base = (supabaseUrl || '').replace(/\/+$/, '');
  if (!base || !supabaseAnonKey) return false;
  try {
    const res = await fetch(`${base}/auth/v1/token?grant_type=refresh_token`, {
      method: 'POST',
      headers: {
        apikey: supabaseAnonKey,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ refresh_token: refreshToken }),
    });
    if (!res.ok) {
      // Reuse detection / revoked grant → drop the session, force re-login.
      await tokenStore.clear();
      return false;
    }
    const json = (await res.json()) as {
      access_token: string;
      refresh_token: string;
      expires_in?: number;
    };
    if (!json.access_token || !json.refresh_token) {
      await tokenStore.clear();
      return false;
    }
    await tokenStore.setSession(json.access_token, json.refresh_token, json.expires_in ?? 3600);
    return true;
  } catch {
    return false; // network blip — keep old tokens, caller surfaces NETWORK error
  }
}
