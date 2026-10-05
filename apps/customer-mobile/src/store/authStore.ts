import { create } from 'zustand';
import { auth } from '../../api/auth';
import { tokenStore, configureRefresh, refreshAccessToken } from '../../../../packages/mobile-shared/src/index.js';

let refreshConfigured = false;
function ensureRefresh() {
  if (refreshConfigured) return;
  try {
    const p = (globalThis as { process?: { env?: Record<string, string | undefined> } }).process?.env;
    configureRefresh({
      supabaseUrl: p?.EXPO_PUBLIC_SUPABASE_URL,
      supabaseAnonKey: p?.EXPO_PUBLIC_SUPABASE_ANON_KEY,
    });
    refreshConfigured = true;
  } catch {
    /* env missing in test — requests still send JWT when set */
  }
}

interface AuthState {
  phone: string;
  otpSent: boolean;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  setPhone: (phone: string) => void;
  sendOtp: (phone: string) => Promise<boolean>;
  verifyOtp: (otp: string) => Promise<boolean>;
  restore: () => Promise<boolean>;
  logout: () => Promise<void>;
  clearError: () => void;
  withRefresh: () => Promise<boolean>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  phone: '',
  otpSent: false,
  isAuthenticated: false,
  isLoading: false,
  error: null,

  setPhone: (phone) => set({ phone }),

  sendOtp: async (phone) => {
    set({ isLoading: true, error: null });
    try {
      ensureRefresh();
      if (phone.trim().length < 10) {
        set({ isLoading: false, error: 'Please enter a valid 10-digit mobile number' });
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
      ensureRefresh();
      const res = await auth.verifyOtp(get().phone, otp.trim());
      if (res.refreshToken) {
        await tokenStore.setSession(res.token, res.refreshToken, res.expiresIn ?? 3600);
      } else {
        tokenStore.accessToken = res.token;
      }
      set({ isAuthenticated: true, isLoading: false, otpSent: false });
      return true;
    } catch (e) {
      set({ isLoading: false, error: e instanceof Error ? e.message : 'Invalid OTP' });
      return false;
    }
  },

  restore: async () => {
    ensureRefresh();
    const ok = await tokenStore.restore();
    if (ok) set({ isAuthenticated: true });
    return ok;
  },

  logout: async () => {
    try {
      await auth.logout();
    } catch {
      /* backend logout best-effort */
    }
    await tokenStore.clear();
    set({ isAuthenticated: false, phone: '', otpSent: false, error: null });
  },

  clearError: () => set({ error: null }),
  withRefresh: () => refreshAccessToken(),
}));
