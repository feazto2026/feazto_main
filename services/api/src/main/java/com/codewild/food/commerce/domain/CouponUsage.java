package com.codewild.food.commerce.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupon_usages")
public class CouponUsage {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "coupon_id", nullable = false)
  private UUID couponId;
  @Column(name = "order_id", nullable = false, unique = true)
  private UUID orderId;
  @Column(name = "customer_id", nullable = false)
  private UUID customerId;
  @Column(name = "discount_paise", nullable = false)
  private long discountPaise = 0;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  protected CouponUsage() {}
  public CouponUsage(UUID couponId, UUID orderId, UUID customerId, long discountPaise) {
    this.couponId = couponId; this.orderId = orderId;
    this.customerId = customerId; this.discountPaise = discountPaise;
  }
  public UUID getId() { return id; }
}
