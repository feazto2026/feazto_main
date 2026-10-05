import { create } from 'zustand';
import { menus } from '../../api/menus';
import { orders } from '../../api/orders';
import { vendors } from '../../api/vendors';
import type { MenuItem, OrderSummary } from '../../../../packages/mobile-shared/src/index.js';

// Vendor kitchen state — server-driven. Availability + preparation transitions
// call role-scoped backend endpoints; no local demo orders.
interface KitchenState {
  online: boolean;
  menu: MenuItem[];
  queue: OrderSummary[];
  isLoading: boolean;
  error: string | null;
  loadMenu: (vendorId: string) => Promise<void>;
  toggleItem: (itemId: string, available: boolean) => Promise<boolean>;
  loadQueue: () => Promise<void>;
  accept: (id: string) => Promise<boolean>;
  markReady: (id: string) => Promise<boolean>;
  setOnline: (v: boolean) => Promise<void>;
}

export const useKitchenStore = create<KitchenState>((set) => ({
  online: true,
  menu: [],
  queue: [],
  isLoading: false,
  error: null,

  loadMenu: async (vendorId) => {
    set({ isLoading: true, error: null });
    try {
      const items = await menus.getMenu(vendorId);
      set({ menu: items ?? [], isLoading: false });
    } catch (e) {
      set({ isLoading: false, error: e instanceof Error ? e.message : 'Could not load menu' });
    }
  },

  toggleItem: async (itemId, available) => {
    try {
      const updated = await menus.setAvailability(itemId, available);
      set((s) => ({ menu: s.menu.map((m) => (m.id === itemId ? updated : m)) }));
      return true;
    } catch (e) {
      set({ error: e instanceof Error ? e.message : 'Could not update availability' });
      return false;
    }
  },

  loadQueue: async () => {
    set({ isLoading: true, error: null });
    try {
      const page = await orders.list({ page: 0, pageSize: 20, role: 'VENDOR' });
      set({ queue: page.content ?? [], isLoading: false });
    } catch (e) {
      set({ isLoading: false, error: e instanceof Error ? e.message : 'Could not load orders' });
    }
  },

  accept: async (id) => {
    try {
      await orders.vendorAccept(id);
      set((s) => ({ queue: s.queue.map((o) => (o.id === id ? { ...o, status: 'PREPARING' } : o)) }));
      return true;
    } catch (e) {
      set({ error: e instanceof Error ? e.message : 'Accept failed' });
      return false;
    }
  },

  markReady: async (id) => {
    try {
      await orders.vendorReady(id);
      set((s) => ({ queue: s.queue.map((o) => (o.id === id ? { ...o, status: 'READY_FOR_PICKUP' } : o)) }));
      return true;
    } catch (e) {
      set({ error: e instanceof Error ? e.message : 'Mark-ready failed' });
      return false;
    }
  },

  setOnline: async (v) => {
    try {
      await vendors.setOpen(v);
      set({ online: v });
    } catch {
      set({ online: v });
    }
  },
}));
