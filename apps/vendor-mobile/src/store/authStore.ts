import { create } from 'zustand';
import { auth } from '../../api/auth';
import { tokenStore, configureRefresh } from '../../../../packages/mobile-shared/src/index.js';

interface AuthState {
  phone: string;
  otpSent: boolean;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  sendOtp: (phone: string) => Promise<boolean>;
  verifyOtp: (otp: string) => Promise<boolean>;
  logout: () => Promise<void>;
  clearError: () => void;
}

function ensureRefresh() {
  try {
    const p = (globalThis as { process?: { env?: Record<string, string | undefined> } }).process?.env;
    configureRefresh({ supabaseUrl: p?.EXPO_PUBLIC_SUPABASE_URL, supabaseAnonKey: p?.EXPO_PUBLIC_SUPABASE_ANON_KEY });
  } catch {
    /* ignore */
  }
}

export const useVendorAuthStore = create<AuthState>((set, get) => ({
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
  clearError: () => set({ error: null }),
}));
