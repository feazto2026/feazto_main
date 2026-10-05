/**
 * Shared environment contract for all React Native apps.
 *
 * Adopt WITHOUT rewriting screens: import { env } from '@feazto/mobile-shared'
 * (or a relative import) and use env.API_BASE_URL / SUPABASE_*.
 *
 * Only public values live here. NEVER add SERVICE_ROLE keys, webhook secrets,
 * SMS credentials, or any server secret to this file — those are backend-env-only.
 */

function required(name: string, value: string | undefined): string {
  if (!value || !value.trim()) {
    throw new Error(
      `[mobile-shared/env] Missing required env var ${name}. ` +
        `Set it via Expo extra / process.env / .env (e.g. API_BASE_URL).`
    );
  }
  return value.trim();
}

function optional(name: string, value: string | undefined, fallback = ''): string {
  if (value == null || value === '') {
    return fallback;
  }
  return String(value).trim();
}

// Expo: values may come from `expo-constants` extra OR process.env (web/bare).
// Keep this dependency-free so plain RN + Expo both work.
declare const process: { env?: Record<string, string | undefined> } | undefined;
declare const require: ((id: string) => any) | undefined;

function read(name: string): string | undefined {
  try {
    const req = typeof require !== 'undefined' ? require : undefined;
    const Constants = req?.('expo-constants')?.default ?? req?.('expo-constants');
    const extra = Constants?.expoConfig?.extra ?? Constants?.manifest?.extra ?? {};
    if (extra && typeof extra[name] === 'string') return extra[name] as string;
  } catch {
    // expo-constants not installed — fall through to process.env
  }
  try {
    return typeof process !== 'undefined' ? process?.env?.[name] : undefined;
  } catch {
    return undefined;
  }
}

export const env = {
  /** Spring Boot API origin, e.g. https://api.feazto.com (no trailing slash). */
  API_BASE_URL: required('API_BASE_URL', read('API_BASE_URL') ?? read('EXPO_PUBLIC_API_BASE_URL')),

  /** Supabase project URL — public identifier, safe to ship in the app. */
  SUPABASE_URL: required('SUPABASE_URL', read('SUPABASE_URL') ?? read('EXPO_PUBLIC_SUPABASE_URL')),

  /** Supabase anon (publishable) key — safe to ship; RLS + backend authz still enforced. */
  SUPABASE_ANON_KEY: required(
    'SUPABASE_ANON_KEY',
    read('SUPABASE_ANON_KEY') ?? read('EXPO_PUBLIC_SUPABASE_ANON_KEY')
  ),

  /** Optional API version prefix (default "v1" → calls go to /api/v1/...). */
  API_VERSION: optional('API_VERSION', read('API_VERSION') ?? read('EXPO_PUBLIC_API_VERSION'), 'v1'),

  /** __DEV__-only overrides; ignored in production builds. */
  DEV_OTP_BYPASS: optional('DEV_OTP_BYPASS', read('DEV_OTP_BYPASS'), ''),
} as const;

export function apiBase(version: string = env.API_VERSION): string {
  const base = env.API_BASE_URL.replace(/\/+$/, '');
  return `${base}/api/${version}`;
}
