package com.codewild.food.commerce.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "carts")
public class Cart {
  public enum Status { ACTIVE, CHECKED_OUT, ABANDONED, EXPIRED }
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "customer_id", nullable = false)
  private UUID customerId;
  @Column(name = "vendor_id", nullable = false)
  private UUID vendorId;
  @Column(name = "slot_id") private UUID slotId;
  @Column(name = "address_id") private UUID addressId;
  @Column(name = "coupon_id") private UUID couponId;
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.ACTIVE;
  @Column(name = "expires_at") private Instant expiresAt;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected Cart() {}
  public Cart(UUID customerId, UUID vendorId) { this.customerId = customerId; this.vendorId = vendorId; }
  public UUID getId() { return id; }
  public UUID getCustomerId() { return customerId; }
  public UUID getVendorId() { return vendorId; }
  public Status getStatus() { return status; }
  public void setStatus(Status s) { this.status = s; }
}
