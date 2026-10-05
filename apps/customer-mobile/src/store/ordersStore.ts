import { create } from 'zustand';
import { orders } from '../../api/orders';
import type { OrderDetail, OrderSummary } from '../../../../packages/mobile-shared/src/index.js';

// Server is authoritative: orders come from the backend only.
// No mock FZ-* orders are ever fabricated on the client.
interface OrdersState {
  list: OrderSummary[];
  active: OrderDetail | null;
  isLoading: boolean;
  error: string | null;
  load: () => Promise<void>;
  loadDetail: (id: string) => Promise<void>;
  cancel: (id: string, reason: string) => Promise<boolean>;
}

export const useOrdersStore = create<OrdersState>((set) => ({
  list: [],
  active: null,
  isLoading: false,
  error: null,

  load: async () => {
    set({ isLoading: true, error: null });
    try {
      const page = await orders.list({ page: 0, pageSize: 20 });
      set({ list: page.content ?? [], isLoading: false });
    } catch (e) {
      set({ isLoading: false, error: e instanceof Error ? e.message : 'Could not load orders' });
    }
  },

  loadDetail: async (id) => {
    set({ isLoading: true, error: null });
    try {
      const d = await orders.get(id);
      set({ active: d, isLoading: false });
    } catch (e) {
      set({ isLoading: false, error: e instanceof Error ? e.message : 'Could not load order' });
    }
  },

  cancel: async (id, reason) => {
    try {
      const d = await orders.cancel(id, reason);
      set((s) => ({ active: d, list: s.list.map((o) => (o.id === id ? { ...o, status: d.status } : o)) }));
      return true;
    } catch (e) {
      set({ error: e instanceof Error ? e.message : 'Could not cancel order' });
      return false;
    }
  },
}));
