import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { get, getToken, post, setToken } from '../api/client';

/**
 * Permission scopes (platform spec §21). Backend is authoritative;
 * the UI only hides/disables actions for UX — every API enforces scopes.
 */
export const SCOPES = [
  'VENDOR_VIEW',
  'VENDOR_APPROVE',
  'VENDOR_SUSPEND',
  'ORDER_VIEW',
  'ORDER_CANCEL',
  'ORDER_REFUND',
  'RIDER_VIEW',
  'RIDER_APPROVE',
  'RIDER_SUSPEND',
  'FINANCE_VIEW',
  'PAYOUT_MANAGE',
  'SUPPORT_VIEW',
  'SUPPORT_ASSIGN',
  'AUDIT_VIEW',
  'SETTINGS_MANAGE'
] as const;

export type Scope = (typeof SCOPES)[number];

export const ROLE_SCOPES: Record<string, Scope[]> = {
  SUPER_ADMIN: [...SCOPES],
  OPS_ADMIN: ['VENDOR_VIEW', 'VENDOR_APPROVE', 'VENDOR_SUSPEND', 'ORDER_VIEW', 'ORDER_CANCEL', 'RIDER_VIEW', 'RIDER_APPROVE', 'SUPPORT_VIEW', 'SUPPORT_ASSIGN', 'AUDIT_VIEW'],
  FINANCE_ADMIN: ['ORDER_VIEW', 'ORDER_REFUND', 'FINANCE_VIEW', 'PAYOUT_MANAGE', 'AUDIT_VIEW'],
  SUPPORT_ADMIN: ['ORDER_VIEW', 'SUPPORT_VIEW', 'SUPPORT_ASSIGN', 'VENDOR_VIEW', 'RIDER_VIEW']
};

export interface AdminUser {
  id: string;
  email: string;
  name?: string;
  role: keyof typeof ROLE_SCOPES | string;
  scopes: Scope[];
}

interface LoginResponse {
  token: string;
  admin: AdminUser;
}

interface AuthState {
  admin: AdminUser | null;
  scopes: Scope[];
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  hasScope: (scope: Scope) => boolean;
  hasAnyScope: (...scopes: Scope[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);
const ADMIN_KEY = 'feazto_admin_user';

function loadStored(): AdminUser | null {
  try {
    const raw = localStorage.getItem(ADMIN_KEY);
    return raw ? (JSON.parse(raw) as AdminUser) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [admin, setAdmin] = useState<AdminUser | null>(() => (getToken() ? loadStored() : null));
  const [loading, setLoading] = useState(false);

  // Rehydrate: token exists but no cached profile (e.g. new tab) -> fetch /me.
  useEffect(() => {
    if (getToken() && !admin) {
      void (async () => {
        try {
          const me = await get<AdminUser>('/api/v1/admin/auth/me');
          setAdmin(me);
          localStorage.setItem(ADMIN_KEY, JSON.stringify(me));
        } catch {
          setToken(null);
          localStorage.removeItem(ADMIN_KEY);
        }
      })();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    setLoading(true);
    try {
      const res = await post<LoginResponse>('/api/v1/admin/auth/login', { email, password });
      setToken(res.token);
      setAdmin(res.admin);
      try {
        localStorage.setItem(ADMIN_KEY, JSON.stringify(res.admin));
      } catch {
        /* ignore */
      }
    } finally {
      setLoading(false);
    }
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setAdmin(null);
    try {
      localStorage.removeItem(ADMIN_KEY);
    } catch {
      /* ignore */
    }
  }, []);

  const scopes = useMemo<Scope[]>(() => admin?.scopes ?? [], [admin]);

  const value = useMemo<AuthState>(
    () => ({
      admin,
      scopes,
      loading,
      login,
      logout,
      hasScope: (s) => scopes.includes(s),
      hasAnyScope: (...ss) => ss.some((s) => scopes.includes(s))
    }),
    [admin, scopes, loading, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
