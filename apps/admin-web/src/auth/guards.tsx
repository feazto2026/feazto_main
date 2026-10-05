import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import type { Scope } from '../auth/AuthContext';

/** Redirects to /login when no JWT is present. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { admin } = useAuth();
  const loc = useLocation();
  if (!admin) return <Navigate to="/login" replace state={{ from: loc.pathname }} />;
  return <>{children}</>;
}

/**
 * Permission gate for UI only (backend enforces). Renders `fallback`
 * (default: nothing) when the admin lacks every listed scope.
 */
export function RequireScope({ scopes, children, fallback = null }: { scopes: Scope[]; children: ReactNode; fallback?: ReactNode }) {
  const { hasAnyScope } = useAuth();
  if (!hasAnyScope(...scopes)) return <>{fallback}</>;
  return <>{children}</>;
}
