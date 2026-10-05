import type { SharedClient } from './client.js';
import type {
  Address,
  Cart,
  DeliverySummary,
  MenuItem,
  NotificationItem,
  OrderDetail,
  OrderSummary,
  Page,
  PaymentSummary,
  PlanSummary,
  SessionUser,
  SubscriptionSummary,
  SupportTicket,
  VendorSummary,
} from './types.js';

/* Auth — OTP login + session (Supabase Auth → Spring Boot JWT). No hard-coded OTPs. */
export const authApi = (c: SharedClient) => ({
  requestOtp: (phone: string) => c.post<{ sent: boolean }>('/api/v1/auth/otp/request', { phone }),
  verifyOtp: (phone: string, otp: string) =>
    c.post<{ token: string; refreshToken?: string; expiresIn?: number; user: SessionUser }>('/api/v1/auth/otp/verify', { phone, otp }),
  me: () => c.get<SessionUser>('/api/v1/auth/me'),
  logout: () => c.post<void>('/api/v1/auth/logout', {}),
});

/* Vendors — discovery + profile (customer/vendor perspectives share reads). */
export const vendorsApi = (c: SharedClient) => ({
  list: (q?: Record<string, unknown>) => c.get<Page<VendorSummary>>('/api/v1/vendors', q),
  search: (q?: Record<string, unknown>) => c.get<Page<VendorSummary>>('/api/v1/vendors/search', q),
  get: (id: string) => c.get<VendorSummary>(`/api/v1/vendors/${id}`),
  updateProfile: (body: Partial<VendorSummary>) => c.put<VendorSummary>('/api/v1/vendor/profile', body),
  setOpen: (open: boolean) => c.patch<VendorSummary>('/api/v1/vendor/availability', { open }),
});

/* Menus — vendor-owned menu / availability; customers read published menus. */
export const menusApi = (c: SharedClient) => ({
  getMenu: (vendorId: string) => c.get<MenuItem[]>(`/api/v1/vendors/${vendorId}/menu`),
  getItem: (itemId: string) => c.get<MenuItem>(`/api/v1/menu/items/${itemId}`),
  upsertItem: (body: Partial<MenuItem>) => c.post<MenuItem>('/api/v1/vendor/menu/items', body),
  updateItem: (itemId: string, body: Partial<MenuItem>) => c.put<MenuItem>(`/api/v1/vendor/menu/items/${itemId}`, body),
  setAvailability: (itemId: string, available: boolean) =>
    c.patch<MenuItem>(`/api/v1/vendor/menu/items/${itemId}`, { available }),
  deleteItem: (itemId: string) => c.delete<void>(`/api/v1/vendor/menu/items/${itemId}`),
});

/* Cart — customer-scoped; priced server-side at checkout. */
export const cartApi = (c: SharedClient) => ({
  get: () => c.get<Cart>('/api/v1/cart'),
  add: (itemId: string, qty: number) => c.post<Cart>('/api/v1/cart/items', { itemId, qty }),
  updateQty: (itemId: string, qty: number) => c.put<Cart>(`/api/v1/cart/items/${itemId}`, { qty }),
  remove: (itemId: string) => c.delete<Cart>(`/api/v1/cart/items/${itemId}`),
  clear: () => c.delete<void>('/api/v1/cart'),
  addresses: () => c.get<Address[]>('/api/v1/addresses'),
  checkout: (addressId: string, slot: string, paymentMode?: string) =>
    c.post<OrderDetail>('/api/v1/cart/checkout', { addressId, slot, paymentMode }),
});

/* Orders — role-scoped transitions only (no free-form status writes, no FZ-* mocks). */
export const ordersApi = (c: SharedClient) => ({
  list: (q?: Record<string, unknown>) => c.get<Page<OrderSummary>>('/api/v1/orders', q),
  get: (id: string) => c.get<OrderDetail>(`/api/v1/orders/${id}`),
  create: (body: { addressId: string; slot: string; items: { itemId: string; qty: number }[]; paymentMode?: string }) =>
    c.post<OrderDetail>('/api/v1/orders', body),
  track: (id: string) => c.get<OrderDetail>(`/api/v1/orders/${id}/tracking`),
  cancel: (id: string, reason: string) => c.post<OrderDetail>(`/api/v1/orders/${id}/cancel`, { reason }),
  reorder: (id: string) => c.post<Cart>(`/api/v1/orders/${id}/reorder`, {}),
  vendorAccept: (id: string) => c.post<OrderDetail>(`/api/v1/vendor/orders/${id}/accept`, {}),
  vendorReady: (id: string) => c.post<OrderDetail>(`/api/v1/vendor/orders/${id}/ready`, {}),
});

