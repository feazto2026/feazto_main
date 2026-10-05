import { create } from 'zustand';
import { vendors } from '../../api/vendors';
import { menus } from '../../api/menus';
import type { MenuItem, VendorSummary } from '../../../../packages/mobile-shared/src/index.js';

interface DiscoveryState {
  vendors: VendorSummary[];
  menu: MenuItem[];
  activeVendorId: string | null;
  search: string;
  category: string;
  isLoading: boolean;
  error: string | null;
  loadVendors: (params?: Record<string, unknown>) => Promise<void>;
  loadMenu: (vendorId: string) => Promise<void>;
  setSearch: (s: string) => void;
  setCategory: (c: string) => void;
}

export const useDiscoveryStore = create<DiscoveryState>((set, get) => ({
  vendors: [],
  menu: [],
  activeVendorId: null,
  search: '',
  category: 'All',
  isLoading: false,
  error: null,

  loadVendors: async (params) => {
    set({ isLoading: true, error: null });
    try {
      const page = await vendors.list({ search: get().search || undefined, ...params });
      set({ vendors: page.content ?? [], isLoading: false });
    } catch (e) {
      set({ isLoading: false, error: e instanceof Error ? e.message : 'Could not load kitchens' });
    }
  },

  loadMenu: async (vendorId) => {
    set({ isLoading: true, error: null, activeVendorId: vendorId });
    try {
      const items = await menus.getMenu(vendorId);
      set({ menu: items ?? [], isLoading: false });
    } catch (e) {
      set({ isLoading: false, error: e instanceof Error ? e.message : 'Could not load menu' });
    }
  },

  setSearch: (s) => set({ search: s }),
  setCategory: (c) => set({ category: c }),
}));
