import { create } from 'zustand';
import { auth } from '../../api/auth';
import { tokenStore, configureRefresh } from '../../../../packages/mobile-shared/src/index.js';
import { deliveries } from '../../api/deliveries';
import type { DeliverySummary } from '../../../../packages/mobile-shared/src/index.js';

interface RiderAuth {
  phone: string;
  otpSent: boolean;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  sendOtp: (phone: string) => Promise<boolean>;
  verifyOtp: (otp: string) => Promise<boolean>;
  logout: () => Promise<void>;
}

function ensureRefresh() {
  try {
    const p = (globalThis as { process?: { env?: Record<string, string | undefined> } }).process?.env;
    configureRefresh({ supabaseUrl: p?.EXPO_PUBLIC_SUPABASE_URL, supabaseAnonKey: p?.EXPO_PUBLIC_SUPABASE_ANON_KEY });
  } catch {
    /* ignore */
  }
}

export const useRiderAuthStore = create<RiderAuth>((set, get) => ({
  phone: '',
  otpSent: false,
  isAuthenticated: false,
  isLoading: false,
  error: null,
  sendOtp: async (phone) => {
    set({ isLoading: true, error: null });
    try {
      ensureRefresh();
      if (phone.trim().length < 10) {
        set({ isLoading: false, error: 'Enter a valid 10-digit mobile number' });
        return false;
      }
      await auth.requestOtp(phone.trim());
      set({ phone: phone.trim(), otpSent: true, isLoading: false });
      return true;
    } catch (e) {
      set({ isLoading: false, error: e instanceof Error ? e.message : 'Could not send OTP' });
      return false;
    }
  },
  verifyOtp: async (otp) => {
    set({ isLoading: true, error: null });
    try {
      const res = await auth.verifyOtp(get().phone, otp.trim());
      if (res.refreshToken) await tokenStore.setSession(res.token, res.refreshToken, res.expiresIn ?? 3600);
      else tokenStore.accessToken = res.token;
      set({ isAuthenticated: true, isLoading: false, otpSent: false });
      return true;
    } catch (e) {
      set({ isLoading: false, error: e instanceof Error ? e.message : 'Invalid OTP' });
      return false;
    }
  },
  logout: async () => {
    try {
      await auth.logout();
    } catch {
      /* best-effort */
    }
    await tokenStore.clear();
    set({ isAuthenticated: false, phone: '', otpSent: false });
  },
}));

interface RiderWork {
  online: boolean;
  available: DeliverySummary[];
  mine: DeliverySummary[];
  isLoading: boolean;
  error: string | null;
  setOnline: (v: boolean) => void;
  loadAvailable: () => Promise<void>;
  loadMine: () => Promise<void>;
  accept: (id: string) => Promise<boolean>;
  pickup: (id: string) => Promise<boolean>;
  complete: (id: string, code?: string) => Promise<boolean>;
}

export const useRiderWorkStore = create<RiderWork>((set) => ({
  online: false,
  available: [],
  mine: [],
  isLoading: false,
  error: null,
  setOnline: (v) => set({ online: v }),
  loadAvailable: async () => {
    set({ isLoading: true, error: null });
    try {
      const list = await deliveries.available();
      set({ available: list ?? [], isLoading: false });
    } catch (e) {
      set({ isLoading: false, error: e instanceof Error ? e.message : 'Could not load deliveries' });
    }
  },
  loadMine: async () => {
    set({ isLoading: true, error: null });
    try {
      const list = await deliveries.mine();
      set({ mine: list ?? [], isLoading: false });
    } catch (e) {
      set({ isLoading: false, error: e instanceof Error ? e.message : 'Could not load my deliveries' });
    }
  },
  accept: async (id) => {
    try {
      await deliveries.accept(id);
      set((s) => ({ available: s.available.filter((d) => d.id !== id) }));
      return true;
    } catch (e) {
      set({ error: e instanceof Error ? e.message : 'Accept failed' });
      return false;
    }
  },
  pickup: async (id) => {
    try {
      await deliveries.pickup(id);
      return true;
    } catch (e) {
      set({ error: e instanceof Error ? e.message : 'Pickup failed' });
      return false;
    }
  },
  complete: async (id, code) => {
    try {
      await deliveries.complete(id, code);
      set((s) => ({ mine: s.mine.filter((d) => d.id !== id) }));
      return true;
    } catch (e) {
      set({ error: e instanceof Error ? e.message : 'Completion failed' });
      return false;
    }
  },
}));
