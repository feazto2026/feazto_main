package com.codewild.food.commerce.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Supabase truth: public.orders (0004).
 * UUID ids, order_number UNIQUE, idempotency_key NOT NULL UNIQUE, payment_status,
 * *_paise snapshots frozen at creation, CHECK total_paise = subtotal - discount
 * + tax + delivery + platform + packaging.
 */
@Entity
@Table(name = "orders",
  uniqueConstraints = {
    @UniqueConstraint(name = "orders_order_number_key", columnNames = "order_number"),
    @UniqueConstraint(name = "orders_idempotency_key_key", columnNames = "idempotency_key")
  },
  indexes = {
    @Index(name = "ix_orders_customer_created", columnList = "customer_id,created_at"),
    @Index(name = "ix_orders_vendor_status", columnList = "vendor_id,status")
  })
public class OrderEntity {
  public enum PaymentStatus {
    PENDING, INITIATED, SUCCESS, FAILED, CANCELLED,
    REFUND_PENDING, PARTIALLY_REFUNDED, REFUNDED
  }

  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_number", nullable = false, unique = true)
  private String orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", insertable = false, updatable = false)
  private com.codewild.food.customer.domain.CustomerProfile customer;

  @Column(name = "vendor_id", nullable = false)
  private UUID vendorId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vendor_id", insertable = false, updatable = false)
  private com.codewild.food.marketplace.domain.VendorProfile vendor;

  @Column(name = "address_id")
  private UUID addressId;
  @Column(name = "slot_id")
  private UUID slotId;
  @Column(name = "service_date")
  private LocalDate serviceDate;
  @Column(name = "subscription_id")
  private UUID subscriptionId;
  @Column(name = "subscription_schedule_id")
  private UUID subscriptionScheduleId;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subscription_schedule_id", insertable = false, updatable = false)
  private com.codewild.food.subscription.domain.SubscriptionSchedule subscriptionSchedule;
  @Column(name = "coupon_id")
  private UUID couponId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private OrderStateMachine.State status = OrderStateMachine.State.CREATED;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_status", nullable = false)
  private PaymentStatus paymentStatus = PaymentStatus.PENDING;

