package com.codewild.food.finance.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vendor_payout_items",
  uniqueConstraints = @UniqueConstraint(name = "vendor_payout_items_payout_order_key",
    columnNames = {"payout_id","order_id"}))
public class VendorPayoutItem {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "payout_id", nullable = false)
  private UUID payoutId;
  @Column(name = "order_id", nullable = false)
  private UUID orderId;
  @Column(name = "commission_id") private UUID commissionId;
  @Column(name = "amount_paise", nullable = false)
  private long amountPaise = 0;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  protected VendorPayoutItem() {}
  public VendorPayoutItem(UUID payoutId, UUID orderId, UUID commissionId, long amountPaise) {
    this.payoutId = payoutId; this.orderId = orderId;
    this.commissionId = commissionId; this.amountPaise = amountPaise;
  }
  public UUID getId() { return id; }
}
