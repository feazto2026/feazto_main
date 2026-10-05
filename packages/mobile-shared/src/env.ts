/**
 * Shared environment contract for all React Native apps.
 * Public values ONLY — never add SERVICE_ROLE keys, webhook secrets,
 * SMS credentials, or any server secret here (backend-env-only).
 */

function required(name: string, value: string | undefined): string {
  if (!value || !value.trim()) {
    throw new Error(
      `[mobile-shared/env] Missing required env var ${name}. ` +
        `Set EXPO_PUBLIC_API_BASE_URL / EXPO_PUBLIC_SUPABASE_URL / EXPO_PUBLIC_SUPABASE_ANON_KEY in app config.`,
    );
  }
  return value.trim();
}

function optional(name: string, value: string | undefined, fallback = ''): string {
  if (value == null || value === '') return fallback;
  return String(value).trim();
}

declare const process: { env?: Record<string, string | undefined> } | undefined;
declare const require: ((id: string) => any) | undefined;

function read(name: string): string | undefined {
  try {
    const req = typeof require !== 'undefined' ? require : undefined;
    const Constants = req?.('expo-constants')?.default ?? req?.('expo-constants');
    const extra = Constants?.expoConfig?.extra ?? Constants?.manifest?.extra ?? {};
    if (extra && typeof extra[name] === 'string') return extra[name] as string;
  } catch {
    /* expo-constants not installed — fall through to process.env */
  }
  try {
    return typeof process !== 'undefined' ? process?.env?.[name] : undefined;
  } catch {
    return undefined;
  }
}

/** Raw public env (throws on missing required keys when accessed). */
function buildEnv() {
  return {
    API_BASE_URL: required('API_BASE_URL', read('API_BASE_URL') ?? read('EXPO_PUBLIC_API_BASE_URL')),
    SUPABASE_URL: required('SUPABASE_URL', read('SUPABASE_URL') ?? read('EXPO_PUBLIC_SUPABASE_URL')),
    SUPABASE_ANON_KEY: required(
      'SUPABASE_ANON_KEY',
      read('SUPABASE_ANON_KEY') ?? read('EXPO_PUBLIC_SUPABASE_ANON_KEY'),
    ),
    API_VERSION: optional('API_VERSION', read('API_VERSION') ?? read('EXPO_PUBLIC_API_VERSION'), 'v1'),
    DEV_OTP_BYPASS: optional('DEV_OTP_BYPASS', read('DEV_OTP_BYPASS'), ''),
  } as const;
}

/** Lenient read that never throws (for boot / config screens). */
export function readEnvLenient(): { API_BASE_URL: string; SUPABASE_URL: string; SUPABASE_ANON_KEY: string; API_VERSION: string } {
  const base = read('API_BASE_URL') ?? read('EXPO_PUBLIC_API_BASE_URL') ?? 'http://localhost:8080';
  const url = read('SUPABASE_URL') ?? read('EXPO_PUBLIC_SUPABASE_URL') ?? '';
  const anon = read('SUPABASE_ANON_KEY') ?? read('EXPO_PUBLIC_SUPABASE_ANON_KEY') ?? '';
  const version = read('API_VERSION') ?? read('EXPO_PUBLIC_API_VERSION') ?? 'v1';
  return { API_BASE_URL: base.trim(), SUPABASE_URL: url.trim(), SUPABASE_ANON_KEY: anon.trim(), API_VERSION: version.trim() || 'v1' };
}

let cached: ReturnType<typeof buildEnv> | null = null;

export const env = new Proxy({} as ReturnType<typeof buildEnv>, {
  get(_t, prop: string) {
    if (!cached) cached = buildEnv();
    return (cached as Record<string, unknown>)[prop];
  },
});

export function apiBase(version?: string): string {
  const v = (version ?? readEnvLenient().API_VERSION).trim() || 'v1';
  const base = readEnvLenient().API_BASE_URL.replace(/\/+$/, '');
  return `${base}/api/${v}`;
}
