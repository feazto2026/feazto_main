/** Shared domain types (server-driven; no mock ids — backend is authoritative). */

export interface Page<T> {
  content: T[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

export type OrderStatus =
  | 'CREATED'
  | 'PAYMENT_PENDING'
  | 'PLACED'
  | 'VENDOR_ACCEPTED'
  | 'PREPARING'
  | 'READY_FOR_PICKUP'
  | 'RIDER_ASSIGNED'
  | 'PICKED_UP'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'COMPLETED'
  | 'CANCELLED';

export interface VendorSummary {
  id: string;
  name: string;
  city?: string;
  status: string;
  specialtyRegions?: string[];
  rating?: number;
  image?: string;
  address?: string;
  deliversTo?: string[];
}

export interface MenuItem {
  id: string;
  vendorId: string;
  name: string;
  description?: string;
  price: number;
  category?: string;
  image?: string;
  available: boolean;
  veg?: boolean;
  rating?: number;
}

export interface CartLine {
  itemId: string;
  name: string;
  unitPrice: number;
  qty: number;
  vendorId: string;
}

export interface Cart {
  lines: CartLine[];
  subtotal: number;
  deliveryFee: number;
  packagingFee: number;
  total: number;
}

export interface Address {
  id: string;
  label: string;
  line1: string;
  city: string;
}

export interface OrderSummary {
  id: string;
  status: OrderStatus | string;
  total: number;
  itemsCount?: number;
  vendorName?: string;
  createdAt: string;
}

export interface OrderDetail extends OrderSummary {
  items: { id?: string; name: string; qty: number; unitPrice: number }[];
  timeline: { at: string; label: string }[];
  deliveryAddress?: string;
  paymentMethod?: string;
  eta?: string;
  delivery?: { riderName?: string; status: string };
}

export interface DeliverySummary {
  id: string;
  orderId: string;
  status: string;
  customerName?: string;
  address?: string;
  payout?: number;
}

export interface PlanSummary {
  id: string;
  vendorId?: string;
  name: string;
  price: number;
  mealsPerDay?: number;
  daysPerWeek?: number;
}

export interface SubscriptionSummary {
  id: string;
  planName: string;
  vendorName?: string;
  status: string;
  nextDeliveryDate?: string;
}

export interface PaymentSummary {
  id: string;
  orderId: string;
  amount: number;
  status: string;
  provider?: string;
}

export interface NotificationItem {
  id: string;
  title: string;
  body: string;
  read: boolean;
  createdAt: string;
}

export interface SupportTicket {
  id: string;
  subject: string;
  status: string;
  createdAt: string;
}

export interface SessionUser {
  id: string;
  phone: string;
  name?: string;
  role: 'CUSTOMER' | 'VENDOR' | 'RIDER' | string;
}