/* Subscriptions — recurring contracts generating daily orders. */
export const subscriptionsApi = (c: SharedClient) => ({
  plans: (vendorId: string) => c.get<PlanSummary[]>(`/api/v1/vendors/${vendorId}/plans`),
  list: () => c.get<SubscriptionSummary[]>('/api/v1/subscriptions'),
  create: (body: { planId: string; addressId: string; startDate: string }) =>
    c.post<SubscriptionSummary>('/api/v1/subscriptions', body),
  pause: (id: string) => c.post<SubscriptionSummary>(`/api/v1/subscriptions/${id}/pause`, {}),
  resume: (id: string) => c.post<SubscriptionSummary>(`/api/v1/subscriptions/${id}/resume`, {}),
  skip: (id: string, date: string) => c.post<SubscriptionSummary>(`/api/v1/subscriptions/${id}/skip`, { date }),
  cancel: (id: string, reason?: string) => c.post<SubscriptionSummary>(`/api/v1/subscriptions/${id}/cancel`, { reason }),
});

/* Book-a-cook — at-home chef bookings (customer domain). */
export const bookingsApi = (c: SharedClient) => ({
  cooks: (q?: Record<string, unknown>) => c.get<Page<VendorSummary>>('/api/v1/cooks', q),
  create: (body: { cookId: string; date: string; slot: string; addressId: string; guests: number; menuIds?: string[] }) =>
    c.post<{ id: string; status: string }>('/api/v1/bookings', body),
  list: () => c.get<{ id: string; status: string; date: string }[]>('/api/v1/bookings'),
  cancel: (id: string, reason?: string) => c.post<void>(`/api/v1/bookings/${id}/cancel`, { reason }),
});

/* Payments — adapter-backed; success confirmed via webhook verification. */
export const paymentsApi = (c: SharedClient) => ({
  create: (orderId: string, method?: string) => c.post<PaymentSummary>('/api/v1/payments', { orderId, method }),
  verify: (ref: string) => c.get<PaymentSummary>(`/api/v1/payments/${ref}/verify`),
  list: (q?: Record<string, unknown>) => c.get<Page<PaymentSummary>>('/api/v1/payments', q),
});

/* Deliveries — rider assignment / pickup / completion with idempotency. */
export const deliveriesApi = (c: SharedClient) => ({
  list: (q?: Record<string, unknown>) => c.get<Page<DeliverySummary>>('/api/v1/deliveries', q),
  available: () => c.get<DeliverySummary[]>('/api/v1/deliveries/available'),
  mine: () => c.get<DeliverySummary[]>('/api/v1/deliveries/mine'),
  get: (id: string) => c.get<DeliverySummary & { otp?: string }>(`/api/v1/deliveries/${id}`),
  accept: (id: string) => c.post<DeliverySummary>(`/api/v1/deliveries/${id}/accept`, {}),
  pickup: (id: string, code?: string) => c.post<DeliverySummary>(`/api/v1/deliveries/${id}/pickup`, { code }),
  complete: (id: string, code?: string) => c.post<DeliverySummary>(`/api/v1/deliveries/${id}/complete`, { code }),
});

/* Notifications — in-app inbox backed by domain-event handlers. */
export const notificationsApi = (c: SharedClient) => ({
  list: () => c.get<NotificationItem[]>('/api/v1/notifications'),
  markRead: (id: string) => c.post<void>(`/api/v1/notifications/${id}/read`, {}),
  markAllRead: () => c.post<void>('/api/v1/notifications/read-all', {}),
  registerPushToken: (token: string, platform: string) =>
    c.post<void>('/api/v1/notifications/push-tokens', { token, platform }),
});

/* Support — tickets for customers, vendors and riders. */
export const supportApi = (c: SharedClient) => ({
  createTicket: (subject: string, body: string) => c.post<SupportTicket>('/api/v1/support/tickets', { subject, body }),
  list: () => c.get<SupportTicket[]>('/api/v1/support/tickets'),
});
