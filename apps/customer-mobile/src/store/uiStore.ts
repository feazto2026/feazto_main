import { create } from 'zustand';

interface UiState {
  toast: string | null;
  addressLabel: string;
  deliveryMode: 'standard' | 'express';
  paymentMode: 'upi' | 'card';
  showToast: (msg: string) => void;
  clearToast: () => void;
  setDeliveryMode: (m: 'standard' | 'express') => void;
  setPaymentMode: (m: 'upi' | 'card') => void;
}

export const useUiStore = create<UiState>((set) => ({
  toast: null,
  addressLabel: 'Home — Anna Nagar, Chennai',
  deliveryMode: 'standard',
  paymentMode: 'upi',
  showToast: (msg) => set({ toast: msg }),
  clearToast: () => set({ toast: null }),
  setDeliveryMode: (m) => set({ deliveryMode: m }),
  setPaymentMode: (m) => set({ paymentMode: m }),
}));
