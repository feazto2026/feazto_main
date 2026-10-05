import { useEffect, useState } from 'react';
import { RequireScope } from '../auth/guards';
import { createZone, listZones, toggleZone } from '../api/admin';
import type { ServiceZone } from '../api/admin';
import { StatusBadge } from '../components/StatusBadge';
import { ApiError } from '../api/client';

export function ServiceZonesPage() {
  const [zones, setZones] = useState<ServiceZone[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [city, setCity] = useState('');

  async function load() {
    setLoading(true);
    setError(null);
    try {
      setZones(await listZones());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load zones');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function onCreate(e: React.FormEvent) {
    e.preventDefault();
    try {
      await createZone({ name, city, active: true });
      setName('');
      setCity('');
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? `${err.message} (${err.code})` : 'Create failed');
    }
  }

  async function onToggle(z: ServiceZone) {
    try {
      await toggleZone(z.id, !z.active);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? `${err.message} (${err.code})` : 'Update failed');
    }
  }

  return (
    <div>
      <h2 style={{ margin: '0 0 4px' }}>Service zones</h2>
      <p className="muted">Serviceability is a domain capability — zones, slots and availability combine server-side.</p>
      {error ? <div className="alert error" role="alert">{error}</div> : null}
      <RequireScope scopes={['SETTINGS_MANAGE']}>
        <form className="form" onSubmit={onCreate} style={{ maxWidth: 480, margin: '12px 0' }}>
          <label>
            Zone name
            <input required value={name} onChange={(e) => setName(e.target.value)} placeholder="Anna Nagar" />
          </label>
          <label>
            City
            <input required value={city} onChange={(e) => setCity(e.target.value)} placeholder="Chennai" />
          </label>
          <button className="btn primary" type="submit">Add zone</button>
        </form>
      </RequireScope>
      {loading ? (
        <p role="status">Loading…</p>
      ) : (
        <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit,minmax(240px,1fr))' }}>
          {zones.map((z) => (
            <div className="card" key={z.id}>
              <h3>{z.name}</h3>
              <div className="big" style={{ fontSize: 18 }}>{z.city}</div>
              <p>
                <StatusBadge status={z.active ? 'ACTIVE' : 'PAUSED'} /> <span className="muted">{z.vendorCount} vendors</span>
              </p>
              <RequireScope scopes={['SETTINGS_MANAGE']}>
                <button className="btn sm" onClick={() => void onToggle(z)}>
                  {z.active ? 'Deactivate' : 'Activate'}
                </button>
              </RequireScope>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
