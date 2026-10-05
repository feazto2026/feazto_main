import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';

export function LoginPage() {
  const { login, loading } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  const loc = useLocation() as { state?: { from?: string } };

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await login(email.trim(), password);
      navigate(loc.state?.from ?? '/', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? `${err.message} (${err.code})` : 'Sign-in failed');
    }
  }

  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 16 }}>
      <div className="card" style={{ width: 400, maxWidth: '100%' }}>
        <h2 style={{ margin: '0 0 4px' }}>Feazto Admin</h2>
        <p className="muted" style={{ marginTop: 0 }}>
          Operations control plane. Sign in with an admin account.
        </p>
        {error ? (
          <div className="alert error" role="alert" style={{ marginBottom: 12 }}>
            {error}
          </div>
        ) : null}
        <form className="form" onSubmit={onSubmit}>
          <label>
            Work email
            <input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="ops@feazto.in" />
          </label>
          <label>
            Password
            <input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
          </label>
          <button className="btn primary" disabled={loading} type="submit">
            {loading ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        <p className="muted" style={{ fontSize: 12 }}>
          Auth: Supabase JWT → Spring Boot RBAC. UI scopes are display-only; the API enforces permissions.
        </p>
      </div>
    </div>
  );
}
