const STATUS_TONE: Record<string, 'ok' | 'warn' | 'bad' | 'info' | 'neutral'> = {
  // orders / deliveries
  COMPLETED: 'ok',
  DELIVERED: 'ok',
  ACTIVE: 'ok',
  APPROVED: 'ok',
  SUCCEEDED: 'ok',
  RESOLVED: 'ok',
  RELEASED: 'ok',
  PICKED_UP: 'info',
  OUT_FOR_DELIVERY: 'info',
  RIDER_ASSIGNED: 'info',
  READY_FOR_PICKUP: 'info',
  PREPARING: 'info',
  VENDOR_ACCEPTED: 'info',
  PLACED: 'info',
  ASSIGNED: 'info',
  OPEN: 'info',
  IN_PROGRESS: 'info',
  // attention
  PENDING: 'warn',
  PAYMENT_PENDING: 'warn',
  CREATED: 'warn',
  HOLD: 'warn',
  ON_HOLD: 'warn',
  PAUSED: 'warn',
  MEDIUM: 'warn',
  // negative
  FAILED: 'bad',
  CANCELLED: 'bad',
  REJECTED: 'bad',
  SUSPENDED: 'bad',
  HIGH: 'bad',
  URGENT: 'bad'
};

/** Uppercase pill for entity states. Unknown values fall back to neutral. */
export function StatusBadge({ status, label }: { status: string; label?: string }) {
  const key = (status ?? '').toUpperCase().replace(/[-\s]/g, '_');
  const tone = STATUS_TONE[key] ?? 'neutral';
  return (
    <span className={`badge ${tone}`} role="status" aria-label={`Status: ${label ?? status}`}>
      {label ?? status}
    </span>
  );
}
