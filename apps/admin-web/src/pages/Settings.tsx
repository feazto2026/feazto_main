import { useEffect, useState } from 'react';
import { getSettings, saveSettings } from '../api/admin';
import type { PlatformSettings } from '../api/admin';
import { ApiError } from '../api/client';

export function SettingsPage() {
  const [form, setForm] = useState<PlatformSettings | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    getSettings()
      .then(setForm)
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed to load settings'))
      .finally(() => setLoading(false));
  }, []);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!form) return;
    setSaving(true);
    setNotice(null);
    try {
      const saved = await saveSettings(form);
      setForm(saved);
      setNotice('Settings saved. Change recorded in audit log.');
    } catch (err) {
      setNotice(err instanceof ApiError ? `${err.message} (${err.code})` : 'Save failed');
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <p role="status">Loading settings…</p>;
  if (error) return <div className="alert error" role="alert">{error}</div>;
  if (!form) return <p className="muted">No settings found.</p>;

  return (
    <div>
      <h2 style={{ margin: '0 0 4px' }}>Settings</h2>
      <p className="muted">Platform configuration. Gated by SETTINGS_MANAGE; every save is audited.</p>
      {notice ? <div className="alert info" role="status">{notice}</div> : null}
      <form className="form" onSubmit={onSubmit}>
        <label>
          Default commission %
          <input type="number" min={0} max={50} step={0.5} value={form.commissionPctDefault} onChange={(e) => setForm({ ...form, commissionPctDefault: Number(e.target.value) })} />
        </label>
        <label>
          Default delivery fee (₹)
          <input type="number" min={0} step={1} value={form.deliveryFeeDefault} onChange={(e) => setForm({ ...form, deliveryFeeDefault: Number(e.target.value) })} />
        </label>
        <label>
          Support email
          <input type="email" value={form.supportEmail} onChange={(e) => setForm({ ...form, supportEmail: e.target.value })} />
        </label>
        <label style={{ display: 'flex', gap: 8, alignItems: 'center', flexDirection: 'row' }}>
          <input type="checkbox" checked={form.maintenanceMode} onChange={(e) => setForm({ ...form, maintenanceMode: e.target.checked })} />
          Maintenance mode
        </label>
        <button className="btn primary" disabled={saving} type="submit">
          {saving ? 'Saving…' : 'Save settings'}
        </button>
      </form>
    </div>
  );
}