  // ---- financial snapshot (frozen at creation) ----
  @Column(name = "subtotal_paise", nullable = false)
  private long subtotalPaise = 0;
  @Column(name = "discount_paise", nullable = false)
  private long discountPaise = 0;
  @Column(name = "tax_paise", nullable = false)
  private long taxPaise = 0;
  @Column(name = "delivery_fee_paise", nullable = false)
  private long deliveryFeePaise = 0;
  @Column(name = "platform_fee_paise", nullable = false)
  private long platformFeePaise = 0;
  @Column(name = "packaging_fee_paise", nullable = false)
  private long packagingFeePaise = 0;
  @Column(name = "total_paise", nullable = false)
  private long totalPaise = 0;
  @Column(name = "currency", nullable = false)
  private String currency = "INR";
  @Column(name = "commission_bps_snapshot", nullable = false)
  private int commissionBpsSnapshot = 0;
  @Column(name = "platform_commission_paise_snapshot", nullable = false)
  private long platformCommissionPaiseSnapshot = 0;
  @Column(name = "vendor_payout_paise_snapshot", nullable = false)
  private long vendorPayoutPaiseSnapshot = 0;
  @Column(name = "rider_payout_paise_snapshot")
  private Long riderPayoutPaiseSnapshot;
  @Column(name = "coupon_code_snapshot")
  private String couponCodeSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "delivery_address_snapshot", nullable = false, columnDefinition = "jsonb")
  private String deliveryAddressSnapshot = "{}";

  @Column(name = "item_count", nullable = false)
  private int itemCount = 0;
  @Column(name = "customer_note")
  private String customerNote;
  @Column(name = "vendor_note")
  private String vendorNote;
  @Column(name = "cancellation_reason")
  private String cancellationReason;
  @Column(name = "cancelled_by")
  private UUID cancelledBy;
  @Column(name = "cancelled_at")
  private Instant cancelledAt;
  @Column(name = "estimated_ready_at")
  private Instant estimatedReadyAt;
  @Column(name = "estimated_delivery_at")
  private Instant estimatedDeliveryAt;
  @Column(name = "delivered_at")
  private Instant deliveredAt;
  @Column(name = "completed_at")
  private Instant completedAt;
  @Column(name = "payment_due_at")
  private Instant paymentDueAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
  private String metadataJson = "{}";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "order_id")
  private List<OrderItem> items = new ArrayList<>();

  public OrderEntity() {}
  public OrderEntity(String customerId, String vendorId) {
    try { this.customerId = customerId == null ? null : UUID.fromString(customerId); } catch (Exception e) { this.customerId = null; }
    try { this.vendorId = vendorId == null ? null : UUID.fromString(vendorId); } catch (Exception e) { this.vendorId = null; }
  }
  public UUID getId() { return id; }
  public String getIdAsString() { return id == null ? null : id.toString(); }
  /** Back-compat: services use String ids. */
  public String getIdCompat() { return getIdAsString(); }
  public String getOrderNumber() { return orderNumber; }
  public void setOrderNumber(String v) { this.orderNumber = v; }
  public UUID getCustomerId() { return customerId; }
  public String getCustomerIdAsString() { return customerId == null ? null : customerId.toString(); }
  public void setCustomerId(UUID c) { this.customerId = c; }
  public void setCustomerId(String c) { try { this.customerId = c == null ? null : UUID.fromString(c); } catch (Exception e) { this.customerId = null; } }
  public UUID getVendorId() { return vendorId; }
  public String getVendorIdAsString() { return vendorId == null ? null : vendorId.toString(); }
  public void setVendorId(UUID v) { this.vendorId = v; }
  public void setVendorId(String v) { try { this.vendorId = v == null ? null : UUID.fromString(v); } catch (Exception e) { this.vendorId = null; } }
  public OrderStateMachine.State getStatus() { return status; }
  public void setStatus(OrderStateMachine.State s) { this.status = s; }
  public void transitionTo(OrderStateMachine.State next) {
    OrderStateMachine.validate(this.status, next);
    this.status = next; this.updatedAt = Instant.now();
    if (next == OrderStateMachine.State.DELIVERED) this.deliveredAt = Instant.now();
    if (next == OrderStateMachine.State.COMPLETED) this.completedAt = Instant.now();
    if (next == OrderStateMachine.State.CANCELLED) this.cancelledAt = Instant.now();
  }
  public PaymentStatus getPaymentStatus() { return paymentStatus; }
  public void setPaymentStatus(PaymentStatus p) { this.paymentStatus = p; }
  public long getSubtotalPaise() { return subtotalPaise; }
  public void setSubtotalPaise(long v) { this.subtotalPaise = v; }
  public long getDiscountPaise() { return discountPaise; }
  public void setDiscountPaise(long v) { this.discountPaise = v; }
  public long getTaxPaise() { return taxPaise; }
  public void setTaxPaise(long v) { this.taxPaise = v; }
  public long getDeliveryFeePaise() { return deliveryFeePaise; }
  public void setDeliveryFeePaise(long v) { this.deliveryFeePaise = v; }
  public long getPlatformFeePaise() { return platformFeePaise; }
  public void setPlatformFeePaise(long v) { this.platformFeePaise = v; }
  public long getPackagingFeePaise() { return packagingFeePaise; }
  public void setPackagingFeePaise(long v) { this.packagingFeePaise = v; }
  public long getTotalPaise() { return totalPaise; }
  public void setTotalPaise(long v) { this.totalPaise = v; }
  public int getCommissionBpsSnapshot() { return commissionBpsSnapshot; }
  public void setCommissionBpsSnapshot(int v) { this.commissionBpsSnapshot = v; }
  public long getPlatformCommissionPaiseSnapshot() { return platformCommissionPaiseSnapshot; }
  public void setPlatformCommissionPaiseSnapshot(long v) { this.platformCommissionPaiseSnapshot = v; }
  public long getVendorPayoutPaiseSnapshot() { return vendorPayoutPaiseSnapshot; }
  public void setVendorPayoutPaiseSnapshot(long v) { this.vendorPayoutPaiseSnapshot = v; }
  public Long getRiderPayoutPaiseSnapshot() { return riderPayoutPaiseSnapshot; }
  public void setRiderPayoutPaiseSnapshot(Long v) { this.riderPayoutPaiseSnapshot = v; }
  public String getCouponCodeSnapshot() { return couponCodeSnapshot; }
  public void setCouponCodeSnapshot(String v) { this.couponCodeSnapshot = v; }
  public String getDeliveryAddressSnapshot() { return deliveryAddressSnapshot; }
  public void setDeliveryAddressSnapshot(String v) { this.deliveryAddressSnapshot = v; }
  public int getItemCount() { return itemCount; }
  public void setItemCount(int v) { this.itemCount = v; }
  public UUID getAddressId() { return addressId; }
  public void setAddressId(UUID v) { this.addressId = v; }
  public void setAddressId(String v) { try { this.addressId = v == null ? null : UUID.fromString(v); } catch (Exception e) { this.addressId = null; } }
  public UUID getSlotId() { return slotId; }
  public void setSlotId(UUID v) { this.slotId = v; }
  public LocalDate getServiceDate() { return serviceDate; }
  public void setServiceDate(LocalDate v) { this.serviceDate = v; }
  public UUID getSubscriptionId() { return subscriptionId; }
  public void setSubscriptionId(UUID v) { this.subscriptionId = v; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String k) { this.idempotencyKey = k; }
  public List<OrderItem> getItems() { return items; }
  public Instant getCreatedAt() { return createdAt; }
  // Back-compat rupees views (price_snapshot_json / BigDecimal total)
  public java.math.BigDecimal getTotalAmount() { return java.math.BigDecimal.valueOf(totalPaise, 2); }
  public void setTotalAmount(java.math.BigDecimal rupees) {
    this.totalPaise = rupees == null ? 0 : rupees.multiply(java.math.BigDecimal.valueOf(100)).longValue();
  }
  public java.math.BigDecimal getDeliveryFee() { return java.math.BigDecimal.valueOf(deliveryFeePaise, 2); }
  public void setDeliveryFee(java.math.BigDecimal rupees) {
    this.deliveryFeePaise = rupees == null ? 0 : rupees.multiply(java.math.BigDecimal.valueOf(100)).longValue();
  }
  public String getPriceSnapshotJson() {
    return "{\"subtotal_paise\":" + subtotalPaise + ",\"total_paise\":" + totalPaise + "}";
  }
  public void setPriceSnapshotJson(String s) { this.metadataJson = s == null ? "{}" : s; }
  /** Server-side total invariant mirror of CHECK constraint (also enforced in DB). */
  public void recomputeTotalOrThrow() {
    long expect = subtotalPaise - discountPaise + taxPaise + deliveryFeePaise + platformFeePaise + packagingFeePaise;
    if (expect < 0 || subtotalPaise < discountPaise)
      throw new com.codewild.food.shared.errors.BusinessException(
        com.codewild.food.shared.errors.ErrorCodes.VALIDATION, "Order total invariant violated");
    this.totalPaise = expect;
  }
}
