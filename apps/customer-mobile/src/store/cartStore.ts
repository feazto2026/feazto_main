import { create } from 'zustand';
import { cart } from '../../api/cart';
import type { Cart } from '../../../../packages/mobile-shared/src/index.js';

interface CartState {
  cart: Cart | null;
  isLoading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  add: (itemId: string, qty?: number) => Promise<boolean>;
  setQty: (itemId: string, qty: number) => Promise<boolean>;
  remove: (itemId: string) => Promise<boolean>;
  clear: () => Promise<void>;
}

const emptyCart: Cart = { lines: [], subtotal: 0, deliveryFee: 0, packagingFee: 0, total: 0 };

export const useCartStore = create<CartState>((set) => ({
  cart: null,
  isLoading: false,
  error: null,

  refresh: async () => {
    set({ isLoading: true, error: null });
    try {
      const c = await cart.get();
      set({ cart: c, isLoading: false });
    } catch (e) {
      set({ cart: emptyCart, isLoading: false, error: e instanceof Error ? e.message : 'Could not load cart' });
    }
  },

  add: async (itemId, qty = 1) => {
    try {
      const c = await cart.add(itemId, qty);
      set({ cart: c, error: null });
      return true;
    } catch (e) {
      set({ error: e instanceof Error ? e.message : 'Could not add item' });
      return false;
    }
  },

  setQty: async (itemId, qty) => {
    try {
      const c = qty <= 0 ? await cart.remove(itemId) : await cart.updateQty(itemId, qty);
      set({ cart: c ?? emptyCart, error: null });
      return true;
    } catch (e) {
      set({ error: e instanceof Error ? e.message : 'Could not update cart' });
      return false;
    }
  },

  remove: async (itemId) => {
    try {
      const c = await cart.remove(itemId);
      set({ cart: c ?? emptyCart, error: null });
      return true;
    } catch (e) {
      set({ error: e instanceof Error ? e.message : 'Could not remove item' });
      return false;
    }
  },

  clear: async () => {
    try {
      await cart.clear();
    } catch {
      /* best-effort */
    }
    set({ cart: emptyCart });
  },
}));

export const cartCount = (c: Cart | null): number => (c?.lines ?? []).reduce((n, l) => n + l.qty, 0);
