/**
 * Token storage + single-flight Supabase refresh for native apps.
 *
 * Wire once at the app root:
 *   configureRefresh({ supabaseUrl: env.SUPABASE_URL, supabaseAnonKey: env.SUPABASE_ANON_KEY });
 *   await tokenStore.restore();
 *   api.get('/orders', { onUnauthorized: () => refreshAccessToken() });
 *
 * Storage rules:
 * - access token: memory ONLY (never persisted).
 * - refresh token: SecureStore when available, else injected adapter — NEVER AsyncStorage.
 * - reuse failures clear the session (possible theft → force re-login).
 */
import { readEnvLenient } from './env.js';

export interface SecureAdapter {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  deleteItem(key: string): Promise<void>;
}

const REFRESH_KEY = 'feazto.refresh_token';

let refreshConfig = { supabaseUrl: '', supabaseAnonKey: '' };
let secureAdapter: SecureAdapter | null = null;

function memoryAdapter(): SecureAdapter {
  let v: string | null = null;
  return {
    getItem: async () => v,
    setItem: async (val: string) => {
      v = val;
    },
    deleteItem: async () => {
      v = null;
    },
  };
}

declare const require: ((id: string) => any) | undefined;

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
    /* expo-secure-store not installed — caller should inject one */
  }
  secureAdapter = memoryAdapter();
  return secureAdapter;
}

/** Inject a custom encrypted adapter (e.g. Keychain-backed) — preferred for bare RN. */
export function setSecureAdapter(adapter: SecureAdapter): void {
  secureAdapter = adapter;
}

export function configureRefresh(opts: { supabaseUrl?: string; supabaseAnonKey?: string }): void {
  const lenient = readEnvLenient();
  refreshConfig = {
    supabaseUrl: opts.supabaseUrl ?? lenient.SUPABASE_URL,
    supabaseAnonKey: opts.supabaseAnonKey ?? lenient.SUPABASE_ANON_KEY,
  };
}

/** In-memory session. Persisted refresh token is loaded via `restore()`. */
export const tokenStore: {
  accessToken: string | null;
  expiresAtSec: number | null;
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
      headers: { apikey: supabaseAnonKey, 'Content-Type': 'application/json' },
      body: JSON.stringify({ refresh_token: refreshToken }),
    });
    if (!res.ok) {
      await tokenStore.clear();
      return false;
    }
    const json = (await res.json()) as { access_token: string; refresh_token: string; expires_in?: number };
    if (!json.access_token || !json.refresh_token) {
      await tokenStore.clear();
      return false;
    }
    await tokenStore.setSession(json.access_token, json.refresh_token, json.expires_in ?? 3600);
    return true;
  } catch {
    return false;
  }
}
