import { get, post, put, patch, del } from './client';
import type { Page } from './client';

/* Shared list query shared by list pages (server pagination + filters). */
export interface ListQuery {
  page?: number;
  pageSize?: number;
  search?: string;
  status?: string;
  signal?: AbortSignal;
  [k: string]: string | number | boolean | undefined | AbortSignal;
}

/* ---------- Dashboard ---------- */

export interface DashboardSummary {
  date: string;
  ordersToday: number;
  subscriptionsActive: number;
  vendorsTotal: number;
  vendorsPendingApproval: number;
  ridersOnline: number;
  deliveriesToday: number;
  deliveriesPending: number;
  govToday: number;
  commissionToday: number;
  payoutsPending: number;
  payoutsPendingAmount: number;
  supportOpen: number;
  supportUnassigned: number;
  ordersByStatus: Record<string, number>;
  paymentsToday: { succeeded: number; failed: number; refunded: number };
}

export const fetchDashboard = (date: string, signal?: AbortSignal) =>
  get<DashboardSummary>('/api/v1/admin/dashboard/summary', { date }, signal);

/* ---------- Customers ---------- */

export interface Customer {
  id: string;
  name: string;
  phone: string;
  email?: string;
  city?: string;
  status: string;
  ordersCount: number;
  createdAt: string;
}

export const listCustomers = (q: ListQuery) =>
  get<Page<Customer>>('/api/v1/admin/customers', { page: q.page ?? 0, pageSize: q.pageSize ?? 20, search: q.search, status: q.status });

/* ---------- Vendors ---------- */

export interface Vendor {
  id: string;
  name: string;
  phone: string;
  city?: string;
  status: 'PENDING' | 'APPROVED' | 'SUSPENDED' | 'REJECTED' | string;
  specialtyRegions?: string[];
  createdAt: string;
}

export const listVendors = (q: ListQuery) =>
  get<Page<Vendor>>('/api/v1/admin/vendors', { page: q.page ?? 0, pageSize: q.pageSize ?? 20, search: q.search, status: q.status });

export const listVendorVerifications = (q: ListQuery) =>
  get<Page<Vendor>>('/api/v1/admin/vendors/verifications', { page: q.page ?? 0, pageSize: q.pageSize ?? 20, search: q.search, status: q.status ?? 'PENDING' });

export const approveVendor = (id: string, note?: string) => post(`/api/v1/admin/vendors/${id}/approve`, { note });
export const rejectVendor = (id: string, reason: string) => post(`/api/v1/admin/vendors/${id}/reject`, { reason });
export const suspendVendor = (id: string, reason: string) => post(`/api/v1/admin/vendors/${id}/suspend`, { reason });

/* ---------- Orders ---------- */

export interface Order {
  id: string;
  customerName: string;
  vendorName: string;
  status: string;
  paymentStatus: string;
  total: number;
  itemsCount: number;
  createdAt: string;
}

export interface OrderDetail extends Order {
  items: { name: string; qty: number; unitPrice: number }[];
  timeline: { at: string; label: string }[];
  delivery?: { riderName?: string; status: string };
}

export const listOrders = (q: ListQuery) =>
  get<Page<Order>>('/api/v1/admin/orders', { page: q.page ?? 0, pageSize: q.pageSize ?? 20, search: q.search, status: q.status });

export const getOrder = (id: string) => get<OrderDetail>(`/api/v1/admin/orders/${id}`);
export const cancelOrder = (id: string, reason: string) => post(`/api/v1/admin/orders/${id}/cancel`, { reason });
export const refundOrder = (id: string, amount?: number, reason?: string) => post(`/api/v1/admin/orders/${id}/refund`, { amount, reason });

/* ---------- Subscriptions ---------- */

export interface Subscription {
  id: string;
  customerName: string;
  vendorName: string;
  planName: string;
  status: string;
  nextDeliveryDate?: string;
  createdAt: string;
}

