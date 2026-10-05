import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { StatusBadge } from '../components/StatusBadge';
import { RequireScope } from '../auth/guards';
import { cancelOrder, getOrder, refundOrder } from '../api/admin';
import { inr } from '../hooks/utils';
import { ApiError } from '../api/client';
import type { OrderDetail } from '../api/admin';

export function OrderDetailPage() {
  const { id = '' } = useParams();
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    const ctrl = new AbortController();
    setLoading(true);
    getOrder(id)
      .then(setOrder)
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed to load order'))
      .finally(() => setLoading(false));
    return () => ctrl.abort();
  }, [id]);

  async function act(kind: 'cancel' | 'refund') {
    const reason = window.prompt(`${kind === 'cancel' ? 'Cancellation' : 'Refund'} reason (required, audited):`);
    if (!reason) return;
    try {
      if (kind === 'cancel') await cancelOrder(id, reason);
      else await refundOrder(id, undefined, reason);
      setNotice(`Order ${kind} requested with idempotency protection.`);
      setOrder(await getOrder(id));
    } catch (e) {
      setNotice(e instanceof ApiError ? `${e.message} (${e.code})` : 'Action failed');
    }
  }

  if (loading) return <p role="status">Loading order…</p>;
  if (error) return <div className="alert error" role="alert">{error}</div>;
  if (!order) return <p className="muted">Order not found.</p>;

  return (
    <div>
      <p>
        <Link to="/orders">← Back to orders</Link>
      </p>
      <h2 style={{ margin: '0 0 4px' }}>Order {order.id}</h2>
      <p>
        <StatusBadge status={order.status} /> <StatusBadge status={order.paymentStatus} label={`pay: ${order.paymentStatus}`} />
      </p>
      {notice ? <div className="alert info" role="status">{notice}</div> : null}
      <div className="detail-grid">
        <div className="card">
          <h3>Parties & totals</h3>
          <p><strong>Customer:</strong> {order.customerName}</p>
          <p><strong>Vendor:</strong> {order.vendorName}</p>
          <p><strong>Total:</strong> {inr(order.total)}</p>
          <RequireScope scopes={['ORDER_CANCEL', 'ORDER_REFUND']}>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <RequireScope scopes={['ORDER_CANCEL']}>
                <button className="btn sm danger" onClick={() => void act('cancel')}>Cancel order</button>
              </RequireScope>
              <RequireScope scopes={['ORDER_REFUND']}>
                <button className="btn sm" onClick={() => void act('refund')}>Issue refund</button>
              </RequireScope>
            </div>
          </RequireScope>
        </div>
        <div className="card">
          <h3>Items (price snapshots)</h3>
          <ul className="timeline">
            {order.items.map((it, i) => (
              <li key={i}>{it.name} × {it.qty} — {inr(it.unitPrice)}</li>
            ))}
          </ul>
        </div>
        <div className="card">
          <h3>Delivery</h3>
          <p>{order.delivery ? `${order.delivery.riderName ?? 'Unassigned'} · ${order.delivery.status}` : 'No delivery yet.'}</p>
          <p><Link to="/deliveries">Open deliveries →</Link></p>
        </div>
        <div className="card">
          <h3>Status history</h3>
          <ul className="timeline">
            {order.timeline.map((t, i) => (
              <li key={i}>{t.label} — <span className="muted">{t.at}</span></li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
