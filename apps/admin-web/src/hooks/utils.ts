import { useEffect, useState } from 'react';

/** Debounce any value (used for server-side search inputs). */
export function useDebounce<T>(value: T, delayMs = 400): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(t);
  }, [value, delayMs]);
  return debounced;
}

/** Today in YYYY-MM-DD (local timezone) for dashboard `?date=` queries. */
export function todayLocal(): string {
  const d = new Date();
  const m = `${d.getMonth() + 1}`.padStart(2, '0');
  const day = `${d.getDate()}`.padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

/** Money formatting (INR default). */
export function inr(paiseOrRupees: number | null | undefined, inPaise = false): string {
  if (paiseOrRupees == null || Number.isNaN(paiseOrRupees)) return '—';
  const rupees = inPaise ? paiseOrRupees / 100 : paiseOrRupees;
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(rupees);
}
