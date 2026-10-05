package com.codewild.food.finance.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Supabase truth: public.commissions (0007). Append-only finance ledger. */
@Entity
@Table(name = "commissions",
  uniqueConstraints = @UniqueConstraint(name = "commissions_order_key", columnNames = "order_id"))
public class Commission {
  public enum Status { ACCRUED, SETTLED, REVERSED }
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "order_id", nullable = false, unique = true)
  private UUID orderId;
  @Column(name = "vendor_id", nullable = false)
  private UUID vendorId;
  @Column(name = "basis_amount_paise", nullable = false)
  private long basisAmountPaise = 0;
  @Column(name = "commission_bps_snapshot", nullable = false)
  private int commissionBpsSnapshot = 0;
  @Column(name = "commission_amount_paise", nullable = false)
  private long commissionAmountPaise = 0;
  @Column(name = "tax_on_commission_paise", nullable = false)
  private long taxOnCommissionPaise = 0;
  @Column(name = "net_vendor_share_paise", nullable = false)
  private long netVendorSharePaise = 0;
  @Column(name = "currency", nullable = false)
  private String currency = "INR";
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.ACCRUED;
  @Column(name = "accrued_at", nullable = false)
  private Instant accruedAt = Instant.now();
  @Column(name = "reversed_at") private Instant reversedAt;
  @Column(name = "reversal_reason") private String reversalReason;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  protected Commission() {}
  public Commission(UUID orderId, UUID vendorId, long basisPaise, int bps) {
    this.orderId = orderId; this.vendorId = vendorId;
    this.basisAmountPaise = basisPaise; this.commissionBpsSnapshot = bps;
    this.commissionAmountPaise = (basisPaise * bps) / 10000L;
    this.netVendorSharePaise = basisPaise - this.commissionAmountPaise;
  }
  public UUID getId() { return id; }
  public UUID getOrderId() { return orderId; }
  public UUID getVendorId() { return vendorId; }
  public Status getStatus() { return status; }
  public void setStatus(Status s) { this.status = s; }
  public long getCommissionAmountPaise() { return commissionAmountPaise; }
  public long getNetVendorSharePaise() { return netVendorSharePaise; }
}
