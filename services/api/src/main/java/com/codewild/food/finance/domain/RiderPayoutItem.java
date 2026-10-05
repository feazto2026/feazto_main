package com.codewild.food.finance.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rider_payout_items",
  uniqueConstraints = @UniqueConstraint(name = "rider_payout_items_payout_delivery_key",
    columnNames = {"payout_id","delivery_id"}))
public class RiderPayoutItem {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "payout_id", nullable = false)
  private UUID payoutId;
  @Column(name = "delivery_id", nullable = false)
  private UUID deliveryId;
  @Column(name = "amount_paise", nullable = false)
  private long amountPaise = 0;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  protected RiderPayoutItem() {}
  public RiderPayoutItem(UUID payoutId, UUID deliveryId, long amountPaise) {
    this.payoutId = payoutId; this.deliveryId = deliveryId; this.amountPaise = amountPaise;
  }
  public UUID getId() { return id; }
}
