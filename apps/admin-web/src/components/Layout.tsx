import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import type { Scope } from '../auth/AuthContext';

interface NavItem {
  to: string;
  label: string;
  scopes: Scope[];
  section: string;
}

const NAV: NavItem[] = [
  { to: '/', label: 'Dashboard', scopes: [], section: 'Overview' },
  { to: '/customers', label: 'Customers', scopes: ['ORDER_VIEW'], section: 'Marketplace' },
  { to: '/vendors', label: 'Vendors', scopes: ['VENDOR_VIEW'], section: 'Marketplace' },
  { to: '/verifications', label: 'Vendor Verification', scopes: ['VENDOR_VIEW', 'VENDOR_APPROVE'], section: 'Marketplace' },
  { to: '/orders', label: 'Orders', scopes: ['ORDER_VIEW'], section: 'Fulfillment' },
  { to: '/subscriptions', label: 'Subscriptions', scopes: ['ORDER_VIEW'], section: 'Fulfillment' },
  { to: '/deliveries', label: 'Deliveries', scopes: ['ORDER_VIEW', 'RIDER_VIEW'], section: 'Fulfillment' },
  { to: '/zones', label: 'Service Zones', scopes: ['SETTINGS_MANAGE'], section: 'Fulfillment' },
  { to: '/payments', label: 'Payments', scopes: ['FINANCE_VIEW'], section: 'Finance' },
  { to: '/payouts', label: 'Payouts', scopes: ['FINANCE_VIEW', 'PAYOUT_MANAGE'], section: 'Finance' },
  { to: '/support', label: 'Support', scopes: ['SUPPORT_VIEW'], section: 'Operations' },
  { to: '/audit', label: 'Audit Logs', scopes: ['AUDIT_VIEW'], section: 'Operations' },
  { to: '/settings', label: 'Settings', scopes: ['SETTINGS_MANAGE'], section: 'Operations' }
];

function sections(items: NavItem[]) {
  const out: { section: string; items: NavItem[] }[] = [];
  for (const it of items) {
    const last = out[out.length - 1];
    if (last && last.section === it.section) last.items.push(it);
    else out.push({ section: it.section, items: [it] });
  }
  return out;
}

export function Layout() {
  const { admin, scopes, logout, hasAnyScope } = useAuth();
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();

  const visible = NAV.filter((n) => n.scopes.length === 0 || hasAnyScope(...n.scopes));

  return (
    <div className="shell">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <aside className={`sidebar${open ? ' open' : ''}`} aria-label="Primary">
        <div className="brand">
          <strong>Feazto Admin</strong>
          <small>Operations control plane</small>
        </div>
        <nav className="nav">
          {sections(visible).map((s) => (
            <div key={s.section}>
              <div className="nav-section">{s.section}</div>
              {s.items.map((n) => (
                <NavLink key={n.to} to={n.to} end={n.to === '/'} onClick={() => setOpen(false)} className={({ isActive }) => (isActive ? 'active' : '')}>
                  <span className="dot" aria-hidden="true" />
                  {n.label}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="side-foot">
          <div>{admin?.email ?? '—'}</div>
          <div>
            {admin?.role} · {scopes.length} scopes
          </div>
          <button
            className="btn sm"
            onClick={() => {
              logout();
              navigate('/login');
            }}
          >
            Sign out
          </button>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <button className="btn sm menu-btn" onClick={() => setOpen((v) => !v)} aria-expanded={open} aria-label="Toggle navigation">
            ☰ Menu
          </button>
          <h1>Feazto Operations</h1>
          <div className="admin-chip" aria-label="Signed-in admin">
            <span>
              {admin?.name ?? admin?.email} · {admin?.role}
            </span>
          </div>
        </header>
        <main id="main" className="content" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