export const listSubscriptions = (q: ListQuery) =>
  get<Page<Subscription>>('/api/v1/admin/subscriptions', { page: q.page ?? 0, pageSize: q.pageSize ?? 20, search: q.search, status: q.status });

/* ---------- Deliveries ---------- */

export interface Delivery {
  id: string;
  orderId: string;
  riderName?: string;
  status: string;
  zone?: string;
  createdAt: string;
}

export const listDeliveries = (q: ListQuery) =>
  get<Page<Delivery>>('/api/v1/admin/deliveries', { page: q.page ?? 0, pageSize: q.pageSize ?? 20, search: q.search, status: q.status });

export const reassignDelivery = (id: string, riderId: string) => post(`/api/v1/admin/deliveries/${id}/reassign`, { riderId });

/* ---------- Service zones ---------- */

export interface ServiceZone {
  id: string;
  name: string;
  city: string;
  active: boolean;
  vendorCount: number;
}

export const listZones = () => get<ServiceZone[]>('/api/v1/admin/service-zones');
export const createZone = (body: Partial<ServiceZone>) => post<ServiceZone>('/api/v1/admin/service-zones', body);
export const updateZone = (id: string, body: Partial<ServiceZone>) => put<ServiceZone>(`/api/v1/admin/service-zones/${id}`, body);
export const toggleZone = (id: string, active: boolean) => patch<ServiceZone>(`/api/v1/admin/service-zones/${id}`, { active });

/* ---------- Payments & payouts ---------- */

export interface Payment {
  id: string;
  orderId: string;
  amount: number;
  status: string;
  provider?: string;
  createdAt: string;
}

export const listPayments = (q: ListQuery) =>
  get<Page<Payment>>('/api/v1/admin/payments', { page: q.page ?? 0, pageSize: q.pageSize ?? 20, search: q.search, status: q.status });

export interface Payout {
  id: string;
  beneficiary: string;
  kind: 'VENDOR' | 'RIDER' | string;
  amount: number;
  status: string;
  createdAt: string;
}

export const listPayouts = (q: ListQuery & { kind?: string }) =>
  get<Page<Payout>>('/api/v1/admin/payouts', { page: q.page ?? 0, pageSize: q.pageSize ?? 20, search: q.search, status: q.status, kind: q.kind });

export const payoutAction = (id: string, action: 'approve' | 'release' | 'hold', note?: string) =>
  post(`/api/v1/admin/payouts/${id}/${action}`, { note });

/* ---------- Support & audit ---------- */

export interface Ticket {
  id: string;
  subject: string;
  requester: string;
  status: string;
  assignee?: string;
  priority: string;
  createdAt: string;
}

export const listTickets = (q: ListQuery) =>
  get<Page<Ticket>>('/api/v1/admin/support/tickets', { page: q.page ?? 0, pageSize: q.pageSize ?? 20, search: q.search, status: q.status });

export const assignTicket = (id: string, assignee: string) => post(`/api/v1/admin/support/tickets/${id}/assign`, { assignee });
export const resolveTicket = (id: string, resolution: string) => post(`/api/v1/admin/support/tickets/${id}/resolve`, { resolution });

export interface AuditLog {
  id: string;
  at: string;
  actor: string;
  action: string;
  entity: string;
  entityId?: string;
  reason?: string;
}

export const listAuditLogs = (q: ListQuery) =>
  get<Page<AuditLog>>('/api/v1/admin/audit-logs', { page: q.page ?? 0, pageSize: q.pageSize ?? 20, search: q.search, status: q.status });

/* ---------- Settings ---------- */

export interface PlatformSettings {
  commissionPctDefault: number;
  deliveryFeeDefault: number;
  supportEmail: string;
  maintenanceMode: boolean;
}

export const getSettings = () => get<PlatformSettings>('/api/v1/admin/settings');
export const saveSettings = (body: PlatformSettings) => put<PlatformSettings>('/api/v1/admin/settings', body);

export const deleteEntity = (path: string) => del(`${path}`);
